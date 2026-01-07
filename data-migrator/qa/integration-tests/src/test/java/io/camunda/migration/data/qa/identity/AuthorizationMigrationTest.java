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
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_AUTH;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GLOBAL;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_REVOKE;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.migration.data.IdentityMigrator;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.BatchPermissions;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class AuthorizationMigrationTest extends IdentityAbstractTest {

  public static final String USERNAME = "tomsmith";
  public static final String USER_FIRST_NAME = "Tom";
  public static final String USER_LAST_NAME = "Smith";
  public static final String USER_PASSWORD = "password";

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Autowired
  protected AuthorizationService authorizationService;

  @Autowired
  protected IdentityService identityService;

  @Autowired
  protected IdentityTestHelper identityTestHelper;

  @BeforeEach
  public void setup() {
    identityTestHelper.createUserInC7(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
    identityTestHelper.createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
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
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.SYSTEM, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.USER, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then
    var authorizations = awaitAuthorizationsCountAndGet(7);
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsContains(authorizations, ResourceType.AUTHORIZATION, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.AUTHORIZATION));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.GROUP));
    assertAuthorizationsContains(authorizations, ResourceType.SYSTEM, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.SYSTEM));
    assertAuthorizationsContains(authorizations, ResourceType.BATCH, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.BATCH));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.TENANT));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "*", USER, USERNAME, getAllSupportedPerms(ResourceType.USER));
  }

  @Test
  public void shouldMigrateAuthorizationsWithSpecificPermissions() {
    // given
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "*", Set.of(Permissions.ACCESS));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.READ, Permissions.UPDATE));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "*", Set.of(Permissions.READ, Permissions.DELETE));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.SYSTEM, "*", Set.of(SystemPermissions.READ));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "*", Set.of(BatchPermissions.CREATE_BATCH_MIGRATE_PROCESS_INSTANCES));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.READ, Permissions.CREATE));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.USER, "*", Set.of(Permissions.READ));

    // when
    identityMigrator.migrate();

    // then
    var authorizations = awaitAuthorizationsCountAndGet(7);
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "*", USER, USERNAME, Set.of(PermissionType.ACCESS));
    assertAuthorizationsContains(authorizations, ResourceType.AUTHORIZATION, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.UPDATE));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.DELETE));
    assertAuthorizationsContains(authorizations, ResourceType.SYSTEM, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.READ_USAGE_METRIC));
    assertAuthorizationsContains(authorizations, ResourceType.BATCH, "*", USER, USERNAME, Set.of(PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, Set.of(PermissionType.READ, PermissionType.CREATE));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "*", USER, USERNAME, Set.of(PermissionType.READ));
  }

  @Test
  public void shouldMigrateMembershipAuthorizationsForAllPerms() {
    // given
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP_MEMBERSHIP, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT_MEMBERSHIP, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then
    var authorizations = awaitAuthorizationsCountAndGet(2);
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, Set.of(PermissionType.UPDATE));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, Set.of(PermissionType.UPDATE));
  }

  @Test
  public void shouldSkipMembershipAuthorizationForSpecificPerms() {
    // given
    Authorization auth1 = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP_MEMBERSHIP, "*", Set.of(Permissions.CREATE));
    Authorization auth2 = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT_MEMBERSHIP, "*", Set.of(Permissions.DELETE));

    // when
    identityMigrator.migrate();

    // then
    verifySkippedViaLogs(auth1.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, Permissions.CREATE, Resources.GROUP_MEMBERSHIP.resourceName()));
    verifySkippedViaLogs(auth2.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, Permissions.DELETE, Resources.TENANT_MEMBERSHIP.resourceName()));
  }

  @Test
  public void shouldMigrateAuthorizationsWithSpecificResourceId() {
    identityTestHelper.createUserInC8("danwhite", "Dan", "White");
    identityTestHelper.createGroupInC8("group", "group");
    identityTestHelper.createTenantInC8("tenant", "tenant");

    // given
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "tasklist", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "group", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.TENANT, "tenant", Set.of(Permissions.ALL));
    createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.USER, "danwhite", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then
    var authorizations = awaitAuthorizationsCountAndGet(4);
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "tasklist", USER, USERNAME,  getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "group", USER, USERNAME,  getAllSupportedPerms(ResourceType.GROUP));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "tenant", USER, USERNAME,  getAllSupportedPerms(ResourceType.TENANT));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "danwhite", USER, USERNAME,  getAllSupportedPerms(ResourceType.USER));
  }

  @Test
  public void shouldSkipRevokeAndGlobalAuthorizations() {
    // given
    Authorization auth1 = createAuthorizationInC7(AUTH_TYPE_GLOBAL, "*", null, Resources.USER, "*", Set.of(Permissions.ALL));
    Authorization auth2 = createAuthorizationInC7(AUTH_TYPE_REVOKE, USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then auths are skipped
    verifySkippedViaLogs(auth1.getId(), FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED);
    verifySkippedViaLogs(auth2.getId(), FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED);
  }

  @Test
  public void shouldSkipWhenOwnerDoesNotExist() {
    // given
    Authorization auth1 = createAuthorizationInC7(AUTH_TYPE_GRANT, "unknownuser", null, Resources.APPLICATION, "*", Set.of(Permissions.ALL));
    Authorization auth2 = createAuthorizationInC7(AUTH_TYPE_GRANT, null, "unknowngroup", Resources.APPLICATION, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then auths are skipped
    verifySkippedViaLogs(auth1.getId(), FAILURE_OWNER_NOT_EXISTS);
    verifySkippedViaLogs(auth2.getId(), FAILURE_OWNER_NOT_EXISTS);
  }

  @Test
  public void shouldSkipWhenResourceIdDoesNotExist() {
    // given
    Authorization auth = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.GROUP, "unknownGroup", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then auths are skipped
    verifySkippedViaLogs(auth.getId(), FAILED_TO_MIGRATE_AUTHORIZATION);
  }

  @Test
  public void shouldSkipOnUnsupportedResourceType() {
    // given
    Authorization auth = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.FILTER, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then auths are skipped
    verifySkippedViaLogs(auth.getId(), format(FAILURE_UNSUPPORTED_RESOURCE_TYPE, Resources.FILTER.resourceName()));
  }

  @Test
  public void shouldSkipIfAtLeastOneUnsupportedPermission() {
    // given
    Authorization auth = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "*", Set.of(BatchPermissions.READ, BatchPermissions.READ_HISTORY));

    // when
    identityMigrator.migrate();

    // then auths are skipped
    verifySkippedViaLogs(auth.getId(), format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, BatchPermissions.READ_HISTORY, Resources.BATCH.resourceName()));
  }

  @Test
  public void shouldSkipIfUnsupportedResourceIdForGivenResourceType() {
    // given
    Authorization auth1 = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "authId", Set.of(Permissions.ALL)); // AUTHORIZATION only accepts '*'
    Authorization auth2 = createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "unknownApp", Set.of(Permissions.ALL)); // APPLICATION only accepts '*' or known app

    // when
    identityMigrator.migrate();

    // then auths are skipped
    verifySkippedViaLogs(auth1.getId(), format(FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID, "authId", Resources.AUTHORIZATION.resourceName()));
    verifySkippedViaLogs(auth2.getId(), format(FAILURE_UNSUPPORTED_RESOURCE_ID, "unknownApp", Resources.APPLICATION.resourceName()));
  }

  protected static void assertAuthorizationsContains(List<io.camunda.client.api.search.response.Authorization> authorizations, ResourceType resourceType, String resourceId, OwnerType ownerType, String ownerId, Set<PermissionType> perms) {
    assertAuthorizationsContains(authorizations, resourceType, resourceId, ownerType, ownerId, perms.toArray(new PermissionType[0]));
  }

  protected List<io.camunda.client.api.search.response.Authorization> awaitAuthorizationsCountAndGet(int size) {
    var request = camundaClient.newAuthorizationSearchRequest().filter(filter -> filter.ownerId(USERNAME));
    await().timeout(30, TimeUnit.SECONDS).until(() -> request.execute().items().size() == size + 1); // incremented by 1 because C8 creates default auth when user is created
    return request.execute().items();
  }

  protected static void assertAuthorizationsContains(List<io.camunda.client.api.search.response.Authorization> authorizations, ResourceType resourceType, String resourceId, OwnerType ownerType, String ownerId, PermissionType[] perms) {
    assertThat(authorizations).anySatisfy(auth -> {
      assertThat(auth.getResourceType()).isEqualTo(resourceType);
      assertThat(auth.getResourceId()).isEqualTo(resourceId);
      assertThat(auth.getOwnerType()).isEqualTo(ownerType);
      assertThat(auth.getOwnerId()).isEqualTo(ownerId);
      assertThat(auth.getPermissionTypes()).containsExactlyInAnyOrder(perms);
    });
  }

  protected Authorization createAuthorizationInC7(int type, String userId, String groupId, Resources resourceType, String resourceId, Set<Permission> permissions) {
    Authorization newAuthorization = authorizationService.createNewAuthorization(type);
    if (StringUtils.isNotBlank(userId)) {
      newAuthorization.setUserId(userId);
    } else if (StringUtils.isNotBlank(groupId)) {
      newAuthorization.setGroupId(groupId);
    }
    newAuthorization.setResourceType(resourceType.resourceType());
    newAuthorization.setResourceId(resourceId);
    newAuthorization.setPermissions(permissions.toArray(new Permission[0]));
    authorizationService.saveAuthorization(newAuthorization);
    return newAuthorization;
  }

  protected static PermissionType[] getAllSupportedPerms(ResourceType resourceType) {
    return AuthorizationResourceType
        .valueOf(resourceType.name())
        .getSupportedPermissionTypes()
        .stream()
        .map(permissionType -> PermissionType.valueOf(permissionType.name())) // Needed to convert from io.camunda.zeebe.protocol.record.value.PermissionType to io.camunda.client.api.search.enums.PermissionType
        .toArray(PermissionType[]::new);
  }

  protected void verifySkippedViaLogs(String authorizationId, String reason) {
    logs.assertContains(formatMessage(SKIPPED_AUTH, authorizationId, reason));
  }
}