/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.sharedRecipes.AbstractMigrationRecipe;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class MigrateProcessInstanceQueryMethodsRecipe extends AbstractMigrationRecipe {

  private static final String PROCESS_INSTANCE_STATE = "io.camunda.client.api.search.enums.ProcessInstanceState";
  private static final String PROCESS_INSTANCE_FILTER =
      "io.camunda.client.api.search.filter.ProcessInstanceFilter";
  private static final MethodMatcher VARIABLE_VALUE_EQUALS_MATCHER =
      new MethodMatcher(
          "org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueEquals(..)");
  private static final Set<String> SUPPORTED_COUNT_QUERY_METHODS =
      Set.of(
          "active",
          "activityIdIn",
          "count",
          "createProcessInstanceQuery",
          "list",
          "processDefinitionKey",
          "processInstanceBusinessKey");

  @Override
  public @NonNull String getDisplayName() {
    return "Migrates process instance query methods";
  }

  @Override
  public @NonNull String getDescription() {
    return "Replaces supported Camunda 7 process instance query methods with Camunda 8 client methods and leaves variable-filtered queries for manual migration.";
  }

  @Override
  protected Predicate<Cursor> visitorSkipCondition() {
    return cursor -> {
      // Preserve the entire chain because the 8.9 process-instance search API has no variable filter.
      Cursor current = cursor;
      while (current != null) {
        if (current.getValue() instanceof J.MethodInvocation invocation
            && VARIABLE_VALUE_EQUALS_MATCHER.matches(invocation)) {
          return true;
        }
        current = current.getParent();
      }

      Object value = cursor.getValue();
      if (value instanceof J.VariableDeclarations declarations
          && declarations.getVariables().stream()
              .anyMatch(
                  variable ->
                      variable.getInitializer() != null
                          && containsVariableValueEquals(variable.getInitializer()))) {
        return true;
      }
      if (value instanceof J.Assignment assignment
          && containsVariableValueEquals(assignment.getAssignment())) {
        return true;
      }
      if (value instanceof J.MethodInvocation invocation
          && hasVariableFilteredQueryAlias(cursor, invocation)) {
        return true;
      }
      return false;
    };
  }

  private static boolean hasVariableFilteredQueryAlias(
      Cursor cursor, J.MethodInvocation invocation) {
    J.Identifier queryVariable = rootReceiverIdentifier(invocation);
    if (queryVariable == null) {
      return false;
    }

    Cursor current = cursor;
    while (current != null) {
      if (current.getValue() instanceof J.ClassDeclaration classDeclaration
          && hasVariableFilteredQuery(classDeclaration.getBody(), queryVariable)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private static boolean hasVariableFilteredQuery(
      J.Block classBody, J.Identifier queryVariable) {
    AtomicBoolean found = new AtomicBoolean();
    new JavaIsoVisitor<AtomicBoolean>() {
      @Override
      public J.VariableDeclarations visitVariableDeclarations(
          J.VariableDeclarations declarations, AtomicBoolean hasVariableFilter) {
        if (declarations.getVariables().stream()
            .anyMatch(
                variable ->
                    refersToSameVariable(variable.getName(), queryVariable)
                        && variable.getInitializer() != null
                        && containsVariableValueEquals(variable.getInitializer()))) {
          hasVariableFilter.set(true);
          return declarations;
        }
        return hasVariableFilter.get()
            ? declarations
            : super.visitVariableDeclarations(declarations, hasVariableFilter);
      }

      @Override
      public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean hasVariableFilter) {
        if (assignment.getVariable() instanceof J.Identifier assignedVariable
            && refersToSameVariable(assignedVariable, queryVariable)
            && containsVariableValueEquals(assignment.getAssignment())) {
          hasVariableFilter.set(true);
          return assignment;
        }
        return hasVariableFilter.get()
            ? assignment
            : super.visitAssignment(assignment, hasVariableFilter);
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation candidate, AtomicBoolean hasVariableFilter) {
        J.Identifier receiver = rootReceiverIdentifier(candidate);
        if (VARIABLE_VALUE_EQUALS_MATCHER.matches(candidate)
            && receiver != null
            && refersToSameVariable(receiver, queryVariable)) {
          hasVariableFilter.set(true);
          return candidate;
        }
        return hasVariableFilter.get()
            ? candidate
            : super.visitMethodInvocation(candidate, hasVariableFilter);
      }
    }.visit(classBody, found);
    return found.get();
  }

  private static J.Identifier rootReceiverIdentifier(J.MethodInvocation invocation) {
    Expression receiver = invocation.getSelect();
    while (receiver != null) {
      receiver = unwrapParentheses(receiver);
      if (receiver instanceof J.MethodInvocation methodInvocation) {
        receiver = methodInvocation.getSelect();
      } else if (receiver instanceof J.FieldAccess fieldAccess) {
        return fieldAccess.getName();
      } else {
        return receiver instanceof J.Identifier identifier ? identifier : null;
      }
    }
    return null;
  }

  private static boolean refersToSameVariable(J.Identifier candidate, J.Identifier target) {
    JavaType.Variable candidateVariable = candidate.getFieldType();
    JavaType.Variable targetVariable = target.getFieldType();
    return candidateVariable != null && targetVariable != null
        ? candidateVariable.equals(targetVariable)
        : candidate.getSimpleName().equals(target.getSimpleName());
  }

  private static boolean containsVariableValueEquals(Expression expression) {
    Expression current = expression;
    while (current != null) {
      current = unwrapParentheses(current);
      if (current instanceof J.MethodInvocation invocation) {
        if (VARIABLE_VALUE_EQUALS_MATCHER.matches(invocation)) {
          return true;
        }
        current = invocation.getSelect();
      } else {
        return false;
      }
    }
    return false;
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.or(
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService createProcessInstanceQuery()", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery activityIdIn(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery processInstanceBusinessKey(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery processDefinitionKey(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueEquals(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery active()", true));
  }

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.runtime.ProcessInstanceQuery processDefinitionKey(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newProcessInstanceSearchRequest()
                    .filter(filter -> filter.processDefinitionId(#{processDefinitionKey:any(java.lang.String)}))
                """,
                "io.camunda.client.api.search.request.ProcessInstanceSearchRequestBuilder",
                "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.search.request.ProcessInstanceSearchRequestBuilder",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("processDefinitionKey", 0)),
            List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
            Collections.emptyList(),
            Collections.emptyList(),
            Set.of("processInstanceBusinessKey")),
        new ReplacementUtils.SimpleReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.runtime.ProcessInstanceQuery processDefinitionKey(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newProcessInstanceSearchRequest()
                    .filter(filter -> filter.processDefinitionId(#{processDefinitionKey:any(java.lang.String)}))
                """,
                "io.camunda.client.api.search.request.ProcessInstanceSearchRequestBuilder",
                "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.search.request.ProcessInstanceSearchRequestBuilder",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("processDefinitionKey", 0)),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet()));
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {
    List<ReplacementUtils.BuilderReplacementSpec> specs = new ArrayList<>();
    MethodMatcher listMatcher = new MethodMatcher("org.camunda.bpm.engine.query.Query list()");
    JavaTemplate activityListWithActive =
        processInstanceSearchTemplate(
            List.of("elementId(#{activityIdIn:any(java.lang.String)})"), true, false);
    JavaTemplate activityListWithoutActive =
        processInstanceSearchTemplate(
            List.of("elementId(#{activityIdIn:any(java.lang.String)})"), false, false);
    JavaTemplate unfilteredListWithActive =
        processInstanceSearchTemplate(Collections.emptyList(), true, false);
    JavaTemplate unfilteredListWithoutActive =
        processInstanceSearchTemplate(Collections.emptyList(), false, false);
    JavaTemplate processDefinitionListWithActive =
        processInstanceSearchTemplate(
            List.of("processDefinitionId(#{processDefinitionKey:any(java.lang.String)})"),
            true,
            false);
    JavaTemplate processDefinitionListWithoutActive =
        processInstanceSearchTemplate(
            List.of("processDefinitionId(#{processDefinitionKey:any(java.lang.String)})"),
            false,
            false);

    addStateVariants(
        specs,
        listMatcher,
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        activityListWithActive,
        activityListWithoutActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        Collections.emptyList(),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    addStateVariants(
        specs,
        listMatcher,
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        unfilteredListWithActive,
        unfilteredListWithoutActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    addStateVariants(
        specs,
        listMatcher,
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionListWithActive,
        processDefinitionListWithoutActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        Collections.emptyList(),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    addStateVariants(
        specs,
        listMatcher,
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionListWithActive,
        processDefinitionListWithoutActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    return specs;
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> countBuilderMethodInvocations() {
    List<ReplacementUtils.BuilderReplacementSpec> specs = new ArrayList<>();
    JavaTemplate activityCountWithActive =
        processInstanceSearchTemplate(
            List.of("elementId(#{activityIdIn:any(java.lang.String)})"), true, true);
    JavaTemplate activityCountWithoutActive =
        processInstanceSearchTemplate(
            List.of("elementId(#{activityIdIn:any(java.lang.String)})"), false, true);
    JavaTemplate unfilteredCountWithActive =
        processInstanceSearchTemplate(Collections.emptyList(), true, true);
    JavaTemplate unfilteredCountWithoutActive =
        processInstanceSearchTemplate(Collections.emptyList(), false, true);
    JavaTemplate processDefinitionCountWithActive =
        processInstanceSearchTemplate(
            List.of("processDefinitionId(#{processDefinitionKey:any(java.lang.String)})"),
            true,
            true);
    JavaTemplate processDefinitionCountWithoutActive =
        processInstanceSearchTemplate(
            List.of("processDefinitionId(#{processDefinitionKey:any(java.lang.String)})"),
            false,
            true);
    addCountSpecs(
        specs,
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        activityCountWithActive,
        activityCountWithoutActive,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of(),
        Collections.emptyList(),
        unfilteredCountWithActive,
        unfilteredCountWithoutActive,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        unfilteredCountWithActive,
        unfilteredCountWithoutActive,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")));
    addCountSpecs(
        specs,
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionCountWithActive,
        processDefinitionCountWithoutActive,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionCountWithActive,
        processDefinitionCountWithoutActive,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")));
    return specs;
  }

  private static JavaTemplate processInstanceSearchTemplate(
      List<String> filterMethods, boolean active, boolean count) {
    List<String> methods = new ArrayList<>(filterMethods);
    if (active) {
      methods.add("state(ProcessInstanceState.ACTIVE)");
    }
    String filter =
        methods.isEmpty()
            ? ""
            : methods.size() == 1 && !(active && !count)
                ? "\n    .filter(filter -> filter." + methods.get(0) + ")"
            : "\n    .filter(filter -> filter\n        ."
                + String.join("\n        .", methods)
                + ")";
    String result = count ? ".page()\n    .totalItems()" : ".items()";
    String template =
        """
        #{camundaClient:any(io.camunda.client.CamundaClient)}
            .newProcessInstanceSearchRequest()%s
            .send()
            .join()
            %s
        """
            .formatted(filter, result);

    List<String> templateTypes = new ArrayList<>();
    if (!methods.isEmpty()) {
      templateTypes.add(PROCESS_INSTANCE_FILTER);
    }
    if (active) {
      templateTypes.add(PROCESS_INSTANCE_STATE);
    }
    if (!count) {
      templateTypes.add("io.camunda.client.api.search.response.ProcessInstance");
    }
    return RecipeUtils.createSimpleJavaTemplate(template, templateTypes.toArray(String[]::new));
  }

  private void addStateVariants(
      List<ReplacementUtils.BuilderReplacementSpec> specs,
      MethodMatcher matcher,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate activeTemplate,
      JavaTemplate defaultTemplate,
      String returnType,
      List<String> textComments,
      Optional<String> receiverType) {
    specs.add(
        stateVariant(
            matcher,
            methodNamesToExtractParameters,
            extractedParametersToApply,
            activeTemplate,
            returnType,
            textComments,
            receiverType,
            true));
    specs.add(
        stateVariant(
            matcher,
            methodNamesToExtractParameters,
            extractedParametersToApply,
            defaultTemplate,
            returnType,
            textComments,
            receiverType,
            false));
  }

  private ReplacementUtils.BuilderReplacementSpec stateVariant(
      MethodMatcher matcher,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate template,
      String returnType,
      List<String> textComments,
      Optional<String> receiverType,
      boolean requiresActive) {
    return new ReplacementUtils.BuilderReplacementSpec(
        matcher,
        methodNamesToExtractParameters,
        extractedParametersToApply,
        template,
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        returnType,
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        textComments,
        Collections.emptyList(),
        requiresActive ? List.of(PROCESS_INSTANCE_STATE) : Collections.emptyList(),
        receiverType,
        requiresActive ? Set.of("active") : Collections.emptySet());
  }

  private void addCountSpecs(
      List<ReplacementUtils.BuilderReplacementSpec> specs,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate activeTemplate,
      JavaTemplate defaultTemplate,
      List<String> textComments) {
    for (String terminalMethod : List.of("list", "count")) {
      addStateVariants(
          specs,
          new MethodMatcher("org.camunda.bpm.engine.query.Query " + terminalMethod + "()"),
          methodNamesToExtractParameters,
          extractedParametersToApply,
          activeTemplate,
          defaultTemplate,
          "java.lang.Long",
          textComments,
          Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    }
  }

  @Override
  protected boolean supportsCountedQuery(J.MethodInvocation queryTerminal) {
    Set<String> seenFilters = new HashSet<>();
    Expression current = queryTerminal;

    while (current instanceof J.MethodInvocation invocation) {
      if (invocation.getSimpleName().equals("createProcessInstanceQuery")) {
        return true;
      }

      String methodName = invocation.getSimpleName();
      if (!SUPPORTED_COUNT_QUERY_METHODS.contains(methodName)) {
        return false;
      }
      if (methodName.equals("active")
          && invocation.getArguments().stream().anyMatch(argument -> !(argument instanceof J.Empty))) {
        return false;
      }
      if (methodName.equals("activityIdIn") && !hasSingleStringArgument(invocation)) {
        return false;
      }
      if (methodName.equals("processDefinitionKey") && invocation.getArguments().size() != 1) {
        return false;
      }
      if (methodName.equals("processInstanceBusinessKey")
          && invocation.getArguments().size() != 1) {
        return false;
      }
      if (!invocation.getArguments().isEmpty() && !seenFilters.add(methodName)) {
        return false;
      }
      current = unwrapParentheses(invocation.getSelect());
    }
    return false;
  }

  @Override
  protected J.MethodInvocation adjustCountBuilderReplacement(
      J.MethodInvocation replacement, J.MethodInvocation replacementTarget, Cursor cursor) {
    String accessor = replacementTarget.getSimpleName().equals("size") ? "intValue" : "longValue";
    return (J.MethodInvocation)
        RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Long)}." + accessor + "()")
            .apply(cursor, replacementTarget.getCoordinates().replace(), replacement);
  }

  @Override
  protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
    return List.of();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return List.of();
  }

  private static boolean hasSingleStringArgument(J.MethodInvocation invocation) {
    return invocation.getArguments().size() == 1
        && isStringType(invocation.getArguments().get(0).getType());
  }

  private static boolean isStringType(JavaType type) {
    return type == JavaType.Primitive.String
        || (type instanceof JavaType.FullyQualified fqn
            && fqn.getFullyQualifiedName().equals("java.lang.String"));
  }

  private static Expression unwrapParentheses(Expression expression) {
    while (expression instanceof J.Parentheses<?> parentheses
        && parentheses.getTree() instanceof Expression nested) {
      expression = nested;
    }
    return expression;
  }

}
