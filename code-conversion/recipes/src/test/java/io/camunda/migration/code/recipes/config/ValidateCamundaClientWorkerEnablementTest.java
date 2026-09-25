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

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ValidateCamundaClientWorkerEnablement());
  }

  @Test
  void flagsWorkersDisabledByLegacyDefaultsAliasAndFalseEnvironmentDefault() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                private static final String TYPE = "process-payment";

                @JobWorker(type = TYPE)
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.zeebe.defaults.enabled=${WORKER_ENABLED:false}
            """,
            """
            ~~(Job-worker readiness is conditional because 'camunda.client.worker.defaults.enabled' may disable the worker for job type 'process-payment'. Resolve profile and environment overrides, then verify its registration at runtime.)~~>camunda.client.zeebe.defaults.enabled=${WORKER_ENABLED:false}
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void flagsWorkersDisabledByLiteralLegacyDefaultsAlias() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.zeebe.defaults.enabled=false
            """,
            """
            ~~(The worker for job type 'process-payment' is disabled by 'camunda.client.worker.defaults.enabled=false'. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.zeebe.defaults.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void flagsWorkerEnablementThatAnEnvironmentValueCanDisable() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.worker.defaults.enabled=${WORKER_ENABLED:true}
            """,
            """
            ~~(Job-worker readiness is conditional because 'camunda.client.worker.defaults.enabled' may disable the worker for job type 'process-payment'. Resolve profile and environment overrides, then verify its registration at runtime.)~~>camunda.client.worker.defaults.enabled=${WORKER_ENABLED:true}
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void treatsPlaceholderJobTypesAsUnresolved() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "${PAYMENT_JOB_TYPE}")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.worker.defaults.enabled=false
            """,
            """
            ~~(The worker with an unresolved job type is disabled by 'camunda.client.worker.defaults.enabled=false'. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.worker.defaults.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void flagsWorkersDisabledByClientConfiguration() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.enabled=false
            """,
            """
            ~~(The worker for job type 'process-payment' is disabled because 'camunda.client.enabled=false' prevents client creation. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void flagsWorkersDisabledByPerWorkerOverride() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  override:
                    process-payment:
                      enabled: false
            """,
            """
            camunda:
              client:
                worker:
                  override:
                    process-payment:
                      ~~(The worker for job type 'process-payment' is disabled by its per-worker 'enabled=false' setting. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>enabled: false
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }

  @Test
  void perWorkerEnablementOverridesDisabledGlobalDefaults() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                private static final String TYPE = "process-payment";

                @JobWorker(type = TYPE)
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
                  override:
                    process-payment:
                      enabled: true
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }

  @Test
  void matchesPerWorkerOverridesByAnnotationWorkerName() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment", name = "payment-handler")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.worker.override.payment-handler.enabled=false
            """,
            """
            ~~(The worker for job type 'process-payment' is disabled by its per-worker 'enabled=false' setting. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.worker.override.payment-handler.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void matchesPerWorkerOverridesByDefaultMethodJobType() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker
                void processPayment() {}

                @JobWorker(type = "")
                void refundPayment() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.worker.override.processPayment.enabled=false
            camunda.client.worker.override.refundPayment.enabled=false
            camunda.client.worker.override.unrelated.enabled=false
            """,
            """
            ~~(The worker for job type 'processPayment' is disabled by its per-worker 'enabled=false' setting. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.worker.override.processPayment.enabled=false
            ~~(The worker for job type 'refundPayment' is disabled by its per-worker 'enabled=false' setting. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.worker.override.refundPayment.enabled=false
            camunda.client.worker.override.unrelated.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void usesConfiguredDefaultTypeForPerWorkerOverrides() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker
                void processPayment() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        properties(
            """
            camunda.client.worker.defaults.type=shared-worker-type
            camunda.client.worker.override.shared-worker-type.enabled=false
            camunda.client.worker.override.processPayment.enabled=false
            """,
            """
            camunda.client.worker.defaults.type=shared-worker-type
            ~~(The worker for job type 'shared-worker-type' is disabled by its per-worker 'enabled=false' setting. Verify the effective runtime configuration and job worker registration before marking workers ready.)~~>camunda.client.worker.override.shared-worker-type.enabled=false
            camunda.client.worker.override.processPayment.enabled=false
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void flagsProfileSpecificWorkerDisablementAsConditional() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
            """,
            """
            camunda:
              client:
                worker:
                  defaults:
                    ~~(Job-worker readiness is conditional because 'camunda.client.worker.defaults.enabled' may disable the worker for job type 'process-payment'. Resolve profile and environment overrides, then verify its registration at runtime.)~~>enabled: false
            """,
            spec -> spec.path("src/main/resources/application-prod.yml")));
  }

  @Test
  void flagsConflictingInheritedYamlWorkerSettingsAsConditional() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
            """,
            """
            camunda:
              client:
                worker:
                  defaults:
                    ~~(Job-worker readiness is conditional because 'camunda.client.worker.defaults.enabled' may disable the worker for job type 'process-payment'. Resolve profile and environment overrides, then verify its registration at runtime.)~~>enabled: false
            """,
            spec -> spec.path("src/main/resources/application.yml")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: true
            """,
            """
            camunda:
              client:
                worker:
                  defaults:
                    ~~(Job-worker readiness is conditional because 'camunda.client.worker.defaults.enabled' may disable the worker for job type 'process-payment'. Resolve profile and environment overrides, then verify its registration at runtime.)~~>enabled: true
            """,
            spec -> spec.path("src/main/resources/application-prod.yml")));
  }

  @Test
  void acceptsProfileSpecificWorkerEnablement() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: true
            """,
            spec -> spec.path("src/main/resources/application-prod.yml")));
  }

  @Test
  void doesNotCompareWorkersAndConfigurationAcrossModules() {
    rewriteRun(
        java(
            """
            import io.camunda.client.annotation.JobWorker;

            class PaymentWorker {
                @JobWorker(type = "process-payment")
                void handle() {}
            }
            """,
            spec -> spec.path("services/payments/src/main/java/PaymentWorker.java")),
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
            """,
            spec -> spec.path("services/invoicing/src/main/resources/application.yml")));
  }

  @Test
  void doesNotFlagDisabledWorkersWhenTheModuleHasNoWorkerDeclarations() {
    rewriteRun(
        yaml(
            """
            camunda:
              client:
                worker:
                  defaults:
                    enabled: false
            """,
            spec -> spec.path("src/main/resources/application.yml")));
  }
}
