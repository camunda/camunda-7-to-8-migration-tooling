/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.client.api.search.enums.OwnerType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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


/*
 * TODO cases to test:
 * [X] Happy path for all supported resource types (migrate) with wildcard *
 * [X] Happy path for all supported resource types (migrate) with resourceId
 * [X] Happy path for C7 Permission.ALL to all C8 permissions (migrate)
 * [X] Happy path for  TENANT_MEMBERSHIP and GROUP_MEMBERSHIP
 *
 * [ ] SKIP Global or Revoke auth
 * [ ] SKIP User or group non existent in C8
 * [ ] SKIP Resource type not supported
 * [ ] SKIP Resource type supported but at least one permission not supported
 * [ ] SKIP Invalid resourceId for resource type - example: AUTHORIZATION with resourceId != *
 * [ ] SKIP Invalid mapping for resourceId - example: APPLICATION with resourceId != cockpit, tasklist, admin
 */

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

  @BeforeEach
  public void setup() {
    createUserInC7(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
    createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
  }

  @AfterEach
  public void cleanup() {
    super.cleanup();
    identityService.deleteUser(USERNAME);
    authorizationService.createAuthorizationQuery().userIdIn(USERNAME).list().forEach(a -> authorizationService.deleteAuthorization(a.getId()));
    camundaClient.newDeleteUserCommand(USERNAME).execute();
  }

  @Test
  public void shouldMigrateAuthorizationsWithAllPermissions() {
    // given
    createAuthorizationInC7(USERNAME, null, Resources.APPLICATION, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.GROUP, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.SYSTEM, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.BATCH, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.USER, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then
    var request = camundaClient.newAuthorizationSearchRequest().filter(filter -> filter.ownerId(USERNAME));
    await().timeout(30, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 8); // incremented by 1 because C8 creates default auth when user is created

    var authorizations = request.execute().items();
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
    createAuthorizationInC7(USERNAME, null, Resources.APPLICATION, "*", Set.of(Permissions.ACCESS));
    createAuthorizationInC7(USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.READ, Permissions.UPDATE));
    createAuthorizationInC7(USERNAME, null, Resources.GROUP, "*", Set.of(Permissions.READ, Permissions.DELETE));
    createAuthorizationInC7(USERNAME, null, Resources.SYSTEM, "*", Set.of(SystemPermissions.READ));
    createAuthorizationInC7(USERNAME, null, Resources.BATCH, "*", Set.of(BatchPermissions.CREATE_BATCH_MIGRATE_PROCESS_INSTANCES));
    createAuthorizationInC7(USERNAME, null, Resources.TENANT, "*", Set.of(Permissions.READ, Permissions.CREATE));
    createAuthorizationInC7(USERNAME, null, Resources.USER, "*", Set.of(Permissions.READ));

    // when
    identityMigrator.migrate();

    // then
    var request = camundaClient.newAuthorizationSearchRequest().filter(filter -> filter.ownerId(USERNAME));
    await().timeout(30, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 8); // incremented by 1 because C8 creates default auth when user is created

    var authorizations = request.execute().items();
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
    createAuthorizationInC7(USERNAME, null, Resources.GROUP_MEMBERSHIP, "*", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.TENANT_MEMBERSHIP, "*", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then
    var request = camundaClient.newAuthorizationSearchRequest().filter(filter -> filter.ownerId(USERNAME));
    await().timeout(30, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 3); // incremented by 1 because C8 creates default auth when user is created

    var authorizations = request.execute().items();
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "*", USER, USERNAME, Set.of(PermissionType.UPDATE));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "*", USER, USERNAME, Set.of(PermissionType.UPDATE));
  }

  @Test
  public void shouldMigrateMembershipsWithSpecificResourceId() {
    createUserInC8("danwhite", "Dan", "White");
    createGroupInC8("group", "group");
    createTenantInC8("tenant", "tenant");

    // given
    createAuthorizationInC7(USERNAME, null, Resources.APPLICATION, "tasklist", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.GROUP, "group", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.TENANT, "tenant", Set.of(Permissions.ALL));
    createAuthorizationInC7(USERNAME, null, Resources.USER, "danwhite", Set.of(Permissions.ALL));

    // when
    identityMigrator.migrate();

    // then
    var request = camundaClient.newAuthorizationSearchRequest().filter(filter -> filter.ownerId(USERNAME));
    await().timeout(30, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 5); // incremented by 1 because C8 creates default auth when user is created

    var authorizations = request.execute().items();
    assertAuthorizationsContains(authorizations, ResourceType.COMPONENT, "tasklist", USER, USERNAME,  getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsContains(authorizations, ResourceType.GROUP, "group", USER, USERNAME,  getAllSupportedPerms(ResourceType.GROUP));
    assertAuthorizationsContains(authorizations, ResourceType.TENANT, "tenant", USER, USERNAME,  getAllSupportedPerms(ResourceType.TENANT));
    assertAuthorizationsContains(authorizations, ResourceType.USER, "danwhite", USER, USERNAME,  getAllSupportedPerms(ResourceType.USER));
  }

  protected static void assertAuthorizationsContains(List<io.camunda.client.api.search.response.Authorization> authorizations, ResourceType resourceType, String resourceId, OwnerType ownerType, String ownerId, Set<PermissionType> perms) {
    assertAuthorizationsContains(authorizations, resourceType, resourceId, ownerType, ownerId, perms.toArray(new PermissionType[0]));
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

  protected Authorization createAuthorizationInC7(String userId, String groupId, Resources resourceType, String resourceId, Set<Permission> permissions) {
    Authorization newAuthorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
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
}