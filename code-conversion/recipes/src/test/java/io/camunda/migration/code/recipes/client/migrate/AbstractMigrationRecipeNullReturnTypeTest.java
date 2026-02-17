/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

/**
 * Regression tests for NPE fix in AbstractMigrationRecipe when return type cannot be resolved.
 *
 * <p>These tests verify that the recipe gracefully handles cases where the cursor message for a
 * variable's return type is null, instead of throwing a NullPointerException.
 *
 * <p>The fix skips transformation for method invocations where the return type cannot be resolved,
 * leaving those specific method calls unchanged for manual migration.
 *
 * @see <a href="https://github.com/camunda-community-hub/camunda-7-to-8-code-conversion/issues/52">
 *     Issue #52</a>
 */
class AbstractMigrationRecipeNullReturnTypeTest implements RewriteTest {

  /**
   * Verifies that the recipe does not crash when accessing methods on a Task obtained via
   * singleResult(), where the return type cannot be resolved from the cursor message.
   */
  @Test
  void taskQuerySingleResultWithMethodAccess() {
    rewriteRun(
        spec ->
            spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.task.Task;
                import io.camunda.client.CamundaClient;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;

                @Component
                public class TaskQuerySingleResultTestClass {

                    @Autowired
                    private TaskService taskService;

                    @Autowired
                    private CamundaClient camundaClient;

                    public String getTaskName(String taskId) {
                        Task task = taskService.createTaskQuery()
                            .taskId(taskId)
                            .singleResult();
                        return task.getName();
                    }
                }
                """));
  }

  /**
   * Verifies that the recipe does not crash when a Task query result is used as input to another
   * query. The task.getProcessInstanceId() call cannot be transformed because the return type for
   * 'task' cannot be resolved, but other transformations should still proceed.
   */
  @Test
  void taskQueryResultUsedInProcessInstanceQuery() {
    rewriteRun(
        spec ->
            // TODO: expectedCyclesThatMakeChanges(2) - see README.md for details
            spec.expectedCyclesThatMakeChanges(2)
                .recipeFromResources("io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.RuntimeService;
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.runtime.ProcessInstance;
                import org.camunda.bpm.engine.task.Task;
                import io.camunda.client.CamundaClient;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;

                import java.util.List;

                @Component
                public class TaskQueryInProcessQueryTestClass {

                    @Autowired
                    private RuntimeService runtimeService;

                    @Autowired
                    private TaskService taskService;

                    @Autowired
                    private CamundaClient camundaClient;

                    public void findProcessInstanceByTaskId(String taskId) {
                        final Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
                        List<ProcessInstance> processInstances = runtimeService
                            .createProcessInstanceQuery()
                            .processInstanceId(task.getProcessInstanceId())
                            .list();
                    }
                }
                """,
            // ProcessInstance type is transformed, but task.getProcessInstanceId() remains unchanged
            """
                package org.camunda.community.migration.example;
                import io.camunda.client.api.search.response.ProcessInstance;
                import io.camunda.client.api.search.response.UserTask;
                import org.camunda.bpm.engine.RuntimeService;
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.task.Task;
                import io.camunda.client.CamundaClient;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;

                import java.util.List;

                @Component
                public class TaskQueryInProcessQueryTestClass {

                    @Autowired
                    private RuntimeService runtimeService;

                    @Autowired
                    private TaskService taskService;

                    @Autowired
                    private CamundaClient camundaClient;

                    public void findProcessInstanceByTaskId(String taskId) {
                        final Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
                        List<ProcessInstance> processInstances = runtimeService
                                .createProcessInstanceQuery()
                                .processInstanceId(task.getProcessInstanceId())
                                .list();
                    }
                }
                """));
  }

  /**
   * Verifies that the recipe does not crash when TaskService is obtained from ProcessEngine
   * parameter. The taskService.complete() call is transformed, but task.getId() remains unchanged
   * because the return type for 'task' cannot be resolved.
   */
  @Test
  void taskServiceFromProcessEngineParameter() {
    rewriteRun(
        spec ->
            spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.ProcessEngine;
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.task.Task;
                import io.camunda.client.CamundaClient;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;

                @Component
                public class StaticMethodWithProcessEngineTestClass {

                    @Autowired
                    private CamundaClient camundaClient;

                    public void completeTask(ProcessEngine processEngine, String businessKey) {
                        TaskService taskService = processEngine.getTaskService();
                        Task task = taskService.createTaskQuery()
                            .processInstanceBusinessKey(businessKey)
                            .singleResult();
                        taskService.complete(task.getId());
                    }
                }
                """,
            // taskService.complete() is transformed, but task.getId() remains unchanged
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.ProcessEngine;
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.task.Task;
                import io.camunda.client.CamundaClient;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;

                @Component
                public class StaticMethodWithProcessEngineTestClass {

                    @Autowired
                    private CamundaClient camundaClient;

                    public void completeTask(ProcessEngine processEngine, String businessKey) {
                        TaskService taskService = processEngine.getTaskService();
                        Task task = taskService.createTaskQuery()
                            .processInstanceBusinessKey(businessKey)
                            .singleResult();
                        camundaClient
                                .newCompleteUserTaskCommand(Long.valueOf(task.getId()))
                                .send()
                                .join();
                    }
                }
                """));
  }
}
