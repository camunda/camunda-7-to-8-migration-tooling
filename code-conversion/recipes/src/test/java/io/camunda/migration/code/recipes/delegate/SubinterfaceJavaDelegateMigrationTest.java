/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SubinterfaceJavaDelegateMigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("io.camunda.migration.code.recipes.AllDelegateRecipes")
            .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @Test
    void rewriteDelegateImplementingSubinterface() {
        rewriteRun(
            java(
                """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.delegate.JavaDelegate;

                public interface ExampleWorkflowDelegate extends JavaDelegate {
                }
                """),
            java(
                """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.springframework.stereotype.Component;

                @Component
                public class ExampleWorkflowStep implements ExampleWorkflowDelegate {

                    @Override
                    public void execute(DelegateExecution ctx) throws Exception {
                        System.out.println(ctx.getVariable("x"));
                    }

                }
                """,
                """
                package org.camunda.community.migration.example;

                import io.camunda.client.annotation.JobWorker;
                import io.camunda.client.api.response.ActivatedJob;
                import org.springframework.stereotype.Component;

                import java.util.HashMap;
                import java.util.Map;

                @Component
                public class ExampleWorkflowStep {

                    @JobWorker(type = "exampleWorkflowStep", autoComplete = true)
                    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
                        Map<String, Object> resultMap = new HashMap<>();
                        System.out.println(job.getVariable("x"));
                        return resultMap;
                    }

                }
                """));
    }
}
