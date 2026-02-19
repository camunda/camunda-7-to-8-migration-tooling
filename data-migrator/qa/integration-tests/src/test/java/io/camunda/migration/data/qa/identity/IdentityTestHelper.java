/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_AUTH;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_TENANT;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.request.TenantsSearchRequest;
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

  protected void createUserInC7(String username, String firstName, String lastName) {
    User user = identityService.newUser(username);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setPassword("pswd");
    identityService.saveUser(user);
  }

  protected void createGroupInC7(String groupId, String groupName) {
    Group group = identityService.newGroup(groupId);
    group.setName(groupName);
    identityService.saveGroup(group);
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

  protected void assertThatTenantsContain(List<org.camunda.bpm.engine.identity.Tenant> expectedTenants, List<io.camunda.client.api.search.response.Tenant> c8Tenants) {
    assertThat(c8Tenants)
        .extracting(io.camunda.client.api.search.response.Tenant::getTenantId, io.camunda.client.api.search.response.Tenant::getName)
        .containsAll(expectedTenants.stream().map(tenant -> tuple(tenant.getId(), tenant.getName())).toList());
  }

  protected void verifyAuthorizationSkippedViaLogs(String authorizationId, String reason, LogCapturer logs) {
    logs.assertContains(formatMessage(SKIPPED_AUTH, authorizationId, reason));
  }

  protected void verifyTenantSkippedViaLogs(String tenantId, LogCapturer logs) {
    logs.assertContains(formatMessage(SKIPPED_TENANT, tenantId));
  }

  protected void assertThatUsersForTenantContainExactly(String tenantId, String... usernames) {
    List<TenantUser> usersForTenant = camundaClient.newUsersByTenantSearchRequest(tenantId).execute().items();
    assertThat(usersForTenant).extracting(TenantUser::getUsername).containsExactlyInAnyOrder(usernames);
  }

  protected void assertThatGroupsForTenantContainExactly(String tenantId, String... groupIds) {
    List<TenantGroup> groupsForTenant = camundaClient.newGroupsByTenantSearchRequest(tenantId).execute().items();
    assertThat(groupsForTenant).extracting(TenantGroup::getGroupId).containsExactlyInAnyOrder(groupIds);
  }

}
