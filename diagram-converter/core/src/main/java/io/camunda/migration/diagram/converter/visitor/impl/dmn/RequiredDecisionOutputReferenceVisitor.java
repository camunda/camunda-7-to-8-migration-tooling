/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.dmn;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
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
 * <p>Camunda 8 only exposes the results of direct requirements, bound to the decision ID (or the
 * {@code variable} name of a literal expression). Such references evaluate to {@code null} after
 * conversion, so they are reported as tasks, naming the decision Camunda 7 actually reads.
 */
public class RequiredDecisionOutputReferenceVisitor extends AbstractDmnElementVisitor {
  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
  private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_]\\w*");

  /** A name used as a variable: see {@link #isReferenced(String, String)}. */
  private static final Pattern NAME_REFERENCE =
      Pattern.compile("(?<![\\w.?])([A-Za-z_]\\w*)(?![\\w?])(?!\\s*[(:])");

  /**
   * The resolution of the {@code definitions} element visited last on this thread. Elements are
   * visited depth first, so {@code definitions} is always visited before its expressions. Only the
   * hash code of the element is kept, so the document isn't retained after the check.
   */
  private static final ThreadLocal<CachedResolution> CACHE = new ThreadLocal<>();

  private record CachedResolution(int definitionsHash, Resolution resolution) {}

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
    String expression = element.getTextContent();
    if (expression == null || expression.isBlank()) {
      return;
    }
    List<String> directRequirements = requiredDecisionIds(decision);
    Set<String> visibleInCamunda8 = new HashSet<>();
    for (String id : directRequirements) {
      visibleInCamunda8.add(id);
      String variableName = resolution.variableName(id);
      if (variableName != null) {
        visibleInCamunda8.add(variableName);
      }
    }
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
      if (providers == null || visibleInCamunda8.contains(name)) {
        continue;
      }
      if (providers.size() > 1) {
        context.addMessage(
            MessageFactory.ambiguousRequiredDecisionOutputReference(
                trimmedExpression, name, describeProviders(providers)));
        continue;
      }
      String providerId = providers.keySet().iterator().next();
      context.addMessage(
          MessageFactory.requiredDecisionOutputReference(
              trimmedExpression,
              name,
              providerId,
              resolution.camunda8Reference(providerId, name),
              !directRequirements.contains(providerId)));
    }
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
    private final Map<String, DomElement> decisions = new LinkedHashMap<>();
    private final Map<String, List<String>> outputNames = new HashMap<>();

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

    String variableName(String decisionId) {
      return RequiredDecisionOutputReferenceVisitor.variableName(decisions.get(decisionId));
    }

    String camunda8Reference(String decisionId, String outputName) {
      DomElement decision = decisions.get(decisionId);
      DomElement table = child(decision, "decisionTable");
      if (table == null) {
        // literal expression: Camunda 8 binds the result to the variable name
        return outputName;
      }
      return outputNames.get(decisionId).size() == 1 ? decisionId : decisionId + "." + outputName;
    }

    private void resolve(DomElement definitions) {
      for (DomElement child : definitions.getChildElements()) {
        if ("decision".equals(child.getLocalName()) && child.getAttribute("id") != null) {
          decisions.putIfAbsent(child.getAttribute("id"), child);
        }
      }
      Map<String, List<String>> requirements = new HashMap<>();
      Set<String> requiredBySomeDecision = new HashSet<>();
      decisions.forEach(
          (id, decision) -> {
            List<String> required = requiredDecisionIds(decision);
            requirements.put(id, required);
            requiredBySomeDecision.addAll(required);
            outputNames.put(id, outputNamesOf(decision));
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
      for (String id : order) {
        Map<String, Map<String, Set<String>>> visible =
            visibleOutputs.computeIfAbsent(id, k -> new LinkedHashMap<>());
        context.forEach(
            (name, provider) ->
                visible
                    .computeIfAbsent(name, k -> new LinkedHashMap<>())
                    .computeIfAbsent(provider, k -> new LinkedHashSet<>())
                    .add(top));
        for (String name : outputNames.get(id)) {
          context.put(name, id);
        }
      }
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
      names.add(matcher.group(1));
    }
    return names;
  }

  /**
   * Whether the expression references the name as a variable: not as part of a longer name, not as
   * a path segment ({@code x.name}), not as a function call and not as a context key.
   */
  private static boolean isReferenced(String expression, String name) {
    Pattern reference =
        Pattern.compile("(?<![\\w.?])" + Pattern.quote(name) + "(?![\\w?])(?!\\s*[(:])");
    return reference.matcher(expression).find();
  }
}
