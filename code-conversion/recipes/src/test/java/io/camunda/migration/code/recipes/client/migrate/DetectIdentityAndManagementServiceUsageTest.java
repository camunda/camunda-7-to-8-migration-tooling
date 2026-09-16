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
                    identityService.createNativeUserQuery().sql("select * from ACT_ID_USER").list();
                    identityService.saveUser(null);
                    identityService.createGroupQuery().list();
                    identityService.createMembership(userId, groupId);
                    identityService.setAuthenticatedUserId(userId);
                    identityService.setAuthentication(userId, java.util.List.of(groupId));
                    identityService.unlockUser(userId);
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

                // TODO: IdentityService usage requires method-specific migration guidance in Camunda 8.
                // Use CamundaClient identity APIs (for example, newUsersSearchRequest(), newCreateUserCommand(), newGroupsSearchRequest(), newAssignUserToGroupCommand(), and newAuthorizationSearchRequest()) or the Orchestration Cluster REST API where available.
                // For bulk migration of users, groups, authorizations, and tenants, use the Identity Data Migrator.
                // For authentication, use transport-level JWT/OAuth and configure the identity provider.
                // See: https://docs.camunda.io/docs/apis-tools/java-client/
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                // See: https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/
                @Autowired
                private IdentityService identityService;

                public void manage(String userId, String groupId) {
                    // TODO: IdentityService method has a direct Java client equivalent in Camunda 8 (createUserQuery()).
                    // Use CamundaClient.newUsersSearchRequest().
                    // See: https://docs.camunda.io/docs/apis-tools/java-client/
                    identityService.createUserQuery().list();
                    // TODO: IdentityService method requires manual migration in Camunda 8 (createNativeUserQuery()).
                    // Review the Camunda 8 identity APIs or identity provider for this operation.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    identityService.createNativeUserQuery().sql("select * from ACT_ID_USER").list();
                    // TODO: IdentityService method has a direct Java client equivalent in Camunda 8 (saveUser()).
                    // Use CamundaClient.newCreateUserCommand() for new users or newUpdateUserCommand(userId) for existing users.
                    // See: https://docs.camunda.io/docs/apis-tools/java-client/
                    identityService.saveUser(null);
                    // TODO: IdentityService method has a direct Java client equivalent in Camunda 8 (createGroupQuery()).
                    // Use CamundaClient.newGroupsSearchRequest().
                    // See: https://docs.camunda.io/docs/apis-tools/java-client/
                    identityService.createGroupQuery().list();
                    // TODO: IdentityService method has a direct Java client equivalent in Camunda 8 (createMembership()).
                    // Use CamundaClient.newAssignUserToGroupCommand().username(userId).groupId(groupId).
                    // See: https://docs.camunda.io/docs/apis-tools/java-client/
                    identityService.createMembership(userId, groupId);
                    // TODO: IdentityService method has no direct Java client equivalent in Camunda 8 (setAuthenticatedUserId()).
                    // Authentication and password operations are handled by the identity provider; use its API instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    identityService.setAuthenticatedUserId(userId);
                    // TODO: IdentityService method has no direct Java client equivalent in Camunda 8 (setAuthentication()).
                    // Authentication and password operations are handled by the identity provider; use its API instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    identityService.setAuthentication(userId, java.util.List.of(groupId));
                    // TODO: IdentityService method has no direct Java client equivalent in Camunda 8 (unlockUser()).
                    // Authentication and password operations are handled by the identity provider; use its API instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    identityService.unlockUser(userId);
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
                    managementService.createJobQuery().timers().singleResult();
                    managementService.createJobQuery().active().timers().singleResult();
                    managementService.executeJob(jobId);
                    managementService.getRegisteredDeployments();
                    managementService.setJobRetries(jobId, retries);
                    managementService.setJobRetries(java.util.List.of(jobId), retries);
                    managementService.setJobRetries(retries);
                    managementService.setJobRetriesAsync(java.util.List.of(jobId), retries);
                    org.camunda.bpm.engine.runtime.JobQuery query = managementService.createJobQuery();
                    managementService.setJobRetriesAsync(query, retries);
                    managementService.setJobRetriesAsync(java.util.List.of(jobId), query, retries);
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
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Use POST /v2/jobs/search or CamundaClient job search requests.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.createJobQuery().list();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Camunda 8 timers are wait states, not searchable jobs. In timer tests, use processTestContext.increaseTime(Duration).
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.createJobQuery().timers().singleResult();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Camunda 8 timers are wait states, not searchable jobs. In timer tests, use processTestContext.increaseTime(Duration).
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.createJobQuery().active().timers().singleResult();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (executeJob()).
                    // Camunda 8 has no operation to execute an arbitrary job by ID. In timer tests, use processTestContext.increaseTime(Duration); production work must run in a job worker that activates jobs by type.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.executeJob(jobId);
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (getRegisteredDeployments()).
                    // Camunda 8 uses job-type-based workers instead of deployment-aware registration. There is no direct equivalent; use deployment search only as an optional inventory.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.getRegisteredDeployments();
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetries()).
                    // Map the Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join().
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetries(jobId, retries);
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetries()).
                    // For synchronous bulk or query updates, preserve the selection, resolve each affected Camunda 7 job id to a Camunda 8 job key, and update each job with CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join(); account for partial success.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetries(java.util.List.of(jobId), retries);
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetries()).
                    // For synchronous bulk or query updates, preserve the selection, resolve each affected Camunda 7 job id to a Camunda 8 job key, and update each job with CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join(); account for partial success.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetries(retries);
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetriesAsync()).
                    // Use CamundaClient.newCreateBatchOperationCommand().updateJob().retries(n).filter(jobFilter).send().join() after mapping the Camunda 7 IDs to Camunda 8 job keys.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetriesAsync(java.util.List.of(jobId), retries);
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Use POST /v2/jobs/search or CamundaClient job search requests.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    org.camunda.bpm.engine.runtime.JobQuery query = managementService.createJobQuery();
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetriesAsync()).
                    // Use CamundaClient.newCreateBatchOperationCommand().updateJob().retries(n).filter(jobFilter).send().join() after translating the Camunda 7 query to a Camunda 8 JobFilter.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetriesAsync(query, retries);
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetriesAsync()).
                    // Resolve the Camunda 7 IDs and query separately, union and deduplicate their mapped Camunda 8 job keys, then use CamundaClient.newCreateBatchOperationCommand().updateJob().retries(n).filter(jobFilter).send().join(); a single conjunctive JobFilter cannot represent the C7 union.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.setJobRetriesAsync(java.util.List.of(jobId), query, retries);
                }
            }
            """));
  }

  @Test
  void annotatesSplitTimerQuery() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.ManagementService;
            import org.camunda.bpm.engine.runtime.JobQuery;

            public class SplitTimerQuery {

                public void manage(ManagementService managementService) {
                    JobQuery query = managementService.createJobQuery();
                    query.timers().singleResult();
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.ManagementService;
            import org.camunda.bpm.engine.runtime.JobQuery;

            public class SplitTimerQuery {

                public void manage(ManagementService managementService) {
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Use POST /v2/jobs/search or CamundaClient job search requests.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    JobQuery query = managementService.createJobQuery();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Camunda 8 timers are wait states, not searchable jobs. In timer tests, use processTestContext.increaseTime(Duration).
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    query.timers().singleResult();
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
                    // TODO: IdentityService method has no direct Java client equivalent in Camunda 8 (clearAuthentication()).
                    // Authentication and password operations are handled by the identity provider; use its API instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    identityService.clearAuthentication();
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (getRegisteredDeployments()).
                    // Camunda 8 uses job-type-based workers instead of deployment-aware registration. There is no direct equivalent; use deployment search only as an optional inventory.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    managementService.getRegisteredDeployments();
                }
            }
            """));
  }

  @Test
  void annotatesServiceMethodReferences() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;

            public class MethodReferenceUse {

                private Runnable logout(IdentityService identityService) {
                    Runnable action = identityService::clearAuthentication;
                    return action;
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;

            public class MethodReferenceUse {

                private Runnable logout(IdentityService identityService) {
                    // TODO: IdentityService method has no direct Java client equivalent in Camunda 8 (clearAuthentication()).
                    // Authentication and password operations are handled by the identity provider; use its API instead.
                    // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                    Runnable action = identityService::clearAuthentication;
                    return action;
                }
            }
            """));
  }

  @Test
  void annotatesManagementServiceRetryMethodReferences() {
    rewriteRun(
        // language=java
        java(
            """
            package org.example;

            import java.util.function.ObjIntConsumer;
            import org.camunda.bpm.engine.ManagementService;

            public class RetryMethodReferenceUse {

                private void assign(ManagementService managementService) {
                    ObjIntConsumer<String> retry = managementService::setJobRetries;
                }
            }
            """,
            """
            package org.example;

            import java.util.function.ObjIntConsumer;
            import org.camunda.bpm.engine.ManagementService;

            public class RetryMethodReferenceUse {

                private void assign(ManagementService managementService) {
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (setJobRetries()).
                    // Map the Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join().
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    ObjIntConsumer<String> retry = managementService::setJobRetries;
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
                    use(managementService.createJobQuery().list(), managementService.createJobQuery().timers().singleResult());
                }
            }
            """,
            """
            package org.example;

            import org.camunda.bpm.engine.IdentityService;
            import org.camunda.bpm.engine.ManagementService;

            public class CombinedServiceUse {

                // TODO: IdentityService usage requires method-specific migration guidance in Camunda 8.
                // Use CamundaClient identity APIs (for example, newUsersSearchRequest(), newCreateUserCommand(), newGroupsSearchRequest(), newAssignUserToGroupCommand(), and newAuthorizationSearchRequest()) or the Orchestration Cluster REST API where available.
                // For bulk migration of users, groups, authorizations, and tenants, use the Identity Data Migrator.
                // For authentication, use transport-level JWT/OAuth and configure the identity provider.
                // See: https://docs.camunda.io/docs/apis-tools/java-client/
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                // See: https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/
                // See: https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/
                private IdentityService identityService;
                // TODO: ManagementService has no direct Java client equivalent in Camunda 8.
                // Use CamundaClient or the Orchestration Cluster REST API.
                // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                private ManagementService managementService;

                private void use(Object first, Object second) {}

                public void manage() {
                    // TODO: IdentityService method has a direct Java client equivalent in Camunda 8 (createUserQuery()).
                    // Use CamundaClient.newUsersSearchRequest().
                    // See: https://docs.camunda.io/docs/apis-tools/java-client/
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (getRegisteredDeployments()).
                    // Camunda 8 uses job-type-based workers instead of deployment-aware registration. There is no direct equivalent; use deployment search only as an optional inventory.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    use(identityService.createUserQuery(), managementService.getRegisteredDeployments());
                    // TODO: ManagementService method has a direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Use POST /v2/jobs/search or CamundaClient job search requests.
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    // TODO: ManagementService has no direct Java client equivalent in Camunda 8 (createJobQuery()).
                    // Camunda 8 timers are wait states, not searchable jobs. In timer tests, use processTestContext.increaseTime(Duration).
                    // See: https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/
                    use(managementService.createJobQuery().list(), managementService.createJobQuery().timers().singleResult());
                }
            }
            """));
  }
}
