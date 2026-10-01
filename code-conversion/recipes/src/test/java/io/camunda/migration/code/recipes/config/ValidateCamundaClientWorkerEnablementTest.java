/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ValidateCamundaClientWorkerEnablementTest implements RewriteTest {

  private static final String WORKER =
      """
      import io.camunda.client.annotation.JobWorker;

      class PaymentWorker {
          @JobWorker(type = "process-payment")
          void handle() {}
      }
      """;
  private static final String FINDING =
      "This setting may disable a declared job worker. Resolve the effective configuration."
          + " Verify worker registration at runtime.";

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ValidateCamundaClientWorkerEnablement());
  }

  @Test
  void flagsInheritedLegacyYamlWorkerDisablement() {
    rewriteRun(
        java(WORKER, spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                zeebe:
                  defaults:
                    enabled: false
            """,
            """
            camunda:
              client:
                zeebe:
                  defaults:
                    ~~(%s)~~>enabled: false
            """
                .formatted(FINDING),
            spec -> spec.path("src/main/resources/application.yml")));
  }

  @Test
  void flagsLegacyAliasesAndEnvironmentOverrides() {
    rewriteRun(
        java(WORKER, spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.zeebe.defaults.enabled=false
            camunda.client.worker.defaults.enabled=${WORKERS_ENABLED:true}
            zeebe.client.enabled=false
            """,
            """
            ~~(%s)~~>camunda.client.zeebe.defaults.enabled=false
            ~~(%s)~~>camunda.client.worker.defaults.enabled=${WORKERS_ENABLED:true}
            ~~(%s)~~>zeebe.client.enabled=false
            """
                .formatted(FINDING, FINDING, FINDING),
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void flagsDisabledClientAndProfileScopedWorkerSettings() {
    rewriteRun(
        java(WORKER, spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.enabled=false
            """,
            """
            ~~(%s)~~>camunda.client.enabled=false
            """
                .formatted(FINDING),
            spec -> spec.path("src/main/resources/application.properties")),
        yaml(
            """
            spring:
              config:
                activate:
                  on-profile: prod
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
            """,
            """
            spring:
              config:
                activate:
                  on-profile: prod
            camunda:
              client:
                worker:
                  defaults:
                    ~~(%s)~~>enabled: false
            """
                .formatted(FINDING),
            spec -> spec.path("src/main/resources/application-prod.yml")));
  }

  @Test
  void flagsDisabledAndUnresolvedWorkerAnnotations() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(enabled = false)
                void disabled() {}

                @JobWorker(enabled = true)
                void enabled() {}

                @JobWorker(enabled = WorkerSettings.ENABLED)
                void unresolved() {}
            }

            final class WorkerSettings {
                static final boolean ENABLED = true;
            }
            """,
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                /*~~(This @JobWorker is disabled unless configuration overrides it. Verify registration at runtime.)~~>*/@JobWorker(enabled = false)
                void disabled() {}

                @JobWorker(enabled = true)
                void enabled() {}

                /*~~(This @JobWorker has an unresolved enabled value. Verify registration at runtime.)~~>*/@JobWorker(enabled = WorkerSettings.ENABLED)
                void unresolved() {}
            }

            final class WorkerSettings {
                static final boolean ENABLED = true;
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")));
  }

  @Test
  void ignoresUnrelatedModulesAndIntentionalWorkerlessConfiguration() {
    rewriteRun(
        java(WORKER, spec -> spec.path("payments/src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
            """,
            spec -> spec.path("invoices/src/main/resources/application.yml")),
        properties(
            """
            camunda.client.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void ignoresKnownEnabledValuesAndTestSources() {
    rewriteRun(
        java(WORKER, spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.enabled=true
            camunda.client.worker.defaults.enabled=on
            camunda.client.worker.override.process-payment.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")),
        properties(
            """
            camunda.client.worker.defaults.enabled=false
            """,
            spec -> spec.path("src/test/resources/application-test.properties")));
  }

  @Test
  void doesNotConfuseC7SubscriptionWithGlobalWorkerSettings() {
    rewriteRun(
        java(WORKER, spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              bpm:
                client:
                  subscriptions:
                    loan:
                      auto-open: false
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }
}
