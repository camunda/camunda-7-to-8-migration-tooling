/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateApplicationPropertiesTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources(
        "io.camunda.migration.code.recipes.MigrateApplicationPropertiesRecipe");
  }

  @Test
  void removesCamundaBpmAndAddsClientSkeletonInPropertiesFile() {
    rewriteRun(
        properties(
            """
            spring.datasource.url=jdbc:h2:file:./camunda-h2-database
            spring.datasource.username=sa
            spring.datasource.password=sa

            server.port=7070

            camunda.bpm.admin-user.id=demo
            camunda.bpm.admin-user.password=demo
            camunda.bpm.job-execution.deployment-aware=true
            camunda.bpm.history-level=full
            """,
            """
            camunda.client.security.plaintext=true
            # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
            camunda.client.zeebe.grpc-address=http://localhost:26500
            camunda.client.zeebe.rest-address=http://localhost:8080
            spring.datasource.url=jdbc:h2:file:./camunda-h2-database
            spring.datasource.username=sa
            spring.datasource.password=sa

            server.port=7070
            # TODO(migration): Camunda 8 runs job workers remotely (no embedded job executor). Tune worker concurrency via camunda.client.worker.defaults.max-jobs-active and camunda.client.execution-threads.""",
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void removesCamundaBpmAndAddsClientSkeletonInYamlFile() {
    rewriteRun(
        yaml(
            """
            server:
              port: 7070
            spring:
              datasource:
                url: jdbc:h2:file:./camunda-h2-database
                username: sa
                password: sa
            camunda:
              bpm:
                admin-user:
                  id: demo
                  password: demo
                history-level: full
            """,
            """
            server:
              port: 7070
            spring:
              datasource:
                url: jdbc:h2:file:./camunda-h2-database
                username: sa
                password: sa
            camunda:
              client:
                # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
                zeebe:
                  grpc-address: http://localhost:26500
                  rest-address: http://localhost:8080
                security:
                  plaintext: true
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }

  /**
   * Litmus: a Camunda 7 key that is not one of the well-known ones must still be removed, because
   * the whole {@code camunda.bpm.*} namespace is deleted by prefix rather than by enumeration.
   */
  @Test
  void removesUnknownCamundaBpmKeys() {
    rewriteRun(
        properties(
            """
            camunda.bpm.some.future.key.we.do.not.know=value
            server.port=7070
            """,
            """
            camunda.client.security.plaintext=true
            # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
            camunda.client.zeebe.grpc-address=http://localhost:26500
            camunda.client.zeebe.rest-address=http://localhost:8080
            server.port=7070""",
            spec -> spec.path("src/main/resources/application.properties")));
  }

  /**
   * Spring Boot also accepts dotted keys in YAML, so `camunda.bpm.*` written as flat keys must be
   * removed too, not only the nested `camunda: { bpm: ... }` form.
   */
  @Test
  void removesDottedCamundaBpmKeysInYamlFile() {
    rewriteRun(
        yaml(
            """
            server:
              port: 7070
            camunda.bpm.admin-user.id: demo
            camunda.bpm.admin-user.password: demo
            """,
            """
            server:
              port: 7070
            camunda:
              client:
                # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
                zeebe:
                  grpc-address: http://localhost:26500
                  rest-address: http://localhost:8080
                security:
                  plaintext: true
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  /** The `.yaml` extension must be handled just like `.yml`. */
  @Test
  void handlesApplicationYamlExtension() {
    rewriteRun(
        yaml(
            """
            camunda:
              bpm:
                admin-user:
                  id: demo
            """,
            """
            camunda:
              client:
                # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
                zeebe:
                  grpc-address: http://localhost:26500
                  rest-address: http://localhost:8080
                security:
                  plaintext: true
            """,
            spec -> spec.path("src/main/resources/application.yaml")));
  }

  /**
   * A removed `camunda.bpm.job-execution.*` namespace leaves a single TODO hint pointing at the
   * Camunda 8 worker-concurrency settings, since the value cannot be translated mechanically.
   */
  @Test
  void emitsJobExecutionHintInProperties() {
    rewriteRun(
        properties(
            """
            camunda.bpm.job-execution.core-pool-size=5
            camunda.bpm.job-execution.max-pool-size=10
            server.port=7070
            """,
            """
            camunda.client.security.plaintext=true
            # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
            camunda.client.zeebe.grpc-address=http://localhost:26500
            camunda.client.zeebe.rest-address=http://localhost:8080
            # TODO(migration): Camunda 8 runs job workers remotely (no embedded job executor). Tune worker concurrency via camunda.client.worker.defaults.max-jobs-active and camunda.client.execution-threads.
            server.port=7070""",
            spec -> spec.path("src/main/resources/application.properties")));
  }

  /** Same job-execution hint is emitted for YAML. */
  @Test
  void emitsJobExecutionHintInYaml() {
    rewriteRun(
        yaml(
            """
            server:
              port: 7070
            camunda:
              bpm:
                job-execution:
                  core-pool-size: 5
            """,
            """
            # TODO(migration): Camunda 8 runs job workers remotely (no embedded job executor). Tune worker concurrency via camunda.client.worker.defaults.max-jobs-active and camunda.client.execution-threads.
            server:
              port: 7070
            camunda:
              client:
                # TODO: review the Camunda 8 connection settings for your deployment (defaults target a local c8run cluster)
                zeebe:
                  grpc-address: http://localhost:26500
                  rest-address: http://localhost:8080
                security:
                  plaintext: true
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }

  /** Files that are not Spring Boot application config must be left untouched. */
  @Test
  void leavesNonApplicationPropertiesFilesUntouched() {
    rewriteRun(
        properties(
            """
            camunda.bpm.admin-user.id=demo
            server.port=7070
            """,
            spec -> spec.path("src/main/resources/some-other-config.properties")));
  }

  /** Non-application YAML files must be left untouched as well. */
  @Test
  void leavesNonApplicationYamlFilesUntouched() {
    rewriteRun(
        yaml(
            """
            camunda:
              bpm:
                admin-user:
                  id: demo
            """,
            spec -> spec.path("src/main/resources/some-other-config.yml")));
  }
}
