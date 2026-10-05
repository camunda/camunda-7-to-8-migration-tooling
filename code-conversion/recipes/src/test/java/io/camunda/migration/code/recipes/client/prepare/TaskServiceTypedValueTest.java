/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.prepare;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.sharedRecipes.ReplaceTypedValueAPIRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class TaskServiceTypedValueTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ReplaceTypedValueAPIRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void preservesTaskLocalScopeForTypedReads() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.TaskService;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class TaskServiceTypedReads {
                void read(TaskService taskService) {
                    IntegerValue processValue = taskService.getVariableTyped("taskId", "processValue");
                    IntegerValue taskValue = taskService.getVariableLocalTyped("taskId", "taskValue");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.TaskService;

            class TaskServiceTypedReads {
                void read(TaskService taskService) {
                    // please check type
                    Integer processValue = (Integer) taskService.getVariable("taskId", "processValue");
                    // please check type
                    Integer taskValue = (Integer) taskService.getVariableLocal("taskId", "taskValue");
                }
            }
            """));
  }
}
