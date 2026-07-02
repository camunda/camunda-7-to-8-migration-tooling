/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.DetectHistoryServiceUsageRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class DetectHistoryServiceUsageTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectHistoryServiceUsageRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void addsCommentToHistoryServiceFieldDeclaration() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class MyService {

                @Autowired
                private HistoryService historyService;
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class MyService {

                // TODO: HistoryService has no direct equivalent in Camunda 8.
                // Use the Orchestration Cluster REST API instead.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                @Autowired
                private HistoryService historyService;
            }
            """));
  }

  @Test
  void addsCommentToCreateHistoricProcessInstanceQuery() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.camunda.bpm.engine.history.HistoricProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            import java.util.List;

            @Component
            public class HistoryQueryService {

                @Autowired
                private HistoryService historyService;

                public List<HistoricProcessInstance> getFinished(String processKey) {
                    return historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey(processKey)
                            .finished()
                            .list();
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.camunda.bpm.engine.history.HistoricProcessInstance;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            import java.util.List;

            @Component
            public class HistoryQueryService {

                // TODO: HistoryService has no direct equivalent in Camunda 8.
                // Use the Orchestration Cluster REST API instead.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                @Autowired
                private HistoryService historyService;

                public List<HistoricProcessInstance> getFinished(String processKey) {
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/process-instances/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    return historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey(processKey)
                            .finished()
                            .list();
                }
            }
            """));
  }

  @Test
  void addsCommentToAllHistoryServiceQueryMethods() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class AllHistoryMethods {

                @Autowired
                private HistoryService historyService;

                public void queries() {
                    historyService.createHistoricActivityInstanceQuery().list();
                    historyService.createHistoricVariableInstanceQuery().list();
                    historyService.createHistoricTaskInstanceQuery().list();
                    historyService.createHistoricDecisionInstanceQuery().list();
                    historyService.createUserOperationLogQuery().list();
                    historyService.createHistoricDetailQuery().list();
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class AllHistoryMethods {

                // TODO: HistoryService has no direct equivalent in Camunda 8.
                // Use the Orchestration Cluster REST API instead.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                @Autowired
                private HistoryService historyService;

                public void queries() {
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/flow-node-instances/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createHistoricActivityInstanceQuery().list();
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/variables/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createHistoricVariableInstanceQuery().list();
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/user-tasks/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createHistoricTaskInstanceQuery().list();
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/decision-instances/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createHistoricDecisionInstanceQuery().list();
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/audit-logs/search (8.9+)
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createUserOperationLogQuery().list();
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/variables/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createHistoricDetailQuery().list();
                }
            }
            """));
  }

  @Test
  void doesNotModifyUnrelatedCode() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.RuntimeService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class RuntimeServiceUser {

                @Autowired
                private RuntimeService runtimeService;

                public void start(String key) {
                    runtimeService.startProcessInstanceByKey(key);
                }
            }
            """));
  }

  @Test
  void doesNotAnnotateHistoryServiceMethodParameter() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.springframework.stereotype.Component;

            @Component
            public class ParameterService {

                public void process(HistoryService historyService) {
                    historyService.createHistoricProcessInstanceQuery().list();
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.HistoryService;
            import org.springframework.stereotype.Component;

            @Component
            public class ParameterService {

                public void process(HistoryService historyService) {
                    // TODO: HistoryService has no direct equivalent in Camunda 8.
                    // Use the Orchestration Cluster REST API: POST /v2/process-instances/search
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    historyService.createHistoricProcessInstanceQuery().list();
                }
            }
            """));
  }
}
