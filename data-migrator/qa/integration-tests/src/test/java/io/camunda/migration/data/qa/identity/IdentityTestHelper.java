/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_AUTH;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_GROUP;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_TENANT;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_USER;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.client.api.search.response.GroupUser;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentityTestHelper {

  @Autowired
  protected CamundaClient camundaClient;

  @Autowired
  protected IdentityService identityService;

  @Autowired
  protected AuthorizationService authorizationService;

  /*
   * C7 helper methods
   */

  protected Tenant createTenantInC7(String tenantId, String tenantName) {
    var tenant = identityService.newTenant(tenantId);
    tenant.setName(tenantName);
    identityService.saveTenant(tenant);
    return tenant;
  }

  protected User createUserInC7(String username, String firstName, String lastName, String email) {
    User user = identityService.newUser(username);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setPassword("pswd");
    user.setEmail(email);
    identityService.saveUser(user);
    return user;
  }

  protected User createUserInC7(String username, String firstName, String lastName) {
    return createUserInC7(username, firstName, lastName, "");
  }

  protected Group createGroupInC7(String groupId, String groupName) {
    Group group = identityService.newGroup(groupId);
    group.setName(groupName);
    identityService.saveGroup(group);
    return group;
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
        .map(permissionType -> PermissionType.valueOf(permissionType.name()))
        .toArray(PermissionType[]::new);
  }

  /*
   * C8 helper methods
   */

  protected void createTenantInC8(String tenantId, String tenantName) {
    camundaClient.newCreateTenantCommand()
        .tenantId(tenantId)
        .name(tenantName)
        .execute();

    // Because of eventual consistency, wait until tenant is visible
    await().timeout(5, TimeUnit.SECONDS).until(() -> camundaClient.newTenantsSearchRequest().filter(t -> t.tenantId(tenantId)).execute().items().size() == 1);
  }

  protected void createGroupInC8(String groupId, String groupName) {
    camundaClient.newCreateGroupCommand()
        .groupId(groupId)
        .name(groupName)
        .execute();

    // Because of eventual consistency, wait until group is visible
    await().timeout(5, TimeUnit.SECONDS).until(() -> camundaClient.newGroupsSearchRequest().filter(g -> g.groupId(groupId)).execute().items().size() == 1);
  }

  protected void createUserInC8(String username, String firstName, String lastName) {
    camundaClient.newCreateUserCommand()
        .username(username)
        .name(firstName + " " + lastName)
        .password("pswd")
        .execute();

    // Because of eventual consistency, wait until user is visible
    await().timeout(5, TimeUnit.SECONDS).until(() -> camundaClient.newUsersSearchRequest().filter(f -> f.username(username)).execute().items().size() == 1);
  }

  /*
   * Assertion helper methods
   */

  protected List<io.camunda.client.api.search.response.Authorization> awaitAuthorizationsCountAndGet(int expectedSize, String username) {
    var request = camundaClient.newAuthorizationSearchRequest().filter(filter -> filter.ownerId(username));
    await().timeout(30, TimeUnit.SECONDS).until(() -> request.execute().items().size() == expectedSize + 1); // +1 because C8 creates default auth when user is created
    return request.execute().items();
  }

  protected List<io.camunda.client.api.search.response.User> awaitUserCountAndGet(int expectedSize) {
    var request = camundaClient.newUsersSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == expectedSize);
    return request.execute().items();
  }

  protected List<io.camunda.client.api.search.response.Group> awaitGroupsCountAndGet(int expectedSize) {
    var request = camundaClient.newGroupsSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == expectedSize);
    return request.execute().items();
  }

  protected List<io.camunda.client.api.search.response.Tenant> awaitTenantsCountAndGet(int expectedSize) {
    TenantsSearchRequest request = camundaClient.newTenantsSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == expectedSize + 1) ; // +1 for default tenant
    return request.execute().items();
  }

  protected static void assertAuthorizationsSatisfy(List<io.camunda.client.api.search.response.Authorization> authorizations, ResourceType resourceType, String resourceId, OwnerType ownerType, String ownerId, Set<PermissionType> perms) {
    assertAuthorizationsSatisfy(authorizations, resourceType, resourceId, ownerType, ownerId, perms.toArray(new PermissionType[0]));
  }

  protected static void assertAuthorizationsSatisfy(List<io.camunda.client.api.search.response.Authorization> authorizations, ResourceType resourceType, String resourceId, OwnerType ownerType, String ownerId, PermissionType[] perms) {
    assertThat(authorizations).anySatisfy(auth -> {
      assertThat(auth.getResourceType()).isEqualTo(resourceType);
      assertThat(auth.getResourceId()).isEqualTo(resourceId);
      assertThat(auth.getOwnerType()).isEqualTo(ownerType);
      assertThat(auth.getOwnerId()).isEqualTo(ownerId);
      assertThat(auth.getPermissionTypes()).containsExactlyInAnyOrder(perms);
    });
  }

  protected void assertThatUsersContain(List<org.camunda.bpm.engine.identity.User> expectedUsers, List<io.camunda.client.api.search.response.User> c8Users) {
    assertThat(c8Users)
        .extracting(io.camunda.client.api.search.response.User::getUsername,
            io.camunda.client.api.search.response.User::getName,
            io.camunda.client.api.search.response.User::getEmail)
        .containsAll(expectedUsers.stream().map(user -> tuple(user.getId(), user.getFirstName() + " " + user.getLastName(), user.getEmail())).toList());
  }

  protected void assertThatGroupsContain(List<org.camunda.bpm.engine.identity.Group> expectedGroups, List<io.camunda.client.api.search.response.Group> c8Groups) {
    assertThat(c8Groups)
        .extracting(io.camunda.client.api.search.response.Group::getGroupId, io.camunda.client.api.search.response.Group::getName)
        .containsAll(expectedGroups.stream().map(group -> tuple(group.getId(), group.getName())).toList());
  }

  protected void assertThatTenantsContain(List<org.camunda.bpm.engine.identity.Tenant> expectedTenants, List<io.camunda.client.api.search.response.Tenant> c8Tenants) {
    assertThat(c8Tenants)
        .extracting(io.camunda.client.api.search.response.Tenant::getTenantId, io.camunda.client.api.search.response.Tenant::getName)
        .containsAll(expectedTenants.stream().map(tenant -> tuple(tenant.getId(), tenant.getName())).toList());
  }

  protected void verifyAuthorizationSkippedViaLogs(String authorizationId, String ownerType, String ownerId, String resourceTypeName,
                                                   String resourceTypeId, String reason, LogCapturer logs) {
    logs.assertContains(formatMessage(SKIPPED_AUTH, authorizationId, ownerType, ownerId, resourceTypeName, resourceTypeId, reason));
  }

  protected void verifyUserSkippedViaLogs(String username, String reason, LogCapturer logs) {
    logs.assertContains(formatMessage(SKIPPED_USER, username, reason));
  }

  protected void verifyGroupSkippedViaLogs(String groupId, String groupName, String reason, LogCapturer logs) {
    logs.assertContains(formatMessage(SKIPPED_GROUP, groupId, groupName == null ? "null" : groupName, reason));
  }

  protected void verifyTenantSkippedViaLogs(String tenantId, String tenantName, String reason, LogCapturer logs) {
    logs.assertContains(formatMessage(SKIPPED_TENANT, tenantId, tenantName == null ? "null" : tenantName, reason));
  }

  protected void assertNoMembershipsForTenant(String tenantId) {
    List<TenantUser> usersForTenant = camundaClient.newUsersByTenantSearchRequest(tenantId).execute().items();
    assertThat(usersForTenant).isEmpty();
  }

  protected void assertNoMembershipsForGroup(String groupId) {
    List<GroupUser> usersForGroup = camundaClient.newUsersByGroupSearchRequest(groupId).execute().items();
    assertThat(usersForGroup).isEmpty();
  }

  protected void assertThatUsersForTenantContainExactly(String tenantId, String... usernames) {
    await().timeout(5, TimeUnit.SECONDS).untilAsserted(() -> {
      List<TenantUser> usersForTenant = camundaClient.newUsersByTenantSearchRequest(tenantId).execute().items();
      assertThat(usersForTenant).extracting(TenantUser::getUsername).containsExactlyInAnyOrder(usernames);
    });
  }

  protected void assertThatUsersForGroupContainExactly(String groupId, String... usernames) {
    await().timeout(5, TimeUnit.SECONDS).untilAsserted(() -> {
      List<GroupUser> usersForGroup = camundaClient.newUsersByGroupSearchRequest(groupId).execute().items();
      assertThat(usersForGroup).extracting(GroupUser::getUsername).containsExactlyInAnyOrder(usernames);
    });
  }

  protected void assertThatGroupsForTenantContainExactly(String tenantId, String... groupIds) {
    await().timeout(5, TimeUnit.SECONDS).untilAsserted(() -> {
      List<TenantGroup> groupsForTenant = camundaClient.newGroupsByTenantSearchRequest(tenantId).execute().items();
      assertThat(groupsForTenant).extracting(TenantGroup::getGroupId).containsExactlyInAnyOrder(groupIds);
    });
  }

}
