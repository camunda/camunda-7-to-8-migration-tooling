/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.testing;

import java.util.Collections;
import java.util.List;
import io.camunda.migration.code.recipes.sharedRecipes.AbstractMigrationRecipe;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils.BuilderReplacementSpec;
import io.camunda.migration.code.recipes.utils.ReplacementUtils.ReturnReplacementSpec;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class ReplaceAssertionsRecipe extends AbstractMigrationRecipe {

  private static final String BPMN_AWARE_TESTS =
      "org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests";
  private static final String CMMN_AWARE_TESTS =
      "org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests";
  private static final String PROCESS_ENGINE_TESTS =
      "org.camunda.bpm.engine.test.assertions.ProcessEngineTests";
  private static final List<String> ASSERTION_IMPORTS_TO_REMOVE =
      List.of(
          PROCESS_ENGINE_TESTS + ".assertThat",
          CMMN_AWARE_TESTS + ".assertThat",
          BPMN_AWARE_TESTS + ".assertThat",
          PROCESS_ENGINE_TESTS,
          CMMN_AWARE_TESTS,
          BPMN_AWARE_TESTS);

  @Override
  public String getDisplayName() {
    return "Convert test assertions";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 test assertions with Camunda 8 CPT assertions.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return new UsesMethod<>(BPMN_AWARE_TESTS + " assertThat(..)", true);
  }

  // Check how to handle variables - can we add MapAssert to C8 assertions?
  // assertThat(processInstance).variables().containsEntry("theAnswer", 42);
  // assertThat(processInstance).hasVariable("theAnswer", 42);

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
            new MethodMatcher(
                BPMN_AWARE_TESTS
                    + " assertThat(org.camunda.bpm.engine.runtime.ProcessInstance)",
                true),
            RecipeUtils.createSimpleJavaTemplate(
                "CamundaAssert.assertThat(#{processInstance:any(io.camunda.client.api.response.ProcessInstanceEvent)})",
                "io.camunda.process.test.api.CamundaAssert"),
            null,
            "io.camunda.process.test.api.assertions.ProcessInstanceAssert",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                    "processInstance", 0, "io.camunda.client.api.response.ProcessInstanceEvent")),
            Collections.emptyList(),
            ASSERTION_IMPORTS_TO_REMOVE,
            List.of("io.camunda.process.test.api.CamundaAssert")),
        new ReplacementUtils.SimpleReplacementSpec(
            new MethodMatcher(
                BPMN_AWARE_TESTS + " assertThat(org.camunda.bpm.engine.task.Task)", true),
            RecipeUtils.createSimpleJavaTemplate(
                "CamundaAssert.assertThat(io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName(#{task:any(io.camunda.client.api.search.response.UserTask)}.getName()))",
                "io.camunda.process.test.api.CamundaAssert",
                "io.camunda.process.test.api.assertions.UserTaskSelectors"),
            null,
            "io.camunda.process.test.api.assertions.UserTaskAssert",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                    "task", 0, "io.camunda.client.api.search.response.UserTask")),
            Collections.emptyList(),
            ASSERTION_IMPORTS_TO_REMOVE,
            List.of(
                "io.camunda.process.test.api.CamundaAssert",
                "io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName")));
  }

  @Override
  protected List<BuilderReplacementSpec> builderMethodInvocations() {
    return Collections.emptyList(); // not used
  }

  @Override
  protected List<ReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return List.of(
        rename("isWaitingAt(..)", "hasActiveElements"),
        rename("isNotWaitingAt(..)", "hasNotActivatedElements"),
        rename("isWaitingAtExactly(..)", "hasActiveElementsExactly"),
        rename("isEnded()", "isCompleted"),
        rename("hasPassed(..)", "hasCompletedElements"),
        rename("hasPassedInOrder(..)", "hasCompletedElementsInOrder"),
        rename("isStarted()", "isCreated"),
        rename("isActive()", "isActive"),
        rename("hasVariables(..)", "hasVariableNames"),
        rename("variables()", "isCreated"),
        new ReplacementUtils.RenameReplacementSpec(
                new MethodMatcher("org.assertj.core.api.AbstractMapAssert containsEntry(..)"),
                "hasVariable"),
        new ReplacementUtils.RenameReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.test.assertions.bpmn.TaskAssert isAssignedTo(..)"),
            "hasAssignee"));
  }

  private ReplacementUtils.RenameReplacementSpec rename(String methodC7, String methodC8) {
    return new ReplacementUtils.RenameReplacementSpec(
        new MethodMatcher(
            "org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert " + methodC7),
        methodC8);
  }
}
