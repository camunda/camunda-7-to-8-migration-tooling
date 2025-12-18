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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class MigrateProcessInstanceQueryMethodsRecipe extends AbstractMigrationRecipe {

  private static final String PROCESS_INSTANCE_STATE = "io.camunda.client.api.search.enums.ProcessInstanceState";

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
                    .filter(filter -> filter.processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(java.lang.String)})))
                """,
                "io.camunda.client.api.search.request.ProcessInstanceSearchRequestBuilder",
                "io.camunda.client.api.search.filter.ProcessInstanceFilter"),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.search.request.ProcessInstanceSearchRequestBuilder",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("processDefinitionKey", 0)),
            List.of(" processInstanceBusinessKey was removed - business key concept changed in Camunda 8"),
            Collections.emptyList(),
            Collections.emptyList()));
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
        List.of(PROCESS_INSTANCE_STATE)));

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
        List.of(" processInstanceBusinessKey was removed - business key concept changed in Camunda 8"),
        Collections.emptyList(),
        List.of(PROCESS_INSTANCE_STATE)));
        

        
    return specs;
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
