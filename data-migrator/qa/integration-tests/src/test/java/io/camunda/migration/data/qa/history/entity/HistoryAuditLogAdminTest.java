/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.AuditLogEntity;
import java.util.List;
import org.camunda.bpm.engine.IdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryAuditLogAdminTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected IdentityService identityService;

  @AfterEach
  public void cleanupData() {
    identityService.clearAuthentication();
    historyService.createUserOperationLogQuery().list().forEach( log -> {
      historyService.deleteUserOperationLogEntry(log.getId());
    });
    identityService.createUserQuery().list().forEach(user -> {
      identityService.deleteUser(user.getId());
    });
    identityService.createGroupQuery().list().forEach(group -> {
      identityService.deleteGroup(group.getId());
    });
    identityService.createTenantQuery().list().forEach(tenant -> {
      identityService.deleteTenant(tenant.getId());
    });
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

}
