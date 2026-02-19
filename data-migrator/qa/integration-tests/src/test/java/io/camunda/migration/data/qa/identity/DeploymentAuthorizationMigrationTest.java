/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.qa.identity.IdentityTestHelper.assertAuthorizationsSatisfy;
import static io.camunda.migration.data.qa.identity.IdentityTestHelper.getAllSupportedPerms;
import static java.lang.String.format;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.migration.data.IdentityMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Set;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.repository.Deployment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DeploymentAuthorizationMigrationTest extends IdentityMigrationAbstractTest {

  public static final String USERNAME = "tomsmith";
  public static final String USER_FIRST_NAME = "Tom";
  public static final String USER_LAST_NAME = "Smith";

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @BeforeEach
  public void setup() {
    testHelper.createUserInC7(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
    testHelper.createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
  }

  @AfterEach
  @Override
  public void cleanup() {
    super.cleanup();
    identityService.deleteUser(USERNAME);
    authorizationService.createAuthorizationQuery().userIdIn(USERNAME).list().forEach(a -> authorizationService.deleteAuthorization(a.getId()));
    camundaClient.newDeleteUserCommand(USERNAME).execute();
  }

  @Test
  public void shouldMigrateDeploymentAuthorizationToMultipleResourceAuthorizationsWithAllPerms() {
    // given
    Deployment deployment = deployer.createDeployment(
        "io/camunda/migration/data/bpmn/c7/userTaskProcess.bpmn",
        "io/camunda/migration/data/dmn/c7/simpleDmn.dmn",
        "io/camunda/migration/data/other/simpleForm.form"
    );
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, deployment.getId(), Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(6, USERNAME); // One for each resource contained in the deployment
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "userTaskProcessId", USER, USERNAME,  getAllSupportedPerms(ResourceType.RESOURCE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("userTaskProcessId"), USER, USERNAME,  getAllSupportedPerms(ResourceType.RESOURCE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDecisionId", USER, USERNAME,  getAllSupportedPerms(ResourceType.RESOURCE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDecisionId"), USER, USERNAME,  getAllSupportedPerms(ResourceType.RESOURCE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleFormId", USER, USERNAME,  getAllSupportedPerms(ResourceType.RESOURCE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleFormId"), USER, USERNAME,  getAllSupportedPerms(ResourceType.RESOURCE));
  }

  @Test
  public void shouldMigrateDeploymentAuthorizationWithCreatePermsOnly() {
    // given
    Deployment deployment = deployer.createDeployment(
        "io/camunda/migration/data/bpmn/c7/userTaskProcess.bpmn",
        "io/camunda/migration/data/dmn/c7/simpleDmn.dmn",
        "io/camunda/migration/data/other/simpleForm.form"
    );
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, deployment.getId(), Set.of(Permissions.CREATE));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(6, USERNAME); // Two for each resource contained in the deployment
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "userTaskProcessId", USER, USERNAME, Set.of(PermissionType.CREATE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("userTaskProcessId"), USER, USERNAME, Set.of(PermissionType.CREATE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDecisionId", USER, USERNAME,  Set.of(PermissionType.CREATE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDecisionId"), USER, USERNAME,  Set.of(PermissionType.CREATE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleFormId", USER, USERNAME,  Set.of(PermissionType.CREATE));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleFormId"), USER, USERNAME,  Set.of(PermissionType.CREATE));
  }

  @Test
  public void shouldMigrateDeploymentAuthorizationWithDeletePermsOnly() {
    // given
    Deployment deployment = deployer.createDeployment(
        "io/camunda/migration/data/bpmn/c7/userTaskProcess.bpmn",
        "io/camunda/migration/data/dmn/c7/simpleDmn.dmn",
        "io/camunda/migration/data/other/simpleForm.form"
    );
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, deployment.getId(), Set.of(Permissions.DELETE));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(6, USERNAME); // Two for each resource contained in the deployment
    var expectedPermissions = Set.of(PermissionType.DELETE_RESOURCE, PermissionType.DELETE_PROCESS, PermissionType.DELETE_DRD, PermissionType.DELETE_FORM);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "userTaskProcessId", USER, USERNAME, expectedPermissions);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("userTaskProcessId"), USER, USERNAME, expectedPermissions);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDecisionId", USER, USERNAME,  expectedPermissions);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDecisionId"), USER, USERNAME,  expectedPermissions);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleFormId", USER, USERNAME,  expectedPermissions);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleFormId"), USER, USERNAME,  expectedPermissions);
  }

  @Test
  public void shouldMigrateDeploymentAuthorizationWithReadPermsOnly() {
    // given
    Deployment deployment = deployer.createDeployment(
        "io/camunda/migration/data/bpmn/c7/userTaskProcess.bpmn",
        "io/camunda/migration/data/dmn/c7/simpleDmn.dmn",
        "io/camunda/migration/data/other/simpleForm.form"
    );
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, deployment.getId(), Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(6, USERNAME); // Two for each resource contained in the deployment
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "userTaskProcessId", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("userTaskProcessId"), USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDecisionId", USER, USERNAME,  Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDecisionId"), USER, USERNAME,  Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleFormId", USER, USERNAME,  Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleFormId"), USER, USERNAME,  Set.of(PermissionType.READ));
  }

  @Test
  public void shouldIgnoreCmmnResourcesWhenMigratingDeploymentAuthorization() {
    // given
    Deployment deployment = deployer.createDeployment(
        "io/camunda/migration/data/bpmn/c7/userTaskProcess.bpmn",
        "io/camunda/migration/data/dmn/c7/simpleDmn.dmn",
        "io/camunda/migration/data/other/simpleCaseDefinition.cmmn"
    );
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, deployment.getId(), Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(4, USERNAME); // Two for each resource contained in the deployment (excluding CMMN)
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "userTaskProcessId", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("userTaskProcessId"), USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDecisionId", USER, USERNAME,  Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDecisionId"), USER, USERNAME,  Set.of(PermissionType.READ));
  }

  @Test
  public void shouldMigrateDeploymentAuthorizationWithWildcard() {
    // given
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, "*", Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(1, USERNAME);
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "*", USER, USERNAME, Set.of(PermissionType.READ));
  }

  @Test
  public void shouldMigrateDeploymentAuthorizationWithDRD() {
    // given
    Deployment deployment = deployer.createDeployment("io/camunda/migration/data/dmn/c7/simpleDmnWithReqs.dmn");
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, deployment.getId(), Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(6, USERNAME); // Two for each resource contained in the deployment (2 DD and 1 DRD)
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDmnWithReqsId", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDmnWithReqsId"), USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDmnWithReqs2Id", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDmnWithReqs2Id"), USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, "simpleDmnWithReqs1Id", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.RESOURCE, prefixDefinitionId("simpleDmnWithReqs1Id"), USER, USERNAME, Set.of(PermissionType.READ));
  }

  @Test
  public void shouldSkipDeploymentAuthorizationWhenDeploymentHasNoResources() {
    // given
    Authorization authorization = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DEPLOYMENT, "unknownDeploymentId", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then
    testHelper.verifyAuthorizationSkippedViaLogs(authorization.getId(), format(FAILURE_UNSUPPORTED_RESOURCE_ID, "unknownDeploymentId", Resources.DEPLOYMENT.resourceName()), logs);
  }

}
