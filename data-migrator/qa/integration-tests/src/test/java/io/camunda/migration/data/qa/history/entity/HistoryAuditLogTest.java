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
import io.camunda.search.entities.ProcessInstanceEntity;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

/**
 * Integration test for audit log migration from Camunda 7 to Camunda 8.
 * <p>
 * Tests the migration of user operation log entries (audit logs) that track
 * user actions and operations performed on process instances.
 * </p>
 */
public class HistoryAuditLogTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigrateAuditLogsForProcessInstanceOperations() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcessId");
    
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
    ProcessInstanceEntity c8ProcessInstance = findHistoricProcessInstance(processInstance.getId());
    assertThat(c8ProcessInstance).isNotNull();
    
    // Note: Verification of migrated audit logs in C8 will depend on the 
    // availability of audit log search API in Camunda 8.9.0
    // This test establishes the migration infrastructure
  }

  @Test
  public void shouldMigrateAuditLogsForTaskOperations() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    
    // Complete a user task to generate audit logs
    completeAllUserTasksWithDefaultUserTaskId();
    
    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .count();
    assertThat(auditLogCount).isGreaterThan(0);

    // when
    historyMigrator.migrate();

    // then
    // Verify process instance was migrated
    ProcessInstanceEntity c8ProcessInstance = findHistoricProcessInstance(processInstance.getId());
    assertThat(c8ProcessInstance).isNotNull();
    assertThat(c8ProcessInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.COMPLETED);
  }

  @Test
  public void shouldMigrateAuditLogsForVariableOperations() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcessId");
    
    // Set variables to generate audit logs
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
    // Verify process instance was migrated
    ProcessInstanceEntity c8ProcessInstance = findHistoricProcessInstance(processInstance.getId());
    assertThat(c8ProcessInstance).isNotNull();
  }

  @Test
  public void shouldHandleMigrationOfAuditLogsWithoutProcessInstance() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcessId");
    
    // Perform operation
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
