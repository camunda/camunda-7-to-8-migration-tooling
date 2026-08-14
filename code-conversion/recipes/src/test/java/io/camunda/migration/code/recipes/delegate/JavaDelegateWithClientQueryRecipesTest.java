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

public class JavaDelegateWithClientQueryRecipesTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources(
            "io.camunda.migration.code.recipes.AllClientRecipes",
            "io.camunda.migration.code.recipes.AllDelegateRecipes")
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void migrateDelegateWithRuntimeQuery() {
    rewriteRun(
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RuntimeService;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ExampleWorkflowDelegate implements JavaDelegate {

                @Autowired
                private RuntimeService runtimeService;

                @Override
                public void execute(DelegateExecution execution) throws Exception {
                    boolean proceed = runtimeService.createProcessInstanceQuery()
                            .processDefinitionKey("example-workflow-process")
                            .list()
                            .size() % 2 == 0;

                    Object inputValue = execution.getVariable("inputValue");
                    System.out.println("ExampleWorkflowDelegate " + inputValue);

                    execution.setVariable("readyToProceed", proceed);
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import io.camunda.client.annotation.JobWorker;
            import io.camunda.client.api.response.ActivatedJob;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            import java.util.HashMap;
            import java.util.Map;

            @Component
            public class ExampleWorkflowDelegate {

                @Autowired
                private CamundaClient camundaClient;

                @JobWorker(type = "exampleWorkflowDelegate", autoComplete = true)
                public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
                    Map<String, Object> resultMap = new HashMap<>();
                    boolean proceed = camundaClient
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter
                                    .processDefinitionId("example-workflow-process"))
                            .send()
                            .join()
                            .items()
                            .size() % 2 == 0;

                    Object inputValue = job.getVariable("inputValue");
                    System.out.println("ExampleWorkflowDelegate " + inputValue);

                    resultMap.put("readyToProceed", proceed);
                    return resultMap;
                }
            }
            """));
  }
}
