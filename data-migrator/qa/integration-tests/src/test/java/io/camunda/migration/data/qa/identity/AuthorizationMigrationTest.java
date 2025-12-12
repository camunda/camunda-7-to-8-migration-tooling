/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import io.camunda.migration.data.IdentityMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Set;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class AuthorizationMigrationTest extends IdentityAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Autowired
  protected AuthorizationService authorizationService;

  @Autowired
  protected IdentityService identityService;

  @Test
  public void shouldMigrateAuthorizations() {
    createUser("user1", "User", "Name");

    Authorization auth1 = createAuthorization("user1", null, Resources.APPLICATION, "cockpit", Set.of(Permissions.ACCESS));
    Authorization auth2 = createAuthorization("user1", null, Resources.AUTHORIZATION, "authId", Set.of(Permissions.READ, Permissions.UPDATE));
    Authorization auth3 = createAuthorization("user1", null, Resources.FILTER, "*", Set.of(Permissions.READ));

    // when migrating
    identityMigrator.migrate();

  }

  /*
    * TODO cases to test:
    * Happy path for all supported resource types (migrate)
    * Proper mapping from C7 Permission.ALL to all C8 permissions (migrate)
    * Global or Revoke auth (skip)
    * User or group non existent in C8 (skip)
    * Resource type not supported (skip)
    * Resource type supported but at least one permission not supported (skip)
    * Invalid resourceId for resource type (skip) - example: AUTHORIZATION with resourceId != *
    * Invalid mapping for resourceId (skip) - example: APPLICATION with resourceId != cockpit, tasklist, admin
   */

  private void createUser(String username, String firstName, String lastName) {
    // Create in C7
    User user = identityService.newUser(username);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setPassword("password");
    identityService.saveUser(user);

    // Create in C8
    camundaClient.newCreateUserCommand()
        .username(username)
        .name(firstName + " " + lastName)
        .password("password")
        .execute();
  }

  private Authorization createAuthorization(String userId, String groupId, Resources resourceType, String resourceId, Set<Permission> permissions) {
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

}
