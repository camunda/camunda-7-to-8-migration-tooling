/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_MIGRATE_AUTHORIZATION;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_OWNER_NOT_EXISTS;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_PERMISSION_TYPE;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_ID;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_TYPE;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.qa.identity.IdentityTestHelper.assertAuthorizationsContains;
import static io.camunda.migration.data.qa.identity.IdentityTestHelper.getAllSupportedPerms;
import static java.lang.String.format;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GLOBAL;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_REVOKE;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.migration.data.IdentityMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Set;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.BatchPermissions;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AuthorizationMigrationTest extends IdentityAbstractTest {

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
  public void shouldMigrateAuthorizationsWithAllPermissions() {
    // given
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.SYSTEM, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.USER, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_DEFINITION, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_REQUIREMENTS_DEFINITION, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.PROCESS_DEFINITION, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(10, USERNAME);
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsContains(authorizations, ResourceType.AUTHORIZATION, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.AUTHORIZATION));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.GROUP));
    assertAuthorizationsContains(authorizations, ResourceType.SYSTEM, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.SYSTEM));
    assertAuthorizationsContains(authorizations, ResourceType.BATCH, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.BATCH));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.TENANT));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.USER));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_DEFINITION, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.DECISION_DEFINITION));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_REQUIREMENTS_DEFINITION, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.DECISION_REQUIREMENTS_DEFINITION));
    assertAuthorizationsContains(authorizations, ResourceType.PROCESS_DEFINITION, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.PROCESS_DEFINITION));
  }

  @Test
  public void shouldMigrateAuthorizationsWithSpecificPermissions() {
    // given
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "*", Set.of(Permissions.ACCESS));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.READ, Permissions.UPDATE));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "*", Set.of(Permissions.READ, Permissions.DELETE));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.SYSTEM, "*", Set.of(SystemPermissions.READ));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "*", Set.of(BatchPermissions.CREATE_BATCH_MIGRATE_PROCESS_INSTANCES));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.READ, Permissions.CREATE));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.USER, "*", Set.of(Permissions.READ));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_DEFINITION, "*", Set.of(Permissions.READ));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_REQUIREMENTS_DEFINITION, "*", Set.of(Permissions.READ));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.PROCESS_DEFINITION, "*", Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(10, USERNAME);
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.COMPONENT)); // Only ACCESS exists
    assertAuthorizationsContains(authorizations, ResourceType.AUTHORIZATION, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.UPDATE));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.DELETE));
    assertAuthorizationsContains(authorizations, ResourceType.SYSTEM, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.READ_USAGE_METRIC));
    assertAuthorizationsContains(authorizations, ResourceType.BATCH, "*", USER, USERNAME, Set.of(PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.CREATE));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "*", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_DEFINITION, "*", USER, USERNAME, Set.of(PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_REQUIREMENTS_DEFINITION, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.DECISION_REQUIREMENTS_DEFINITION)); // Only READ exists
    assertAuthorizationsContains(authorizations, ResourceType.PROCESS_DEFINITION, "*", USER, USERNAME, Set.of(PermissionType.READ_PROCESS_DEFINITION));
  }

  @Test
  public void shouldMigrateMembershipAuthorizationsForAllPerms() {
    // given
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP_MEMBERSHIP, "*", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT_MEMBERSHIP, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(2, USERNAME);
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, Set.of(PermissionType.UPDATE));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, Set.of(PermissionType.UPDATE));
  }

  @Test
  public void shouldSkipMembershipAuthorizationForSpecificPerms() {
    // given
    Authorization auth1 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP_MEMBERSHIP, "*", Set.of(Permissions.CREATE));
    Authorization auth2 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT_MEMBERSHIP, "*", Set.of(Permissions.DELETE));

    // when
    identityMigrator.start();

    // then
    testHelper.verifySkippedViaLogs(auth1.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, Permissions.CREATE, Resources.GROUP_MEMBERSHIP.resourceName()), logs);
    testHelper.verifySkippedViaLogs(auth2.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, Permissions.DELETE, Resources.TENANT_MEMBERSHIP.resourceName()), logs);
  }

  @Test
  public void shouldMigrateCommonAuthorizationsWithSpecificResourceId() {
    testHelper.createUserInC8("danwhite", "Dan", "White");
    testHelper.createGroupInC8("group", "group");
    testHelper.createTenantInC8("tenant", "tenant");

    // given
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "tasklist", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "group", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT, "tenant", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.USER, "danwhite", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_REQUIREMENTS_DEFINITION, "decReqDefKey", Set.of(Permissions.ALL));


    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(5, USERNAME);
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "tasklist", USER, USERNAME,  getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "group", USER, USERNAME,  getAllSupportedPerms(ResourceType.GROUP));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "tenant", USER, USERNAME,  getAllSupportedPerms(ResourceType.TENANT));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "danwhite", USER, USERNAME,  getAllSupportedPerms(ResourceType.USER));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_REQUIREMENTS_DEFINITION, "decReqDefKey", USER, USERNAME,  getAllSupportedPerms(ResourceType.DECISION_REQUIREMENTS_DEFINITION));
  }

  @Test
  public void shouldSkipRevokeAndGlobalAuthorizations() {
    // given
    Authorization auth1 = testHelper.createAuthorizationInC7(AUTH_TYPE_GLOBAL, "*", null, Resources.USER, "*", Set.of(Permissions.ALL));
    Authorization auth2 = testHelper.createAuthorizationInC7(AUTH_TYPE_REVOKE, USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then auths are skipped
    testHelper.verifySkippedViaLogs(auth1.getId(), FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED, logs);
    testHelper.verifySkippedViaLogs(auth2.getId(), FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED, logs);
  }

  @Test
  public void shouldSkipWhenOwnerDoesNotExist() {
    // given
    Authorization auth1 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, "unknownuser", null, Resources.APPLICATION, "*", Set.of(Permissions.ALL));
    Authorization auth2 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, null, "unknowngroup", Resources.APPLICATION, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then auths are skipped
    testHelper.verifySkippedViaLogs(auth1.getId(), FAILURE_OWNER_NOT_EXISTS, logs);
    testHelper.verifySkippedViaLogs(auth2.getId(), FAILURE_OWNER_NOT_EXISTS, logs);
  }

  @Test
  public void shouldSkipWhenResourceIdDoesNotExist() {
    // given
    Authorization auth = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "unknownGroup", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then auths are skipped
    testHelper.verifySkippedViaLogs(auth.getId(), FAILED_TO_MIGRATE_AUTHORIZATION, logs);
  }

  @Test
  public void shouldSkipOnUnsupportedResourceType() {
    // given
    Authorization auth = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.FILTER, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then auths are skipped
    testHelper.verifySkippedViaLogs(auth.getId(), format(FAILURE_UNSUPPORTED_RESOURCE_TYPE, Resources.FILTER.resourceName()), logs);
  }

  @Test
  public void shouldSkipIfAtLeastOneUnsupportedPermission() {
    // given
    Authorization auth1 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "*", Set.of(BatchPermissions.READ, BatchPermissions.READ_HISTORY));
    Authorization auth2 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_DEFINITION, "*", Set.of(Permissions.READ, Permissions.DELETE_HISTORY));
    Authorization auth3 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.PROCESS_DEFINITION, "*", Set.of(ProcessDefinitionPermissions.READ, ProcessDefinitionPermissions.UPDATE));

    // when
    identityMigrator.start();

    // then auths are skipped
    testHelper.verifySkippedViaLogs(auth1.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, BatchPermissions.READ_HISTORY, Resources.BATCH.resourceName()), logs);
    testHelper.verifySkippedViaLogs(auth2.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, Permissions.DELETE_HISTORY, Resources.DECISION_DEFINITION.resourceName()), logs);
    testHelper.verifySkippedViaLogs(auth3.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, ProcessDefinitionPermissions.UPDATE, Resources.PROCESS_DEFINITION.resourceName()), logs);
  }

  @Test
  public void shouldSkipIfUnsupportedResourceIdForGivenResourceType() {
    // given
    Authorization auth1 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "authId", Set.of(Permissions.ALL)); // AUTHORIZATION only accepts '*'
    Authorization auth2 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "unknownApp", Set.of(Permissions.ALL)); // APPLICATION only accepts '*' or known app

    // when
    identityMigrator.start();

    // then auths are skipped
    testHelper.verifySkippedViaLogs(auth1.getId(), format(FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID, "authId", Resources.AUTHORIZATION.resourceName()), logs);
    testHelper.verifySkippedViaLogs(auth2.getId(), format(FAILURE_UNSUPPORTED_RESOURCE_ID, "unknownApp", Resources.APPLICATION.resourceName()), logs);
  }

  @Test
  public void shouldMigrateTwoAuthorizationsForEachDefinitionAuthorization() {
    // given
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.DECISION_DEFINITION, "decDefKey", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.PROCESS_DEFINITION, "procDefKey", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(4, USERNAME);
    assertAuthorizationsContains(authorizations, ResourceType.PROCESS_DEFINITION, "procDefKey", USER, USERNAME,  getAllSupportedPerms(ResourceType.PROCESS_DEFINITION));
    assertAuthorizationsContains(authorizations, ResourceType.PROCESS_DEFINITION, prefixDefinitionId("procDefKey"), USER, USERNAME,  getAllSupportedPerms(ResourceType.PROCESS_DEFINITION));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_DEFINITION, "decDefKey", USER, USERNAME,  getAllSupportedPerms(ResourceType.DECISION_DEFINITION));
    assertAuthorizationsContains(authorizations, ResourceType.DECISION_DEFINITION, prefixDefinitionId("decDefKey"), USER, USERNAME,  getAllSupportedPerms(ResourceType.DECISION_DEFINITION));
  }
  
}