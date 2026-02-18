/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.AuditLogEntity;
import java.util.List;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.identity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryAuditLogAdminTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected IdentityService identityService;

  @Autowired
  protected AuthorizationService authorizationService;

  @AfterEach
  public void cleanupData() {
    identityService.clearAuthentication();
    historyService.createUserOperationLogQuery().list().forEach(log ->
        historyService.deleteUserOperationLogEntry(log.getId()));
    identityService.createUserQuery().list().forEach(user ->
        identityService.deleteUser(user.getId()));
    identityService.createGroupQuery().list().forEach(group ->
        identityService.deleteGroup(group.getId()));
    identityService.createTenantQuery().list().forEach(tenant ->
        identityService.deleteTenant(tenant.getId()));
    authorizationService.createAuthorizationQuery().list().forEach(authorization ->
        authorizationService.deleteAuthorization(authorization.getId()));
  }

  @Test
  public void shouldMigrateAuditLogsForUser() {
    // given
    identityService.setAuthenticatedUserId("demo");
    var user = identityService.newUser("newUserId");
    identityService.saveUser(user);

    long auditLogCount = historyService.createUserOperationLogQuery()
        .count();
    assertThat(auditLogCount).isEqualTo(1);
    String annotation = "anAnnotation";
    historyService.setAnnotationForOperationLogById(historyService.createUserOperationLogQuery().singleResult().getOperationId(), annotation);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);
    AuditLogEntity log = logs.getFirst();

    assertThat(log.auditLogKey()).isNotNull();
    assertThat(log.processInstanceKey()).isNull();
    assertThat(log.rootProcessInstanceKey()).isNull();
    assertThat(log.processDefinitionKey()).isNull();
    assertThat(log.userTaskKey()).isNull();
    assertThat(log.timestamp()).isNotNull();
    assertThat(log.actorId()).isEqualTo("demo");
    assertThat(log.actorType()).isEqualTo(AuditLogEntity.AuditLogActorType.USER);
    assertThat(log.processDefinitionId()).isNull();
    assertThat(log.annotation()).isEqualTo(annotation);
    assertThat(log.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(log.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.GLOBAL);
    assertThat(log.entityType()).isEqualTo(AuditLogEntity.AuditLogEntityType.USER);
    assertThat(log.operationType()).isEqualTo(AuditLogEntity.AuditLogOperationType.CREATE);
    assertThat(log.result()).isEqualTo(AuditLogEntity.AuditLogOperationResult.SUCCESS);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateUser() {
    // given
    identityService.setAuthenticatedUserId("demo");
    var user = identityService.newUser("newUserId");
    identityService.saveUser(user);

    long auditLogCount = historyService.createUserOperationLogQuery()
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.USER);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.CREATE);
  }

  @Test
  public void shouldMigrateAuditLogsForUpdateUser() {
    // given
    var user = identityService.newUser("newUserId");
    user.setFirstName("John");
    identityService.saveUser(user);

    // Update the user
    identityService.setAuthenticatedUserId("demo");
    user.setFirstName("Jane");
    identityService.saveUser(user);

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.USER);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteUser() {
    // given
    var user = identityService.newUser("newUserId");
    identityService.saveUser(user);

    // Delete the user
    identityService.setAuthenticatedUserId("demo");
    identityService.deleteUser("newUserId");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.USER);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateGroup() {
    // given
    identityService.setAuthenticatedUserId("demo");
    var group = identityService.newGroup("newGroupId");
    group.setName("Test Group");
    identityService.saveGroup(group);

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.GROUP);
    assertThat(logs).extracting(AuditLogEntity::operationType).containsOnly(AuditLogEntity.AuditLogOperationType.CREATE);
  }

  @Test
  public void shouldMigrateAuditLogsForUpdateGroup() {
    // given
    var group = identityService.newGroup("newGroupId");
    group.setName("Test Group");
    identityService.saveGroup(group);

    // Update the group
    identityService.setAuthenticatedUserId("demo");
    group.setName("Updated Group");
    identityService.saveGroup(group);

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.GROUP);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteGroup() {
    // given
    var group = identityService.newGroup("newGroupId");
    group.setName("Test Group");
    identityService.saveGroup(group);

    // Delete the group
    identityService.setAuthenticatedUserId("demo");
    identityService.deleteGroup("newGroupId");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.GROUP);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateTenant() {
    // given
    identityService.setAuthenticatedUserId("demo");
    var tenant = identityService.newTenant("newTenantId");
    identityService.saveTenant(tenant);

    identityService.clearAuthentication();
    long auditLogCount = historyService.createUserOperationLogQuery()
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType).containsOnly(AuditLogEntity.AuditLogOperationType.CREATE);
  }

  @Test
  public void shouldMigrateAuditLogsForUpdateTenant() {
    // given
    var tenant = identityService.newTenant("newTenantId");
    identityService.saveTenant(tenant);

    // Update the tenant
    identityService.setAuthenticatedUserId("demo");
    tenant.setName("Updated Tenant");
    identityService.saveTenant(tenant);
    identityService.clearAuthentication();

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteTenant() {
    // given
    var tenant = identityService.newTenant("newTenantId");
    identityService.saveTenant(tenant);

    // Delete the tenant
    identityService.setAuthenticatedUserId("demo");
    identityService.deleteTenant("newTenantId");
    identityService.clearAuthentication();

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateAuthorization() {
    // given
    createUser();
    identityService.setAuthenticatedUserId("demo");
    Authorization authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    authorization.setUserId("testUser");
    authorization.setResource(Resources.PROCESS_DEFINITION);
    authorization.setResourceId("*");
    authorization.addPermission(Permissions.READ);
    authorizationService.saveAuthorization(authorization);

    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_CREATE)
        .count();
    assertThat(auditLogCount).isEqualTo(6); // there are 6 entries for the same log due to 6 properties

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.AUTHORIZATION);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.CREATE);
  }

  @Test
  public void shouldMigrateAuditLogsForUpdateAuthorization() {
    // given
    createUser();
    Authorization authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    authorization.setUserId("testUser");
    authorization.setResource(Resources.PROCESS_DEFINITION);
    authorization.setResourceId("*");
    authorization.addPermission(Permissions.READ);
    authorizationService.saveAuthorization(authorization);

    // Update the authorization
    identityService.setAuthenticatedUserId("demo");
    authorization.addPermission(Permissions.UPDATE);
    authorizationService.saveAuthorization(authorization);

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(6); // there are 6 entries for the same log due to 6 properties

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.AUTHORIZATION);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteAuthorization() {
    // given
    createUser();
    Authorization authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    authorization.setUserId("testUser");
    authorization.setResource(Resources.PROCESS_DEFINITION);
    authorization.setResourceId("*");
    authorization.addPermission(Permissions.READ);
    authorizationService.saveAuthorization(authorization);

    // Delete the authorization
    identityService.setAuthenticatedUserId("demo");
    authorizationService.deleteAuthorization(authorization.getId());
    identityService.clearAuthentication();

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(6); // there are 6 entries for the same log due to 6 properties

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.AUTHORIZATION);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateGroupMembership() {
    // given
    var user = identityService.newUser("testUser");
    identityService.saveUser(user);
    var group = identityService.newGroup("testGroup");
    identityService.saveGroup(group);

    identityService.setAuthenticatedUserId("demo");
    identityService.createMembership("testUser", "testGroup");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.GROUP);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.ASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteGroupMembership() {
    // given
    var user = identityService.newUser("testUser");
    identityService.saveUser(user);
    var group = identityService.newGroup("testGroup");
    identityService.saveGroup(group);
    identityService.createMembership("testUser", "testGroup");

    identityService.setAuthenticatedUserId("demo");
    identityService.deleteMembership("testUser", "testGroup");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.GROUP);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UNASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateTenantUserMembership() {
    // given
    var user = identityService.newUser("testUser");
    identityService.saveUser(user);
    var tenant = identityService.newTenant("testTenant");
    identityService.saveTenant(tenant);

    identityService.setAuthentication("demo", null, List.of("testTenant"));
    identityService.createTenantUserMembership("testTenant", "testUser");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.ASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteTenantUserMembership() {
    // given
    var user = identityService.newUser("testUser");
    identityService.saveUser(user);
    var tenant = identityService.newTenant("testTenant");
    identityService.saveTenant(tenant);
    identityService.createTenantUserMembership("testTenant", "testUser");

    identityService.setAuthentication("demo", null, List.of("testTenant"));
    identityService.deleteTenantUserMembership("testTenant", "testUser");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UNASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateTenantGroupMembership() {
    // given
    var group = identityService.newGroup("testGroup");
    identityService.saveGroup(group);
    var tenant = identityService.newTenant("testTenant");
    identityService.saveTenant(tenant);

    identityService.setAuthentication("demo", null, List.of("testTenant"));
    identityService.createTenantGroupMembership("testTenant", "testGroup");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.ASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteTenantGroupMembership() {
    // given
    var group = identityService.newGroup("testGroup");
    identityService.saveGroup(group);
    var tenant = identityService.newTenant("testTenant");
    identityService.saveTenant(tenant);
    identityService.createTenantGroupMembership("testTenant", "testGroup");


    identityService.setAuthentication("demo", null, List.of("testTenant"));
    identityService.deleteTenantGroupMembership("testTenant", "testGroup");

    long auditLogCount = historyService.createUserOperationLogQuery().count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);

    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.TENANT);
    assertThat(logs).extracting(AuditLogEntity::operationType)
        .containsOnly(AuditLogEntity.AuditLogOperationType.UNASSIGN);
  }

  protected void createUser() {
    User testUser = identityService.newUser("testUser");
    identityService.saveUser(testUser);
  }

}
