/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public class IdentityAbstractTest extends AbstractMigratorTest {

  @Autowired
  protected CamundaClient camundaClient;

  @Autowired
  protected IdentityService identityService;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected IdentityMigrator identityMigrator;

  @AfterEach
  public void cleanup() {
    identityService.createTenantQuery().list().forEach(tenant -> identityService.deleteTenant(tenant.getId()));
    dbClient.deleteAllMappings();
  }

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
    user.setPassword(AuthorizationMigrationTest.USER_PASSWORD);
    identityService.saveUser(user);
  }

  protected void createTenantInC8(String tenantId, String tenantName) {
    camundaClient.newCreateTenantCommand()
        .tenantId(tenantId)
        .name(tenantName)
        .execute();

    // Because of eventual consistency, wait until group is visible
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
      .password(AuthorizationMigrationTest.USER_PASSWORD)
      .execute();

    // Because of eventual consistency, wait until user is visible
    await().timeout(5, TimeUnit.SECONDS).until(() -> camundaClient.newUsersSearchRequest().filter(f -> f.username(username)).execute().items().size() == 1);
  }
}
