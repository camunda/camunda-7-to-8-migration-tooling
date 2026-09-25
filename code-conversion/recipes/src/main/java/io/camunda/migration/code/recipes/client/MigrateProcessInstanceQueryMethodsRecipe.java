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
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
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
  private static final String VARIABLE_VALUE_EQUALS = "variableValueEquals";
  private static final String VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT =
      VARIABLE_VALUE_EQUALS + "Arg1";
  private static final Set<String> SUPPORTED_COUNT_QUERY_METHODS =
      Set.of(
          "active",
          "activityIdIn",
          "count",
          "createProcessInstanceQuery",
          "list",
          "processDefinitionKey",
          "processInstanceBusinessKey",
          VARIABLE_VALUE_EQUALS);

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
    JavaTemplate variableListTemplate =
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .variables(java.util.Collections.singletonMap(#{variableName:any(java.lang.String)}, #{variableValue:any(java.lang.Object)}))
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            PROCESS_INSTANCE_FILTER);
    JavaTemplate processDefinitionAndVariableListTemplate =
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .variables(java.util.Collections.singletonMap(#{variableName:any(java.lang.String)}, #{variableValue:any(java.lang.Object)}))
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items()
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            PROCESS_INSTANCE_FILTER);
    JavaTemplate variableSingleResultTemplate =
        RecipeUtils.createSimpleJavaTemplate(
            """
            java.util.Optional.of(
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newProcessInstanceSearchRequest()
                    .page(page -> page.limit(1))
                    .filter(filter -> filter
                        .variables(java.util.Collections.singletonMap(#{variableName:any(java.lang.String)}, #{variableValue:any(java.lang.Object)}))
                        .state(ProcessInstanceState.ACTIVE))
                    .send()
                    .join())
                .map(response -> {
                    if (response.page().totalItems() > 1) {
                        throw new java.lang.IllegalStateException("Process-instance query returned more than one result");
                    }
                    return response.items().stream().findFirst().orElse(null);
                })
                .orElse(null)
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            PROCESS_INSTANCE_FILTER);
    JavaTemplate processDefinitionAndVariableSingleResultTemplate =
        RecipeUtils.createSimpleJavaTemplate(
            """
            java.util.Optional.of(
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newProcessInstanceSearchRequest()
                    .page(page -> page.limit(1))
                    .filter(filter -> filter
                        .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                        .variables(java.util.Collections.singletonMap(#{variableName:any(java.lang.String)}, #{variableValue:any(java.lang.Object)}))
                        .state(ProcessInstanceState.ACTIVE))
                    .send()
                    .join())
                .map(response -> {
                    if (response.page().totalItems() > 1) {
                        throw new java.lang.IllegalStateException("Process-instance query returned more than one result");
                    }
                    return response.items().stream().findFirst().orElse(null);
                })
                .orElse(null)
            """,
            PROCESS_INSTANCE_STATE,
            "io.camunda.client.api.search.response.ProcessInstance",
            PROCESS_INSTANCE_FILTER);

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

    specs.add(
        new ReplacementUtils.BuilderReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
            Set.of(VARIABLE_VALUE_EQUALS, VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            List.of(VARIABLE_VALUE_EQUALS, VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            variableListTemplate,
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "List<io.camunda.client.api.search.response.ProcessInstance>",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(PROCESS_INSTANCE_STATE),
            Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(
        new ReplacementUtils.BuilderReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.query.Query list()"),
            Set.of(
                "processDefinitionKey",
                VARIABLE_VALUE_EQUALS,
                VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            List.of(
                "processDefinitionKey",
                VARIABLE_VALUE_EQUALS,
                VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            processDefinitionAndVariableListTemplate,
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "List<io.camunda.client.api.search.response.ProcessInstance>",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(PROCESS_INSTANCE_STATE),
            Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(
        new ReplacementUtils.BuilderReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.query.Query singleResult()"),
            Set.of(VARIABLE_VALUE_EQUALS, VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            List.of(VARIABLE_VALUE_EQUALS, VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            variableSingleResultTemplate,
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.search.response.ProcessInstance",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(PROCESS_INSTANCE_STATE),
            Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    specs.add(
        new ReplacementUtils.BuilderReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.query.Query singleResult()"),
            Set.of(
                "processDefinitionKey",
                VARIABLE_VALUE_EQUALS,
                VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            List.of(
                "processDefinitionKey",
                VARIABLE_VALUE_EQUALS,
                VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
            processDefinitionAndVariableSingleResultTemplate,
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.search.response.ProcessInstance",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(PROCESS_INSTANCE_STATE),
            Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery")));

    return specs;
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> countBuilderMethodInvocations() {
    JavaTemplate activityCountTemplate =
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
            PROCESS_INSTANCE_FILTER);
    JavaTemplate activeCountTemplate =
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
            PROCESS_INSTANCE_FILTER);
    JavaTemplate processDefinitionCountTemplate =
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
            PROCESS_INSTANCE_FILTER);
    JavaTemplate variableCountTemplate =
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .variables(java.util.Collections.singletonMap(#{variableName:any(java.lang.String)}, #{variableValue:any(java.lang.Object)}))
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            PROCESS_INSTANCE_FILTER);
    JavaTemplate processDefinitionAndVariableCountTemplate =
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                    .processDefinitionId(#{processDefinitionKey:any(java.lang.String)})
                    .variables(java.util.Collections.singletonMap(#{variableName:any(java.lang.String)}, #{variableValue:any(java.lang.Object)}))
                    .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .page()
                .totalItems()
            """,
            PROCESS_INSTANCE_STATE,
            PROCESS_INSTANCE_FILTER);

    List<ReplacementUtils.BuilderReplacementSpec> specs = new ArrayList<>();
    addCountSpecs(
        specs,
        Set.of("activityIdIn"),
        List.of("activityIdIn"),
        activityCountTemplate,
        Collections.emptyList());
    addCountSpecs(
        specs, Set.of(), Collections.emptyList(), activeCountTemplate, Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of("processInstanceBusinessKey"),
        Collections.emptyList(),
        activeCountTemplate,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")));
    addCountSpecs(
        specs,
        Set.of("processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionCountTemplate,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of("processInstanceBusinessKey", "processDefinitionKey"),
        List.of("processDefinitionKey"),
        processDefinitionCountTemplate,
        List.of(RecipeUtils.businessIdHint("processInstanceBusinessKey")));
    addCountSpecs(
        specs,
        Set.of(VARIABLE_VALUE_EQUALS, VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
        List.of(VARIABLE_VALUE_EQUALS, VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
        variableCountTemplate,
        Collections.emptyList());
    addCountSpecs(
        specs,
        Set.of(
            "processDefinitionKey",
            VARIABLE_VALUE_EQUALS,
            VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
        List.of(
            "processDefinitionKey",
            VARIABLE_VALUE_EQUALS,
            VARIABLE_VALUE_EQUALS_SECOND_ARGUMENT),
        processDefinitionAndVariableCountTemplate,
        Collections.emptyList());
    return specs;
  }

  private void addCountSpecs(
      List<ReplacementUtils.BuilderReplacementSpec> specs,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate template,
      List<String> textComments) {
    specs.add(
        countSpec(
            "list",
            methodNamesToExtractParameters,
            extractedParametersToApply,
            template,
            textComments));
    specs.add(
        countSpec(
            "count",
            methodNamesToExtractParameters,
            extractedParametersToApply,
            template,
            textComments));
  }

  private ReplacementUtils.BuilderReplacementSpec countSpec(
      String terminalMethod,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate template,
      List<String> textComments) {
    return new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.query.Query " + terminalMethod + "()"),
        methodNamesToExtractParameters,
        extractedParametersToApply,
        template,
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.Long",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        textComments,
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE),
        Optional.of("org.camunda.bpm.engine.runtime.ProcessInstanceQuery"));
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
      if (methodName.equals("active") && !invocation.getArguments().isEmpty()) {
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
      if (methodName.equals(VARIABLE_VALUE_EQUALS) && invocation.getArguments().size() != 2) {
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
