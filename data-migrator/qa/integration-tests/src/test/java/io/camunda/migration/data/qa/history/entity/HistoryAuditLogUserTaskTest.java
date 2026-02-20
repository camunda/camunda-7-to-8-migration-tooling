/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.util.List;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for audit log migration from Camunda 7 to Camunda 8.
 * <p>
 * Tests the migration of user operation log entries (audit logs) that track
 * user actions and operations performed on process instances.
 * </p>
 */
public class HistoryAuditLogUserTaskTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected IdentityService identityService;

  @AfterEach
  public void cleanupData() {
    identityService.clearAuthentication();
    historyService.createUserOperationLogQuery().list().forEach(log ->
        historyService.deleteUserOperationLogEntry(log.getId()));
  }

  @Test
  public void shouldMigrateAuditLogsForTask() {
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
    assertThat(auditLogCount).isEqualTo(1);
    String annotation = "anAnnotation";
    historyService.setAnnotationForOperationLogById(historyService.createUserOperationLogQuery().singleResult().getOperationId(), annotation);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<UserTaskEntity> userTaskEntities = searchHistoricUserTasks(c8ProcessInstance.getFirst().processInstanceKey());
    assertThat(userTaskEntities).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    AuditLogEntity log = logs.getFirst();

    assertThat(log.auditLogKey()).isNotNull();
    assertThat(log.entityKey()).isEqualTo(String.valueOf(log.userTaskKey()));
    assertThat(log.processInstanceKey()).isEqualTo(c8ProcessInstance.getFirst().processInstanceKey());
    assertThat(log.rootProcessInstanceKey()).isEqualTo(c8ProcessInstance.getFirst().processInstanceKey());
    assertThat(log.processDefinitionKey()).isNotNull();
    assertThat(log.userTaskKey()).isNotNull();
    assertThat(log.elementInstanceKey()).isEqualTo(userTaskEntities.getFirst().elementInstanceKey());
    assertThat(log.timestamp()).isNotNull();
    assertThat(log.actorId()).isEqualTo("demo");
    assertThat(log.actorType()).isEqualTo(AuditLogEntity.AuditLogActorType.USER);
    assertThat(log.processDefinitionId()).isEqualTo(prefixDefinitionId("userTaskProcessId"));
    assertThat(log.annotation()).isEqualTo(annotation);
    assertThat(log.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(log.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.GLOBAL);
    assertThat(log.result()).isEqualTo(AuditLogEntity.AuditLogOperationResult.SUCCESS);
    assertThat(log.agentElementId()).isNull();
    assertThat(log.relatedEntityKey()).isNull();
    assertThat(log.relatedEntityType()).isNull();
  }

  @Test
  public void shouldMigrateAuditLogsForTaskWithTenant() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn", "tenantA");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Complete a user task to generate audit logs
    identityService.setAuthentication("demo", null, List.of("tenantA"));
    completeAllUserTasksWithDefaultUserTaskId();

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    AuditLogEntity log = logs.getFirst();

    assertThat(log.tenantId()).isEqualTo("tenantA");
    assertThat(log.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.TENANT);
  }

  @Test
  public void shouldMigrateAuditLogsForCompleteTask() {
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
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.COMPLETE);
  }

  @Test
  public void shouldMigrateAuditLogsForAssignTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Assign a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.setAssignee(task.getId(), "assignedUser");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_ASSIGN)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.ASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForClaimTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Claim a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.claim(task.getId(), "claimingUser");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_CLAIM)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.ASSIGN);
  }

  @Test
  public void shouldMigrateAuditLogsForDelegateTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Delegate a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.delegateTask(task.getId(), "delegatedUser");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_DELEGATE)
        .count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs.size()).isEqualTo(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.ASSIGN);
  }

  @Test
  @Disabled
  public void shouldMigrateAuditLogsForDeleteTask() {
    // given
    Task task = taskService.newTask();
    taskService.saveTask(task);

    // Delete a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    taskService.deleteTask(task.getId());

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType("Delete")
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.USER_TASKS.name());
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForResolveTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Resolve task to generate audit logs
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    identityService.setAuthenticatedUserId("demo");
    taskService.resolveTask(task.getId());

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_RESOLVE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs.stream().filter(log -> log.operationType().equals(AuditLogEntity.AuditLogOperationType.UPDATE)).count()).isEqualTo(1);
    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.USER_TASK);
    assertThat(logs).extracting(AuditLogEntity::category).containsOnly(AuditLogEntity.AuditLogOperationCategory.USER_TASKS);
  }

  @Test
  public void shouldMigrateAuditLogsForSetOwnerTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Set owner on a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.setOwner(task.getId(), "taskOwner");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_SET_OWNER)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForSetPriorityTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Set priority on a user task to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.setPriority(task.getId(), 100);

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_SET_PRIORITY)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForUpdateTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Update a user task property to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    task.setDescription("Updated description");
    taskService.saveTask(task);

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_UPDATE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertAuditLogProperties(logs, AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldSkipAuditLogsWhenUserTasksAreNotMigrated() {
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
    assertThat(auditLogCount).isEqualTo(1);

    // when user tasks are not migrated
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateAuditLogs();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(0);
  }

  protected void assertAuditLogProperties(List<AuditLogEntity> logs, AuditLogEntity.AuditLogOperationType opType) {
    assertThat(logs.getFirst().category()).isEqualTo(AuditLogEntity.AuditLogOperationCategory.USER_TASKS);
    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.USER_TASK);
    assertThat(logs).extracting(AuditLogEntity::operationType).containsOnly(opType);
  }

}
