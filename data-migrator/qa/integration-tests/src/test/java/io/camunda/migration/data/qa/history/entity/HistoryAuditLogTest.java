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
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for audit log migration from Camunda 7 to Camunda 8.
 * <p>
 * Tests the migration of user operation log entries (audit logs) that track
 * user actions and operations performed on process instances.
 * </p>
 */
public class HistoryAuditLogTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected IdentityService identityService;

  @Test
  public void shouldMigrateAuditLogsForProcessInstanceOperations() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    identityService.setAuthenticatedUserId("demo");
    // Perform operations that generate audit log entries
    runtimeService.suspendProcessInstanceById(processInstance.getId());
    runtimeService.activateProcessInstanceById(processInstance.getId());
    
    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .count();
    assertThat(auditLogCount).isGreaterThan(0);

    // when
    historyMigrator.migrate();

    // then
    // Verify process instance was migrated
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(2);
    assertThat(logs).extracting(AuditLogEntity::category).contains(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
  }

  @Test
  public void shouldMigrateAuditLogsForTaskOperations() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    
    // Complete a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    completeAllUserTasksWithDefaultUserTaskId();
    
    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .count();
    assertThat(auditLogCount).isGreaterThan(0);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().category()).isEqualTo(AuditLogEntity.AuditLogOperationCategory.USER_TASKS);

  }

  @Test
  public void shouldMigrateAuditLogsForVariableOperations() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    
    // Set variables to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    runtimeService.setVariable(processInstance.getId(), "testVar1", "value1");
    runtimeService.setVariable(processInstance.getId(), "testVar2", 123);
    
    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType("SetVariable")
        .count();
    assertThat(auditLogCount).isGreaterThan(0);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(2);
    assertThat(logs).extracting(AuditLogEntity::category).contains(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
  }

  @Test
  public void shouldMigrateAuditLogsForUser() {
    // given
//    deployer.deployCamunda7Process("simpleProcess.bpmn");
//    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Set variables to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var user = identityService.newUser("newUserId");
    identityService.saveUser(user);

    long auditLogCount = historyService.createUserOperationLogQuery()
        .count();
    assertThat(auditLogCount).isGreaterThan(0);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.ADMIN.name());
    assertThat(logs).hasSize(1);
  }

  @Test
  public void shouldHandleMigrationOfAuditLogsWithoutProcessInstance() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    
    // Perform operation
    identityService.setAuthenticatedUserId("demo");
    runtimeService.suspendProcessInstanceById(processInstance.getId());
    
    // Get audit log entries before migration
    long auditLogCountBefore = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .count();
    assertThat(auditLogCountBefore).isGreaterThan(0);

    // when
    historyMigrator.migrateAuditLogs();

    // then
    // Audit logs should be migrated even if process instance is not yet migrated
    // The audit log migrator should handle this gracefully
  }
}
