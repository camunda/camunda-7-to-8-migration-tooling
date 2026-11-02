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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * Detects expressions that reference the output of a required decision by its bare output name.
 *
 * <p>Camunda 7 evaluates the whole requirement tree in one flat variable context, so a decision can
 * read every output of every decision it requires, directly or indirectly, by output name. Camunda
 * 8 only exposes the results of direct requirements, bound to the decision ID (or the {@code
 * variable} name of a literal expression). Such references evaluate to {@code null} after
 * conversion, so they are reported as tasks.
 */
public class RequiredDecisionOutputReferenceVisitor extends AbstractDmnElementVisitor {
  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

  @Override
  public String localName() {
    return "text";
  }

  @Override
  protected void visitDmnElement(DomElementVisitorContext context) {
    DomElement text = context.getElement();
    DomElement decision = findEnclosingDecision(text);
    if (decision == null) {
      return;
    }
    Map<String, DomElement> decisions = collectDecisions(decision.getParentElement());
    List<String> directRequirements = requiredDecisionIds(decision);
    if (directRequirements.isEmpty()) {
      return;
    }
    Set<String> visibleInCamunda8 = new LinkedHashSet<>();
    for (String id : directRequirements) {
      visibleInCamunda8.add(id);
      DomElement required = decisions.get(id);
      if (required != null) {
        String variableName = variableName(required);
        if (variableName != null) {
          visibleInCamunda8.add(variableName);
        }
      }
    }
    Map<String, List<String>> providersByName = new LinkedHashMap<>();
    for (String id : transitiveRequirements(decision, decisions)) {
      for (String name : outputNames(decisions.get(id))) {
        providersByName.computeIfAbsent(name, n -> new ArrayList<>()).add(id);
      }
    }
    String expression = text.getTextContent();
    if (expression == null || expression.isBlank()) {
      return;
    }
    String searchable = blankStringLiterals(expression);
    String trimmedExpression = expression.trim();
    providersByName.forEach(
        (name, providers) -> {
          if (visibleInCamunda8.contains(name) || !isReferenced(searchable, name)) {
            return;
          }
          if (providers.size() > 1) {
            context.addMessage(
                MessageFactory.ambiguousRequiredDecisionOutputReference(
                    trimmedExpression,
                    name,
                    providers.stream()
                        .map(id -> "'" + id + "'")
                        .collect(Collectors.joining(", "))));
            return;
          }
          String providerId = providers.get(0);
          context.addMessage(
              MessageFactory.requiredDecisionOutputReference(
                  trimmedExpression,
                  name,
                  providerId,
                  camunda8Reference(decisions.get(providerId), name),
                  !directRequirements.contains(providerId)));
        });
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

  private static Map<String, DomElement> collectDecisions(DomElement definitions) {
    Map<String, DomElement> decisions = new LinkedHashMap<>();
    if (definitions == null) {
      return decisions;
    }
    for (DomElement child : definitions.getChildElements()) {
      if ("decision".equals(child.getLocalName())) {
        decisions.put(child.getAttribute("id"), child);
      }
    }
    return decisions;
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

  private static Set<String> transitiveRequirements(
      DomElement decision, Map<String, DomElement> decisions) {
    Set<String> visited = new LinkedHashSet<>();
    Deque<String> toVisit = new ArrayDeque<>(requiredDecisionIds(decision));
    while (!toVisit.isEmpty()) {
      String id = toVisit.pop();
      DomElement required = decisions.get(id);
      if (required != null && visited.add(id)) {
        toVisit.addAll(requiredDecisionIds(required));
      }
    }
    return visited;
  }

  private static List<String> outputNames(DomElement decision) {
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

  private static String camunda8Reference(DomElement decision, String outputName) {
    String id = decision.getAttribute("id");
    DomElement table = child(decision, "decisionTable");
    if (table == null) {
      // literal expression: Camunda 8 binds the result to the variable name
      return outputName;
    }
    long outputs =
        table.getChildElements().stream().filter(e -> "output".equals(e.getLocalName())).count();
    return outputs == 1 ? id : id + "." + outputName;
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
