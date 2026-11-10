/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.dmn;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractDmnElementVisitor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.DomElement;

/**
 * Detects expressions that reference the output of another decision by its bare output name.
 *
 * <p>Camunda 7 ({@code DefaultDmnDecisionContext}) evaluates all decisions of the requirement tree
 * of the called decision in one flat variable context: depth first, required decisions in document
 * order, each decision once. After each decision, the outputs of its matched rule are put into the
 * context under their output names, overwriting earlier values. A decision can therefore read the
 * outputs of every decision evaluated before it, including decisions it doesn't require at all.
 *
 * <p>Camunda 8 only exposes the results of direct requirements, each bound to the {@code variable}
 * name of the decision, or its ID if it has none. A decision table with several outputs is bound as
 * a context of its outputs. After conversion, such references evaluate to {@code null}, or to the
 * value of another decision. They are reported as tasks, naming the decision Camunda 7 actually
 * reads. Expressions are checked as {@link TextVisitor} converts them to FEEL.
 */
public class RequiredDecisionOutputReferenceVisitor extends AbstractDmnElementVisitor {
  // possessive, so long literals don't recurse once per character and overflow the stack
  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]++|\\\\.)*+\"");
  private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_]\\w*");

  /**
   * A simple name that isn't part of a longer name, a function call or a context key. Path segments
   * are excluded separately: see {@link #isPathSegment(String, int)}.
   */
  private static final Pattern NAME_REFERENCE =
      Pattern.compile("(?<![\\w?])([A-Za-z_]\\w*)(?![\\w?])(?!\\s*[(:])");

  /**
   * The resolution of the {@code definitions} element visited last on this thread. Elements are
   * visited depth first, so {@code definitions} is always visited before its expressions. Neither
   * the key nor the resolution references the document, so it isn't retained after the check.
   */
  private static final ThreadLocal<CachedResolution> CACHE = new ThreadLocal<>();

  private record CachedResolution(int definitionsHash, Resolution resolution) {}

  /** Number of (decision, name, provider, top-level decision) entries the resolution keeps. */
  static int resolutionSize(DomElement definitions) {
    return Resolution.of(definitions).visibleOutputs.values().stream()
        .flatMap(names -> names.values().stream())
        .flatMap(providers -> providers.values().stream())
        .mapToInt(Set::size)
        .sum();
  }

  @Override
  public String localName() {
    return "text";
  }

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    DomElement element = context.getElement();
    return namespaceUri().contains(element.getNamespaceURI())
        && ("text".equals(element.getLocalName()) || "definitions".equals(element.getLocalName()));
  }

  @Override
  protected void visitDmnElement(DomElementVisitorContext context) {
    DomElement element = context.getElement();
    if ("definitions".equals(element.getLocalName())) {
      CACHE.set(new CachedResolution(element.hashCode(), Resolution.of(element)));
      return;
    }
    DomElement decision = findEnclosingDecision(element);
    if (decision == null) {
      return;
    }
    Resolution resolution = resolution(decision.getParentElement());
    String decisionId = decision.getAttribute("id");
    Map<String, Map<String, Set<String>>> visible = resolution.visibleOutputs(decisionId);
    if (visible.isEmpty()) {
      return;
    }
    String expression = toFeel(element.getTextContent());
    if (expression == null || expression.isBlank()) {
      return;
    }
    List<String> directRequirements = requiredDecisionIds(decision);
    String searchable = blankStringLiterals(expression);
    String trimmedExpression = expression.trim();
    // Look up only the names the expression uses, so the cost doesn't grow with the number of
    // visible outputs. Names that aren't simple identifiers can't be tokenized and are matched
    // one by one; they are rare.
    Set<String> referencedNames = simpleNameReferences(searchable);
    for (String name : resolution.nonSimpleNames(decisionId)) {
      if (isReferenced(searchable, name)) {
        referencedNames.add(name);
      }
    }
    for (String name : referencedNames) {
      Map<String, Set<String>> providers = visible.get(name);
      if (providers == null) {
        continue;
      }
      if (providers.size() > 1) {
        context.addMessage(
            MessageFactory.ambiguousRequiredDecisionOutputReference(
                trimmedExpression, name, describeProviders(providers)));
        continue;
      }
      String providerId = providers.keySet().iterator().next();
      if (directRequirements.contains(providerId)
          && name.equals(resolution.camunda8Reference(providerId, name))) {
        // Camunda 8 binds this name to the same value
        continue;
      }
      context.addMessage(
          MessageFactory.requiredDecisionOutputReference(
              trimmedExpression,
              name,
              providerId,
              resolution.camunda8Reference(providerId, name),
              !directRequirements.contains(providerId)));
    }
  }

  /** The expression as {@link TextVisitor} converts it. */
  private static String toFeel(String expression) {
    ExpressionTransformationResult transformation =
        ExpressionTransformer.transformToFeelDmn("Text field", expression);
    if (transformation == null || transformation.result() == null) {
      return expression;
    }
    return transformation.result();
  }

  private static Resolution resolution(DomElement definitions) {
    CachedResolution cached = CACHE.get();
    if (cached != null
        && definitions != null
        && cached.definitionsHash() == definitions.hashCode()) {
      return cached.resolution();
    }
    return Resolution.of(definitions);
  }

  private static String describeProviders(Map<String, Set<String>> providers) {
    return providers.entrySet().stream()
        .map(
            e ->
                "'"
                    + e.getKey()
                    + "' (when evaluating "
                    + e.getValue().stream()
                        .map(t -> "'" + t + "'")
                        .collect(Collectors.joining(", "))
                    + ")")
        .collect(Collectors.joining(", "));
  }

  /**
   * Which outputs each decision sees in Camunda 7, simulated once per {@code definitions} element.
   */
  private static final class Resolution {
    private final Map<String, Decision> decisions = new LinkedHashMap<>();

    /** decision id -> output name -> providing decision id -> top-level decisions */
    private final Map<String, Map<String, Map<String, Set<String>>>> visibleOutputs =
        new HashMap<>();

    private final Map<String, List<String>> nonSimpleNames = new HashMap<>();

    static Resolution of(DomElement definitions) {
      Resolution resolution = new Resolution();
      if (definitions != null) {
        resolution.resolve(definitions);
      }
      return resolution;
    }

    Map<String, Map<String, Set<String>>> visibleOutputs(String decisionId) {
      return visibleOutputs.getOrDefault(decisionId, Map.of());
    }

    /** Visible names that aren't simple identifiers, such as names with spaces. */
    List<String> nonSimpleNames(String decisionId) {
      return nonSimpleNames.computeIfAbsent(
          decisionId,
          id ->
              visibleOutputs(id).keySet().stream()
                  .filter(name -> !SIMPLE_NAME.matcher(name).matches())
                  .toList());
    }

    /**
     * How Camunda 8 references the output of the given decision: the variable name of the decision
     * (or its ID), followed by the output name if a decision table has several outputs.
     */
    String camunda8Reference(String decisionId, String outputName) {
      Decision decision = decisions.get(decisionId);
      String binding = decision.variableName() != null ? decision.variableName() : decisionId;
      return decision.table() && decision.outputNames().size() > 1
          ? binding + "." + outputName
          : binding;
    }

    private void resolve(DomElement definitions) {
      for (DomElement child : definitions.getChildElements()) {
        String id = child.getAttribute("id");
        if ("decision".equals(child.getLocalName()) && id != null && !decisions.containsKey(id)) {
          decisions.put(
              id,
              new Decision(
                  variableName(child),
                  child(child, "decisionTable") != null,
                  outputNamesOf(child),
                  requiredDecisionIds(child),
                  expressionText(child)));
        }
      }
      Map<String, List<String>> requirements = new HashMap<>();
      Set<String> requiredBySomeDecision = new HashSet<>();
      decisions.forEach(
          (id, decision) -> {
            requirements.put(id, decision.requiredDecisionIds());
            requiredBySomeDecision.addAll(decision.requiredDecisionIds());
          });
      // Every decision that no other decision requires can be called. Decisions that are only
      // reachable through a requirement cycle are treated as callable too.
      List<String> roots = new ArrayList<>();
      decisions.keySet().stream()
          .filter(id -> !requiredBySomeDecision.contains(id))
          .forEach(roots::add);
      Set<String> reached = new HashSet<>();
      for (String root : roots) {
        simulate(root, requirements, reached);
      }
      for (String id : decisions.keySet()) {
        if (!reached.contains(id)) {
          simulate(id, requirements, reached);
        }
      }
    }

    /** Camunda 7's evaluation of the given top-level decision. */
    private void simulate(String top, Map<String, List<String>> requirements, Set<String> reached) {
      List<String> order = new ArrayList<>();
      evaluationOrder(top, requirements, new HashSet<>(), order);
      reached.addAll(order);
      Map<String, String> context = new HashMap<>();
      Map<String, String> nonSimpleContext = new LinkedHashMap<>();
      for (String id : order) {
        Decision decision = decisions.get(id);
        Map<String, Map<String, Set<String>>> visible =
            visibleOutputs.computeIfAbsent(id, k -> new LinkedHashMap<>());
        // Keep only the names the decision mentions, so the resolution doesn't grow with the
        // square of the number of decisions.
        for (String name : decision.mentionedNames()) {
          String provider = context.get(name);
          if (provider != null) {
            see(visible, name, provider, top);
          }
        }
        nonSimpleContext.forEach(
            (name, provider) -> {
              if (decision.expressionText().contains(name)) {
                see(visible, name, provider, top);
              }
            });
        for (String name : decision.outputNames()) {
          context.put(name, id);
          if (!SIMPLE_NAME.matcher(name).matches()) {
            nonSimpleContext.put(name, id);
          }
        }
      }
    }

    private static void see(
        Map<String, Map<String, Set<String>>> visible, String name, String provider, String top) {
      visible
          .computeIfAbsent(name, k -> new LinkedHashMap<>())
          .computeIfAbsent(provider, k -> new LinkedHashSet<>())
          .add(top);
    }

    /** Depth first, required decisions in document order, each decision once. */
    private void evaluationOrder(
        String id,
        Map<String, List<String>> requirements,
        Set<String> started,
        List<String> order) {
      if (!decisions.containsKey(id) || !started.add(id)) {
        return;
      }
      for (String required : requirements.get(id)) {
        evaluationOrder(required, requirements, started, order);
      }
      order.add(id);
    }
  }

  /** What the resolution needs to know about a decision, without references to the document. */
  private record Decision(
      String variableName,
      boolean table,
      List<String> outputNames,
      List<String> requiredDecisionIds,
      String expressionText,
      Set<String> mentionedNames) {

    Decision(
        String variableName,
        boolean table,
        List<String> outputNames,
        List<String> requiredDecisionIds,
        String expressionText) {
      this(
          variableName,
          table,
          outputNames,
          requiredDecisionIds,
          expressionText,
          simpleNames(expressionText));
    }
  }

  /**
   * The text of all expressions of the decision, before conversion to FEEL. The conversion only
   * rewrites names that occur in the original text, also inside string literals, as in {@code
   * execution.getVariable("name")}, so the names in this text include every name the converted
   * expressions can reference.
   */
  private static String expressionText(DomElement decision) {
    StringBuilder text = new StringBuilder();
    appendExpressionText(decision, text);
    return text.toString();
  }

  private static void appendExpressionText(DomElement element, StringBuilder text) {
    for (DomElement child : element.getChildElements()) {
      if ("text".equals(child.getLocalName())) {
        text.append(child.getTextContent()).append('\n');
      } else {
        appendExpressionText(child, text);
      }
    }
  }

  private static Set<String> simpleNames(String text) {
    Set<String> names = new HashSet<>();
    Matcher matcher = SIMPLE_NAME.matcher(text);
    while (matcher.find()) {
      names.add(matcher.group());
    }
    return names;
  }

  private static DomElement findEnclosingDecision(DomElement element) {
    DomElement current = element.getParentElement();
    while (current != null) {
      String localName = current.getLocalName();
      if ("textAnnotation".equals(localName)) {
        return null;
      }
      if ("decision".equals(localName)) {
        return current;
      }
      current = current.getParentElement();
    }
    return null;
  }

  private static List<String> requiredDecisionIds(DomElement decision) {
    List<String> ids = new ArrayList<>();
    for (DomElement requirement : decision.getChildElements()) {
      if (!"informationRequirement".equals(requirement.getLocalName())) {
        continue;
      }
      for (DomElement required : requirement.getChildElements()) {
        if ("requiredDecision".equals(required.getLocalName())) {
          String href = required.getAttribute("href");
          if (href != null && href.startsWith("#") && href.length() > 1) {
            ids.add(href.substring(1));
          }
        }
      }
    }
    return ids;
  }

  private static List<String> outputNamesOf(DomElement decision) {
    List<String> names = new ArrayList<>();
    DomElement table = child(decision, "decisionTable");
    if (table != null) {
      for (DomElement output : table.getChildElements()) {
        if ("output".equals(output.getLocalName())) {
          String name = output.getAttribute("name");
          if (name != null && !name.isBlank()) {
            names.add(name);
          }
        }
      }
      return names;
    }
    String variableName = variableName(decision);
    if (child(decision, "literalExpression") != null && variableName != null) {
      names.add(variableName);
    }
    return names;
  }

  private static String variableName(DomElement decision) {
    DomElement variable = child(decision, "variable");
    if (variable == null) {
      return null;
    }
    String name = variable.getAttribute("name");
    return name == null || name.isBlank() ? null : name;
  }

  private static DomElement child(DomElement element, String localName) {
    if (element == null) {
      return null;
    }
    return element.getChildElements().stream()
        .filter(e -> localName.equals(e.getLocalName()))
        .findFirst()
        .orElse(null);
  }

  private static String blankStringLiterals(String expression) {
    Matcher matcher = STRING_LITERAL.matcher(expression);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(result, " ".repeat(matcher.group().length()));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /** All simple names the expression references as variables, found in one pass. */
  private static Set<String> simpleNameReferences(String expression) {
    Set<String> names = new LinkedHashSet<>();
    Matcher matcher = NAME_REFERENCE.matcher(expression);
    while (matcher.find()) {
      if (!isPathSegment(expression, matcher.start())) {
        names.add(matcher.group(1));
      }
    }
    return names;
  }

  /**
   * Whether the expression references the name as a variable: not as part of a longer name, not as
   * a path segment ({@code x.name}), not as a function call and not as a context key.
   */
  private static boolean isReferenced(String expression, String name) {
    Pattern reference =
        Pattern.compile("(?<![\\w?])" + Pattern.quote(name) + "(?![\\w?])(?!\\s*[(:])");
    Matcher matcher = reference.matcher(expression);
    while (matcher.find()) {
      if (!isPathSegment(expression, matcher.start())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the name starting at the given index follows a path dot ({@code x.name}, {@code x .
   * name}). FEEL allows whitespace around the dot. A range ({@code 0..name}) isn't a path.
   */
  private static boolean isPathSegment(String expression, int start) {
    int i = start - 1;
    while (i >= 0 && Character.isWhitespace(expression.charAt(i))) {
      i--;
    }
    return i >= 0 && expression.charAt(i) == '.' && (i == 0 || expression.charAt(i - 1) != '.');
  }
}
