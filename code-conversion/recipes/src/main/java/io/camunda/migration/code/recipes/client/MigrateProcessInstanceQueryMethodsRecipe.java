/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.sharedRecipes.AbstractMigrationRecipe;
import io.camunda.migration.code.recipes.utils.MigrationMessages;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import java.util.*;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class MigrateProcessInstanceQueryMethodsRecipe extends AbstractMigrationRecipe {

  private static final String PROCESS_INSTANCE_STATE = "io.camunda.client.api.search.enums.ProcessInstanceState";
  private static final Set<String> UNFILTERED_QUERY_METHODS =
      Set.of("active", "count", "createProcessInstanceQuery", "getRuntimeService", "list");

  @Override
  public @NonNull String getDisplayName() {
    return "Migrates process instance query methods";
  }

  @Override
  public @NonNull String getDescription() {
    return "Replaces Camunda 7 process instance query methods with Camunda 8 client methods.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.or(
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService createProcessInstanceQuery()", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery activityIdIn(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery processInstanceBusinessKey(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.runtime.ProcessInstanceQuery processDefinitionKey(..)", true),
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

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .elementId(#{activityIdIn:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items()
            """,
            "io.camunda.client.api.search.response.ProcessInstance",
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items()
            """,
            "io.camunda.client.api.search.response.ProcessInstance",
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "List<io.camunda.client.api.search.response.ProcessInstance>",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    return specs;
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> countBuilderMethodInvocations() {
    List<ReplacementUtils.BuilderReplacementSpec> specs = new ArrayList<>();

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .elementId(#{activityIdIn:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query count()"),
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .elementId(#{activityIdIn:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of(),
        Collections.emptyList(),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query count()"),
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query count()"),
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query count()"),
        Set.of(),
        Collections.emptyList(),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query count()"),
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    return specs;
  }

  @Override
  protected boolean isBuilderReplacementApplicable(
      ReplacementUtils.BuilderReplacementSpec spec,
      J.MethodInvocation queryTerminal,
      Map<String, Expression> collectedArgs) {
    if (!hasCreateProcessInstanceQueryInReceiverChain(queryTerminal)) {
      return false;
    }

    if (spec.methodNamesToExtractParameters().contains("activityIdIn")
        && !hasSupportedActivityIdIn(queryTerminal)) {
      return false;
    }

    if (spec.methodNamesToExtractParameters().isEmpty()
        && (queryTerminal.getSimpleName().equals("list")
            || queryTerminal.getSimpleName().equals("count"))
        && !hasMethodInReceiverChain(queryTerminal, "active")) {
      return false;
    }

    Expression current = queryTerminal;
    while (current instanceof J.MethodInvocation invocation) {
      if (invocation.getSimpleName().equals("createProcessInstanceQuery")) {
        return true;
      }
      if (!UNFILTERED_QUERY_METHODS.contains(invocation.getSimpleName())
          && !spec.methodNamesToExtractParameters().contains(invocation.getSimpleName())) {
        return false;
      }
      current = unwrapParentheses(invocation.getSelect());
    }
    return true;
  }

  private boolean hasSupportedActivityIdIn(J.MethodInvocation queryTerminal) {
    Expression current = unwrapParentheses(queryTerminal.getSelect());
    while (current instanceof J.MethodInvocation invocation) {
      if (invocation.getSimpleName().equals("activityIdIn")) {
        return invocation.getArguments().size() == 1
            && isStringType(invocation.getArguments().get(0).getType());
      }
      current = unwrapParentheses(invocation.getSelect());
    }
    return false;
  }

  private boolean hasMethodInReceiverChain(
      J.MethodInvocation invocation, String methodName) {
    Expression current = unwrapParentheses(invocation.getSelect());
    while (current instanceof J.MethodInvocation methodInvocation) {
      if (methodInvocation.getSimpleName().equals(methodName)) {
        return true;
      }
      current = unwrapParentheses(methodInvocation.getSelect());
    }
    return false;
  }

  private boolean isStringType(JavaType type) {
    return type instanceof JavaType.FullyQualified fqn
        && fqn.getFullyQualifiedName().equals("java.lang.String");
  }

  @Override
  protected boolean shouldStopBuilderTraversal(J.MethodInvocation invocation) {
    return hasCreateProcessInstanceQueryInReceiverChain(invocation);
  }

  @Override
  protected J.MethodInvocation adjustBuilderReplacement(
      J.MethodInvocation replacement, J.MethodInvocation replacementTarget, Cursor cursor) {
    if (replacementTarget.getSimpleName().equals("size")) {
      if (sizeResultType(cursor, replacementTarget) == SizeResultType.LONG) {
        return replacement;
      }

      return (J.MethodInvocation)
          RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Long)}.intValue()")
              .apply(cursor, replacementTarget.getCoordinates().replace(), replacement);
    }

    if (!replacementTarget.getSimpleName().equals("count")) {
      return replacement;
    }

    return (J.MethodInvocation)
        RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Long)}.longValue()")
            .apply(cursor, replacementTarget.getCoordinates().replace(), replacement);
  }

  @Override
  protected boolean preserveVariableDeclarationType(
      J.VariableDeclarations declarations,
      J.MethodInvocation invocation,
      ReplacementUtils.ReplacementSpec spec) {
    return invocation.getSimpleName().equals("size")
        || invocation.getSimpleName().equals("count");
  }

  @Override
  protected boolean preserveAssignmentType(
      J.Assignment assignment,
      J.MethodInvocation invocation,
      ReplacementUtils.ReplacementSpec spec) {
    return invocation.getSimpleName().equals("size")
        || invocation.getSimpleName().equals("count");
  }

  @Override
  protected boolean shouldTrackQueryResultVariable(
      J.MethodInvocation invocation, ReplacementUtils.ReplacementSpec spec) {
    return invocation.getSimpleName().equals("list");
  }

  @Override
  protected String manualMigrationComment(J.MethodInvocation invocation, Cursor cursor) {
    Expression select = unwrapParentheses(invocation.getSelect());
    String variableName = null;
    if (invocation.getSimpleName().equals("size")) {
      variableName = getTrackedVariableName(select);
    } else if (invocation.getSimpleName().equals("count")
        && select instanceof J.MethodInvocation stream
        && stream.getSimpleName().equals("stream")
        ) {
      variableName = getTrackedVariableName(unwrapParentheses(stream.getSelect()));
    }

    return variableName != null && isTrackedQueryResultVariable(variableName, cursor)
        ? MigrationMessages.formatQueryResultCount(variableName)
        : null;
  }

  private String getTrackedVariableName(Expression expression) {
    if (expression instanceof J.Identifier identifier) {
      return identifier.getSimpleName();
    }
    if (expression instanceof J.FieldAccess fieldAccess) {
      return fieldAccess.getName().getSimpleName();
    }
    return null;
  }

  private SizeResultType sizeResultType(Cursor cursor, J.MethodInvocation replacementTarget) {
    Cursor current = cursor.getParent();
    while (current != null && !(current.getValue() instanceof Tree)) {
      current = current.getParent();
    }
    while (current != null) {
      Object value = current.getValue();

      if (value instanceof J.NewArray newArray
          && newArray.getInitializer() != null
          && (isIntContextType(newArray.getType())
              || (newArray.getTypeExpression() != null
                  && isIntContextType(newArray.getTypeExpression().getType())))
          && newArray.getInitializer().stream()
              .anyMatch(
                  initializer -> containsReplacementTarget(initializer, replacementTarget))) {
        return SizeResultType.INT;
      }

      if (value instanceof J.ArrayDimension arrayDimension
          && containsReplacementTarget(arrayDimension.getIndex(), replacementTarget)) {
        return SizeResultType.INT;
      }

      if (value instanceof J.Switch switchStatement
          && containsReplacementTarget(switchStatement.getSelector().getTree(), replacementTarget)) {
        return SizeResultType.INT;
      }

      if (value instanceof J.SwitchExpression switchExpression
          && containsReplacementTarget(switchExpression.getSelector().getTree(), replacementTarget)) {
        return SizeResultType.INT;
      }

      if (value instanceof J.VariableDeclarations declarations) {
        SizeResultType resultType = resultTypeFor(declarations.getType());
        if (resultType != SizeResultType.UNKNOWN) {
          return resultType;
        }
        resultType = functionalResultType(declarations.getType());
        if (resultType != SizeResultType.UNKNOWN) {
          return resultType;
        }
      }

      if (value instanceof J.Assignment assignment && assignment.getVariable() != null) {
        SizeResultType resultType = resultTypeFor(assignment.getVariable().getType());
        if (resultType != SizeResultType.UNKNOWN) {
          return resultType;
        }
      }

      if (value instanceof J.AssignmentOperation assignmentOperation
          && assignmentOperation.getVariable() != null
          && resultTypeFor(assignmentOperation.getVariable().getType())
              != SizeResultType.UNKNOWN) {
        return resultTypeFor(assignmentOperation.getVariable().getType());
      }

      if (value instanceof J.MethodInvocation methodInvocation) {
        for (int i = 0; i < methodInvocation.getArguments().size(); i++) {
          if (containsReplacementTarget(methodInvocation.getArguments().get(i), replacementTarget)) {
            JavaType.Method methodType = methodInvocation.getMethodType();
            if (methodType != null
                && i < methodType.getParameterTypes().size()) {
              SizeResultType resultType = resultTypeFor(methodType.getParameterTypes().get(i));
              if (resultType != SizeResultType.UNKNOWN) {
                return resultType;
              }
            }
          }
        }
      }

      if (value instanceof J.NewClass newClass) {
        for (int i = 0; i < newClass.getArguments().size(); i++) {
          if (containsReplacementTarget(newClass.getArguments().get(i), replacementTarget)) {
            JavaType.Method constructorType = newClass.getConstructorType();
            if (constructorType != null && i < constructorType.getParameterTypes().size()) {
              SizeResultType resultType =
                  resultTypeFor(constructorType.getParameterTypes().get(i));
              if (resultType != SizeResultType.UNKNOWN) {
                return resultType;
              }
            }
          }
        }
      }

      if (value instanceof J.Lambda lambda) {
        SizeResultType resultType = lambdaResultType(lambda);
        if (resultType != SizeResultType.UNKNOWN) {
          return resultType;
        }
      }

      if (value instanceof J.Return) {
        J.Lambda lambda = cursor.firstEnclosing(J.Lambda.class);
        if (lambda == null) {
          J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
          if (method != null && method.getReturnTypeExpression() != null) {
            return resultTypeFor(method.getReturnTypeExpression().getType());
          }
        }
        if (lambda != null) {
          SizeResultType resultType = lambdaResultType(lambda);
          if (resultType != SizeResultType.UNKNOWN) {
            return resultType;
          }
        }
      }

      current = current.getParent();
      while (current != null && !(current.getValue() instanceof Tree)) {
        current = current.getParent();
      }
    }
    return SizeResultType.UNKNOWN;
  }

  private enum SizeResultType {
    INT,
    LONG,
    UNKNOWN
  }

  private SizeResultType resultTypeFor(JavaType type) {
    if (isIntType(type)
        || (type instanceof JavaType.Array array && isIntType(array.getElemType()))) {
      return SizeResultType.INT;
    }
    if (isLongType(type)
        || (type instanceof JavaType.Array array && isLongType(array.getElemType()))) {
      return SizeResultType.LONG;
    }
    return SizeResultType.UNKNOWN;
  }

  private boolean isIntContextType(JavaType type) {
    return resultTypeFor(type) == SizeResultType.INT;
  }

  private SizeResultType lambdaResultType(J.Lambda lambda) {
    return functionalResultType(lambda.getType());
  }

  private SizeResultType functionalResultType(JavaType type) {
    if (type instanceof JavaType.Method method) {
      return resultTypeFor(method.getReturnType());
    }
    if (type instanceof JavaType.FullyQualified fullyQualified) {
      boolean hasIntReturn =
          fullyQualified.getMethods().stream()
              .anyMatch(method -> resultTypeFor(method.getReturnType()) == SizeResultType.INT);
      boolean hasLongReturn =
          fullyQualified.getMethods().stream()
              .anyMatch(method -> resultTypeFor(method.getReturnType()) == SizeResultType.LONG);
      if (hasIntReturn && !hasLongReturn) {
        return SizeResultType.INT;
      }
      if (hasLongReturn && !hasIntReturn) {
        return SizeResultType.LONG;
      }
    }
    return SizeResultType.UNKNOWN;
  }

  private boolean isLongType(JavaType type) {
    return type == JavaType.Primitive.Long
        || (type instanceof JavaType.FullyQualified fqn
            && fqn.getFullyQualifiedName().equals("java.lang.Long"));
  }

  private boolean isReplacementTarget(J expression, J.MethodInvocation replacementTarget) {
    J current = expression;
    while (current instanceof J.Parentheses<?> parentheses) {
      current = parentheses.getTree();
    }
    return current.getId().equals(replacementTarget.getId());
  }

  private boolean containsReplacementTarget(J expression, J.MethodInvocation replacementTarget) {
    if (isReplacementTarget(expression, replacementTarget)) {
      return true;
    }

    boolean[] found = {false};
    new JavaIsoVisitor<Object>() {
      @Override
      public J visit(Tree tree, Object ctx) {
        if (tree.getId().equals(replacementTarget.getId())) {
          found[0] = true;
          return (J) tree;
        }
        return super.visit(tree, ctx);
      }
    }.visit(expression, null);
    return found[0];
  }

  private boolean isIntType(JavaType type) {
    return type == JavaType.Primitive.Int
        || (type instanceof JavaType.FullyQualified fqn
            && fqn.getFullyQualifiedName().equals("java.lang.Integer"));
  }

  private boolean hasCreateProcessInstanceQueryInReceiverChain(J.MethodInvocation invocation) {
    Expression current = invocation.getSelect();
    current = unwrapParentheses(current);
    while (current instanceof J.MethodInvocation methodInvocation) {
      if (methodInvocation.getSimpleName().equals("createProcessInstanceQuery")) {
        return true;
      }
      current = unwrapParentheses(methodInvocation.getSelect());
    }
    return false;
  }

  private Expression unwrapParentheses(Expression expression) {
    while (expression instanceof J.Parentheses<?> parentheses
        && parentheses.getTree() instanceof Expression nested) {
      expression = nested;
    }
    return expression;
  }

  @Override
  protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
    return List.of();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return List.of();
  }
}
