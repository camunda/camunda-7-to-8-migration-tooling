/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.identity;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.search.response.AuthorizationImpl;
import io.camunda.migrator.IdentityMigrator;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import java.util.Comparator;
import java.util.List;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IdentityTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private IdentityMigrator identityMigrator;

  @Autowired
  private IdentityService identityService;

  @Autowired
  private AuthorizationService authorizationService;

  @Autowired
  private CamundaClient camundaClient;

  @Test
  public void shouldMigrateAuthorizations() throws InterruptedException {
    // Create identities in C7
    identityService.saveUser(identityService.newUser("tom"));
    identityService.saveGroup(identityService.newGroup("group"));

    // Create identities in C8
    camundaClient.newCreateUserCommand().username("tom").password("psw").execute();
    camundaClient.newCreateGroupCommand().groupId("group").name("group").execute();

    Thread.sleep(1000); // For some reason the calls above seem asynchronous, but shouldn't be

    // Create authorizations in C7

    createGrantAuthorization(OwnerType.USER, "tom", Resources.TENANT, "tenantId", Permissions.ALL);
    createGrantAuthorization(OwnerType.GROUP, "group", Resources.TENANT_MEMBERSHIP, Authorization.ANY, Permissions.CREATE);

    // when running migration
    identityMigrator.migrate();

    Thread.sleep(1000);

    // then authorizations are migrated
    var migratedAuths = camundaClient.newAuthorizationSearchRequest()
        .filter(authorizationFilter -> authorizationFilter.resourceType(ResourceType.TENANT))
        .execute()
        .items();

    assertThat(migratedAuths).usingElementComparator(getAuthComparator()).contains(new AuthorizationImpl(null, "tom", "tenantId", OwnerType.USER, ResourceType.TENANT, List.of(CREATE, READ, DELETE, UPDATE)));
    assertThat(migratedAuths).usingElementComparator(getAuthComparator()).contains(new AuthorizationImpl(null, "group", "*", OwnerType.GROUP, ResourceType.TENANT, List.of(UPDATE)));
  }

  private void createGrantAuthorization(OwnerType ownerType, String ownerId, Resources resourceType, String resourceId, Permissions permissions) {
    var authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    if (OwnerType.USER.equals(ownerType)) {
      authorization.setUserId(ownerId);
    } else {
      authorization.setGroupId(ownerId);
    }
    authorization.setResource(resourceType);
    authorization.setResourceId(resourceId);
    authorization.addPermission(permissions);
    authorizationService.saveAuthorization(authorization);
  }

  private static @NotNull Comparator<io.camunda.client.api.search.response.Authorization> getAuthComparator() {
    return Comparator
        .comparing(io.camunda.client.api.search.response.Authorization::getOwnerType)
        .thenComparing(io.camunda.client.api.search.response.Authorization::getOwnerId)
        .thenComparing(io.camunda.client.api.search.response.Authorization::getResourceType)
        .thenComparing(io.camunda.client.api.search.response.Authorization::getResourceId)
        .thenComparing((auth1, auth2) -> {
          if (!auth1.getPermissionTypes().containsAll(auth2.getPermissionTypes()) || !auth2.getPermissionTypes().containsAll(auth1.getPermissionTypes())) { // Permissions are the same
            return -1;
          }
          return 0;
    });
  }
}
