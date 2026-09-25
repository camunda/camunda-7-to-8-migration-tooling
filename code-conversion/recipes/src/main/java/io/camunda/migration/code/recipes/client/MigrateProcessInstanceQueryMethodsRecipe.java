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
import org.openrewrite.java.tree.TypeUtils;

public class MigrateProcessInstanceQueryMethodsRecipe extends AbstractMigrationRecipe {

  private static final String PROCESS_INSTANCE_STATE = "io.camunda.client.api.search.enums.ProcessInstanceState";
  private static final String PROCESS_INSTANCE_FILTER =
      "io.camunda.client.api.search.filter.ProcessInstanceFilter";
  private static final String PROCESS_INSTANCE_QUERY =
      "org.camunda.bpm.engine.runtime.ProcessInstanceQuery";
  private static final List<MethodMatcher> VARIABLE_FILTER_MATCHERS =
      List.of(
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueEquals(..)"),
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueNotEquals(..)"),
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueGreaterThan(..)"),
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueGreaterThanOrEqual(..)"),
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueLessThan(..)"),
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueLessThanOrEqual(..)"),
          new MethodMatcher(PROCESS_INSTANCE_QUERY + " variableValueLike(..)"));
  private static final MethodMatcher CREATE_PROCESS_INSTANCE_QUERY_MATCHER =
      new MethodMatcher("org.camunda.bpm.engine.RuntimeService createProcessInstanceQuery()");
  private static final MethodMatcher ACTIVE_QUERY_MATCHER =
      new MethodMatcher(PROCESS_INSTANCE_QUERY + " active()");
  private static final MethodMatcher SUSPENDED_QUERY_MATCHER =
      new MethodMatcher(PROCESS_INSTANCE_QUERY + " suspended()");
  private static final Set<String> SUPPORTED_QUERY_FILTER_METHODS =
      Set.of("active", "activityIdIn", "processDefinitionKey", "processInstanceBusinessKey");
  private static final Set<String> SUPPORTED_COUNT_QUERY_METHODS =
      Set.of(
          "active",
          "activityIdIn",
          "count",
          "createProcessInstanceQuery",
          "list",
          "processDefinitionKey",
          "processInstanceBusinessKey");
  private static final Set<String> UNSUPPORTED_QUERY_TERMINALS =
      Set.of("listPage", "singleResult", "unlimitedList");

  @Override
  public @NonNull String getDisplayName() {
    return "Migrates process instance query methods";
  }

  @Override
  public @NonNull String getDescription() {
    return "Replaces supported Camunda 7 process instance query methods with Camunda 8 client methods and leaves queries with unsupported filters, suspended/default state, or unsupported terminals for manual migration.";
  }

  @Override
  protected Predicate<Cursor> visitorSkipCondition() {
    return cursor -> {
      Object value = cursor.getValue();
      if (value instanceof J.MethodInvocation invocation
          && (hasUnsupportedQueryMethodInReceiverChain(invocation)
              || isManualDefaultStateQuery(invocation))) {
        return true;
      }

      Cursor current = cursor;
      while (current != null) {
        if (current.getValue() instanceof J.MethodInvocation invocation
            && (isUnsupportedQueryMethod(invocation) || isUnsupportedQueryTerminal(invocation))) {
          return true;
        }
        current = current.getParent();
      }

      if (value instanceof J.VariableDeclarations declarations
          && declarations.getVariables().stream()
              .anyMatch(
                  variable ->
                      variable.getInitializer() != null
                          && containsUnsupportedQueryMethod(variable.getInitializer()))) {
        return true;
      }
      if (value instanceof J.Assignment assignment
          && containsUnsupportedQueryMethod(assignment.getAssignment())) {
        return true;
      }
      if (value instanceof J.MethodInvocation invocation
          && hasUnsafeQueryAlias(cursor, invocation)) {
        return true;
      }
      return false;
    };
  }

  private static boolean isUnsupportedQueryTerminal(J.MethodInvocation invocation) {
    Expression receiver = invocation.getSelect();
    return UNSUPPORTED_QUERY_TERMINALS.contains(invocation.getSimpleName())
        && receiver != null
        && TypeUtils.isOfClassType(receiver.getType(), PROCESS_INSTANCE_QUERY);
  }

  private static boolean isManualDefaultStateQuery(J.MethodInvocation invocation) {
    String methodName = invocation.getSimpleName();
    if (!methodName.equals("list") && !methodName.equals("count")) {
      return false;
    }

    Expression receiver = invocation.getSelect();
    if (receiver == null || !TypeUtils.isOfClassType(receiver.getType(), PROCESS_INSTANCE_QUERY)) {
      return false;
    }

    while (receiver != null) {
      receiver = unwrapParentheses(receiver);
      if (receiver instanceof J.MethodInvocation methodInvocation) {
        if (ACTIVE_QUERY_MATCHER.matches(methodInvocation)) {
          return false;
        }
        receiver = methodInvocation.getSelect();
      } else {
        break;
      }
    }
    return true;
  }

  private static boolean hasUnsupportedQueryMethodInReceiverChain(J.MethodInvocation invocation) {
    Expression current = invocation.getSelect();
    while (current != null) {
      current = unwrapParentheses(current);
      if (current instanceof J.MethodInvocation receiver) {
        if (isUnsupportedQueryMethod(receiver)) {
          return true;
        }
        current = receiver.getSelect();
      } else {
        return false;
      }
    }
    return false;
  }

  private static boolean hasUnsafeQueryAlias(Cursor cursor, J.MethodInvocation invocation) {
    J.Identifier queryVariable = rootReceiverIdentifier(invocation);
    if (queryVariable == null || queryVariable.getSimpleName().equals("this")) {
      Expression receiver = invocation.getSelect();
      return receiver != null && TypeUtils.isOfClassType(receiver.getType(), PROCESS_INSTANCE_QUERY);
    }

    boolean processInstanceQueryVariable =
        TypeUtils.isOfClassType(queryVariable.getType(), PROCESS_INSTANCE_QUERY);
    Cursor current = cursor;
    while (current != null) {
      if (current.getValue() instanceof J.ClassDeclaration classDeclaration) {
        J.Block classBody = classDeclaration.getBody();
        return hasVariableFilteredQuery(classBody, queryVariable)
            || (processInstanceQueryVariable
                && hasUntraceableProcessInstanceQueryAlias(classBody, queryVariable));
      }
      current = current.getParent();
    }
    return processInstanceQueryVariable;
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
                        && containsVariableFilter(variable.getInitializer()))) {
          hasVariableFilter.set(true);
          return declarations;
        }
        return hasVariableFilter.get()
            ? declarations
            : super.visitVariableDeclarations(declarations, hasVariableFilter);
      }

      @Override
      public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean hasVariableFilter) {
        J.Identifier assignedVariable =
            assignment.getVariable() instanceof J.Identifier identifier
                ? identifier
                : assignment.getVariable() instanceof J.FieldAccess fieldAccess
                    ? fieldAccess.getName()
                    : null;
        if (assignedVariable != null
            && refersToSameVariable(assignedVariable, queryVariable)
            && containsVariableFilter(assignment.getAssignment())) {
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
        if (isVariableFilterMethod(candidate)
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

  private static boolean hasUntraceableProcessInstanceQueryAlias(
      J.Block classBody, J.Identifier queryVariable) {
    AtomicBoolean hasKnownQueryCreation = new AtomicBoolean();
    AtomicBoolean hasUnknownSource = new AtomicBoolean();
    new JavaIsoVisitor<AtomicBoolean>() {
      @Override
      public J.VariableDeclarations visitVariableDeclarations(
          J.VariableDeclarations declarations, AtomicBoolean unknownSource) {
        for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
          if (!refersToSameVariable(variable.getName(), queryVariable)) {
            continue;
          }
          Expression initializer = variable.getInitializer();
          if (initializer == null || !hasProcessInstanceQueryCreation(initializer)) {
            unknownSource.set(true);
          } else {
            hasKnownQueryCreation.set(true);
          }
        }
        return unknownSource.get()
            ? declarations
            : super.visitVariableDeclarations(declarations, unknownSource);
      }

      @Override
      public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean unknownSource) {
        J.Identifier assignedVariable =
            assignment.getVariable() instanceof J.Identifier identifier
                ? identifier
                : assignment.getVariable() instanceof J.FieldAccess fieldAccess
                    ? fieldAccess.getName()
                    : null;
        if (assignedVariable != null && refersToSameVariable(assignedVariable, queryVariable)) {
          if (hasProcessInstanceQueryCreation(assignment.getAssignment())) {
            hasKnownQueryCreation.set(true);
          } else {
            unknownSource.set(true);
          }
        }
        return unknownSource.get()
            ? assignment
            : super.visitAssignment(assignment, unknownSource);
      }
    }.visit(classBody, hasUnknownSource);
    return !hasKnownQueryCreation.get() || hasUnknownSource.get();
  }

  private static boolean hasProcessInstanceQueryCreation(Expression expression) {
    Expression current = expression;
    while (current != null) {
      current = unwrapParentheses(current);
      if (current instanceof J.MethodInvocation invocation) {
        if (CREATE_PROCESS_INSTANCE_QUERY_MATCHER.matches(invocation)) {
          return true;
        }
        current = invocation.getSelect();
      } else {
        return false;
      }
    }
    return false;
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

  private static boolean isVariableFilterMethod(J.MethodInvocation invocation) {
    return VARIABLE_FILTER_MATCHERS.stream().anyMatch(matcher -> matcher.matches(invocation));
  }

  private static boolean isUnsupportedQueryMethod(J.MethodInvocation invocation) {
    return isVariableFilterMethod(invocation)
        || SUSPENDED_QUERY_MATCHER.matches(invocation)
        || (TypeUtils.isOfClassType(invocation.getType(), PROCESS_INSTANCE_QUERY)
            && !SUPPORTED_QUERY_FILTER_METHODS.contains(invocation.getSimpleName())
            && !CREATE_PROCESS_INSTANCE_QUERY_MATCHER.matches(invocation));
  }

  private static boolean containsUnsupportedQueryMethod(Expression expression) {
    Expression current = expression;
    while (current != null) {
      current = unwrapParentheses(current);
      if (current instanceof J.MethodInvocation invocation) {
        if (isUnsupportedQueryMethod(invocation)) {
          return true;
        }
        current = invocation.getSelect();
      } else {
        return false;
      }
    }
    return false;
  }

  private static boolean containsVariableFilter(Expression expression) {
    Expression current = expression;
    while (current != null) {
      current = unwrapParentheses(current);
      if (current instanceof J.MethodInvocation invocation) {
        if (isVariableFilterMethod(invocation)) {
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
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueNotEquals(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueGreaterThan(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueGreaterThanOrEqual(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueLessThan(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueLessThanOrEqual(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery variableValueLike(..)", true),
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
            List.of("elementId(#{activityIdIn:any(java.lang.String)})"), false);
    JavaTemplate unfilteredListWithActive =
        processInstanceSearchTemplate(Collections.emptyList(), false);
    JavaTemplate processDefinitionListWithActive =
        processInstanceSearchTemplate(
            List.of("processDefinitionId(#{processDefinitionKey:any(java.lang.String)})"),
            false);

    addActiveStateVariant(
        specs,
        listMatcher,
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        activityListWithActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        Collections.emptyList(),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    addActiveStateVariant(
        specs,
        listMatcher,
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        unfilteredListWithActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    addActiveStateVariant(
        specs,
        listMatcher,
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionListWithActive,
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        Collections.emptyList(),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
    addActiveStateVariant(
        specs,
        listMatcher,
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionListWithActive,
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
            List.of("elementId(#{activityIdIn:any(java.lang.String)})"), true);
    JavaTemplate unfilteredCountWithActive =
        processInstanceSearchTemplate(Collections.emptyList(), true);
    JavaTemplate processDefinitionCountWithActive =
        processInstanceSearchTemplate(
            List.of("processDefinitionId(#{processDefinitionKey:any(java.lang.String)})"),
            true);
    addCountSpecs(
        specs,
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        activityCountWithActive,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of(),
        Collections.emptyList(),
        unfilteredCountWithActive,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        unfilteredCountWithActive,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")));
    addCountSpecs(
        specs,
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionCountWithActive,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionCountWithActive,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")));
    return specs;
  }

  private static JavaTemplate processInstanceSearchTemplate(
      List<String> filterMethods, boolean count) {
    List<String> methods = new ArrayList<>(filterMethods);
    methods.add("state(ProcessInstanceState.ACTIVE)");
    String filter =
        methods.size() == 1 && count
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
    templateTypes.add(PROCESS_INSTANCE_FILTER);
    templateTypes.add(PROCESS_INSTANCE_STATE);
    if (!count) {
      templateTypes.add("io.camunda.client.api.search.response.ProcessInstance");
    }
    return RecipeUtils.createSimpleJavaTemplate(template, templateTypes.toArray(String[]::new));
  }

  private void addActiveStateVariant(
      List<ReplacementUtils.BuilderReplacementSpec> specs,
      MethodMatcher matcher,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate activeTemplate,
      String returnType,
      List<String> textComments,
      Optional<String> receiverType) {
    specs.add(
        new ReplacementUtils.BuilderReplacementSpec(
            matcher,
            methodNamesToExtractParameters,
            extractedParametersToApply,
            activeTemplate,
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            returnType,
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            textComments,
            Collections.emptyList(),
            List.of(PROCESS_INSTANCE_STATE),
            receiverType,
            Set.of("active")));
  }

  private void addCountSpecs(
      List<ReplacementUtils.BuilderReplacementSpec> specs,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate activeTemplate,
      List<String> textComments) {
    for (String terminalMethod : List.of("list", "count")) {
      addActiveStateVariant(
          specs,
          new MethodMatcher("org.camunda.bpm.engine.query.Query " + terminalMethod + "()"),
          methodNamesToExtractParameters,
          extractedParametersToApply,
          activeTemplate,
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
