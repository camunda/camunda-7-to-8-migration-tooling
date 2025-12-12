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

  public static final String USER = "tom.smith";
  public static final String USER_FIRST_NAME = "Tom";
  public static final String USER_LAST_NAME = "Smith";
  public static final String USER_PASSWORD = "password";

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Autowired
  protected AuthorizationService authorizationService;

  @Autowired
  protected IdentityService identityService;

  @Test
  public void shouldMigrateAuthorizations() {
    createUserInC7(USER, USER_FIRST_NAME, USER_LAST_NAME);
    createUserInC8(USER, USER_FIRST_NAME, USER_LAST_NAME);

    Authorization auth1 = createAuthorizationInC7(USER, null, Resources.APPLICATION, "cockpit", Set.of(Permissions.ACCESS));
    Authorization auth2 = createAuthorizationInC7(USER, null, Resources.AUTHORIZATION, "authId", Set.of(Permissions.READ, Permissions.UPDATE));
    Authorization auth3 = createAuthorizationInC7(USER, null, Resources.FILTER, "*", Set.of(Permissions.READ));

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

  private void createUserInC7(String username, String firstName, String lastName) {
    User user = identityService.newUser(username);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setPassword(USER_PASSWORD);
    identityService.saveUser(user);
  }

  private void createUserInC8(String username, String firstName, String lastName) {
    camundaClient.newCreateUserCommand()
      .username(username)
      .name(firstName + " " + lastName)
      .password(USER_PASSWORD)
      .execute();
  }

  private Authorization createAuthorizationInC7(String userId, String groupId, Resources resourceType, String resourceId, Set<Permission> permissions) {
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
