/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class MigrateExecutionRecipeWarningTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("io.camunda.migration.code.recipes.AllDelegateMigrateRecipes")
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void warnsWhenJobWorkerStubIsMissing() {
    rewriteRun(
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            @Component
            public class MissingStubDelegate implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) throws Exception {
                    execution.setVariable("done", true);
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            /* The delegate execute(DelegateExecution) method exists, but no generated executeJob(ActivatedJob) stub was found. The delegate body could not be copied automatically; migrate it manually.*/@Component
            public class MissingStubDelegate implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) throws Exception {
                    execution.setVariable("done", true);
                }
            }
            """));
  }
}
