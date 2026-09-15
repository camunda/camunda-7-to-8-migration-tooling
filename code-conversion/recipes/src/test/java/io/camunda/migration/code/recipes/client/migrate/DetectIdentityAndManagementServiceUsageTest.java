/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.DetectIdentityAndManagementServiceUsageRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

class DetectIdentityAndManagementServiceUsageTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectIdentityAndManagementServiceUsageRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
        .typeValidationOptions(TypeValidation.none());
  }

  @Test
  void addsIdentityServiceDeclarationAndCallHints() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class IdentityUser {

                @Autowired
                private IdentityService identityService;

                public void manage(String userId, String groupId) {
                    identityService.createUserQuery().list();
                    identityService.createMembership(userId, groupId);
                    identityService.setAuthenticatedUserId(userId);
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class IdentityUser {

                // TODO: IdentityService has no direct Java client equivalent in Camunda 8.
                // For bulk migration of users/groups/authorizations, use the Identity Data Migrator.
                // For runtime identity management, use the Camunda Admin REST API or your identity provider's API.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                // See: https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/
                @Autowired
                private IdentityService identityService;

                public void manage(String userId, String groupId) {
                    // TODO: IdentityService has no direct Java client equivalent in Camunda 8 (createUserQuery()).
                    // Use the Camunda Admin REST API or your identity provider's API.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    identityService.createUserQuery().list();
                    // TODO: IdentityService has no direct Java client equivalent in Camunda 8 (createMembership()).
                    // Use the Camunda Admin REST API or your identity provider's API.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    identityService.createMembership(userId, groupId);
                    // TODO: IdentityService has no direct Java client equivalent in Camunda 8 (setAuthenticatedUserId()).
                    // Authentication is handled at the transport layer with JWT/OAuth; configure the identity provider instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    identityService.setAuthenticatedUserId(userId);
                }
            }
            """));
  }

  @Test
  void addsManagementServiceHintsAndJobRetryGuidance() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.ManagementService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ManagementUser {

                @Autowired
                private ManagementService managementService;

                public void manage(String jobId, int retries) {
                    managementService.createJobQuery().list();
                    managementService.getRegisteredDeployments();
                    managementService.setJobRetries(jobId, retries);
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.ManagementService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            public class ManagementUser {

                // TODO: ManagementService has no direct Java client equivalent in Camunda 8.
                // Use CamundaClient or the Orchestration Cluster REST API.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                @Autowired
                private ManagementService managementService;

                public void manage(String jobId, int retries) {
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Use POST /v2/jobs/search or CamundaClient job search requests.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.createJobQuery().list();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (getRegisteredDeployments()).
                    // Use the Orchestration Cluster REST API to search deployments.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.getRegisteredDeployments();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (setJobRetries()).
                    // Map the Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).retries(n).send().join().
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetries(jobId, retries);
                }
            }
            """));
  }

  @Test
  void annotatesServiceCallsFromParameters() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.camunda.bpm.engine.ManagementService;

            public class ServiceParameters {

                public void use(IdentityService identityService, ManagementService managementService) {
                    identityService.clearAuthentication();
                    managementService.getRegisteredDeployments();
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.camunda.bpm.engine.ManagementService;

            public class ServiceParameters {

                public void use(IdentityService identityService, ManagementService managementService) {
                    // TODO: IdentityService has no direct Java client equivalent in Camunda 8 (clearAuthentication()).
                    // Authentication is handled at the transport layer with JWT/OAuth; configure the identity provider instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    identityService.clearAuthentication();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (getRegisteredDeployments()).
                    // Use the Orchestration Cluster REST API to search deployments.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.getRegisteredDeployments();
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

            public class RuntimeUser {

                private RuntimeService runtimeService;

                public void start(String processKey) {
                    runtimeService.startProcessInstanceByKey(processKey);
                }
            }
            """));
  }

  @Test
  void annotatesEveryServiceCallInOneStatement() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.camunda.bpm.engine.ManagementService;

            public class CombinedServiceUse {

                private IdentityService identityService;
                private ManagementService managementService;

                private void use(Object first, Object second) {}

                public void manage() {
                    use(identityService.createUserQuery(), managementService.getRegisteredDeployments());
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.camunda.bpm.engine.ManagementService;

            public class CombinedServiceUse {

                // TODO: IdentityService has no direct Java client equivalent in Camunda 8.
                // For bulk migration of users/groups/authorizations, use the Identity Data Migrator.
                // For runtime identity management, use the Camunda Admin REST API or your identity provider's API.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                // See: https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/
                private IdentityService identityService;
                // TODO: ManagementService has no direct Java client equivalent in Camunda 8.
                // Use CamundaClient or the Orchestration Cluster REST API.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                private ManagementService managementService;

                private void use(Object first, Object second) {}

                public void manage() {
                    // TODO: IdentityService has no direct Java client equivalent in Camunda 8 (createUserQuery()).
                    // Use the Camunda Admin REST API or your identity provider's API.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (getRegisteredDeployments()).
                    // Use the Orchestration Cluster REST API to search deployments.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    use(identityService.createUserQuery(), managementService.getRegisteredDeployments());
                }
            }
            """));
  }
}
