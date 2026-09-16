/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.MigrateRepositoryServiceRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceRepositoryServiceMethodsTest implements RewriteTest {

  @Test
  void migratesSupportedDeploymentResources() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.io.InputStream;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private CamundaClient camundaClient;
              private RepositoryService repositoryService;

              void deploy(InputStream stream, String text, String tenantId) {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .addInputStream("stream.bpmn", stream)
                    .addString("text.bpmn", text)
                    .name("orders")
                    .tenantId(tenantId)
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import java.io.InputStream;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private CamundaClient camundaClient;
              private RepositoryService repositoryService;

              void deploy(InputStream stream, String text, String tenantId) {
                  camundaClient
                          .newDeployResourceCommand()
                          .addResourceFromClasspath("bpmn/order.bpmn")
                          .addResourceStream(stream, "stream.bpmn")
                          .addResourceStringUtf8(text, "text.bpmn")
                          .tenantId(tenantId)
                          .send()
                          .join();
              }
            }
            """));
  }

  @Test
  void addsTodoForRepositoryQueries() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              long count() {
                return repositoryService.createDecisionDefinitionQuery().count();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              long count() {
                // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                return repositoryService.createDecisionDefinitionQuery().count();
              }
            }
            """));
  }

  @Test
  void defersDeploymentThatWouldReorderArguments() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import java.io.InputStream;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                repositoryService.createDeployment()
                    .addInputStream(nextName(), nextStream())
                    .deploy();
              }

              private String nextName() {
                return "order.bpmn";
              }

              private InputStream nextStream() {
                return null;
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import java.io.InputStream;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addInputStream(nextName(), nextStream())
                    .deploy();
              }

              private String nextName() {
                return "order.bpmn";
              }

              private InputStream nextStream() {
                return null;
              }
            }
            """));
  }
}
