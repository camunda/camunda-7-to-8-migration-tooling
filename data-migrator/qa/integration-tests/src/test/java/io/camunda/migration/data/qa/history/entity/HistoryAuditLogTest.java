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
import static org.camunda.bpm.engine.variable.Variables.createVariables;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
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

  @AfterEach
  public void cleanupData() {
    identityService.clearAuthentication();
    historyService.createUserOperationLogQuery().list().forEach(log ->
        historyService.deleteUserOperationLogEntry(log.getId()));
  }

  @Test
  public void shouldMigrateAuditLogs() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    identityService.setAuthenticatedUserId("demo");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Verify audit logs exist in C7
    UserOperationLogEntry userOperationLogEntry = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_CREATE)
        .singleResult();
    assertThat(userOperationLogEntry).isNotNull();
    String annotation = "anAnnotation";
    historyService.setAnnotationForOperationLogById(userOperationLogEntry.getOperationId(), annotation);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(1);
    AuditLogEntity log = logs.getFirst();

    assertThat(log.auditLogKey()).isNotNull();
    assertThat(log.processInstanceKey()).isEqualTo(c8ProcessInstance.getFirst().processInstanceKey());
    assertThat(log.rootProcessInstanceKey()).isEqualTo(c8ProcessInstance.getFirst().processInstanceKey());
    assertThat(log.processDefinitionKey()).isNotNull();
    assertThat(log.userTaskKey()).isNull();

    assertThat(log.timestamp()).isNotNull();
    assertThat(log.actorId()).isEqualTo("demo");
    assertThat(log.actorType()).isEqualTo(AuditLogEntity.AuditLogActorType.USER);
    assertThat(log.processDefinitionId()).isEqualTo(prefixDefinitionId("simpleProcess"));
    assertThat(log.annotation()).isEqualTo(annotation);
    assertThat(log.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(log.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.GLOBAL);
    assertThat(log.result()).isEqualTo(AuditLogEntity.AuditLogOperationResult.SUCCESS);
  }

  @Test
  public void shouldMigrateAuditLogsWithTenant() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn", "tenantA");
    identityService.setAuthentication("demo", null, List.of("tenantA"));
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_CREATE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(1);
    AuditLogEntity log = logs.getFirst();

    assertThat(log.tenantId()).isEqualTo("tenantA");
    assertThat(log.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.TENANT);
  }
  
  @Test
  public void shouldMigrateAuditLogsForSetVariable() {
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
        .operationType(UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLE)
        .count();
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(2);
    assertThat(logs).extracting(AuditLogEntity::category).contains(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(logs).extracting(AuditLogEntity::entityType).containsOnly(AuditLogEntity.AuditLogEntityType.VARIABLE);
    assertThat(logs).extracting(AuditLogEntity::operationType).containsOnly(AuditLogEntity.AuditLogOperationType.UPDATE);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateProcessInstance() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    identityService.setAuthenticatedUserId("demo");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_CREATE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::category).contains(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.CREATE);
  }

  @Test
  public void shouldMigrateAuditLogsForModifyProcessInstance() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Modify process instance to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    runtimeService.createProcessInstanceModification(processInstance.getId())
        .cancelAllForActivity("userTask")
        .execute();

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_MODIFY_PROCESS_INSTANCE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("userTaskProcessId");
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::category).contains(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.MODIFY);
  }

  @Test
  public void shouldMigrateAuditLogsForMigrateProcessInstance() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Deploy a new version of the process
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    var targetProcessDefinition = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("simpleProcess")
        .latestVersion()
        .singleResult();

    // Migrate process instance to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    var migrationPlan = runtimeService.createMigrationPlan(processInstance.getProcessDefinitionId(), targetProcessDefinition.getId())
        .mapEqualActivities()
        .build();
    runtimeService.newMigration(migrationPlan)
        .processInstanceIds(processInstance.getId())
        .execute();

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_MIGRATE)
        .count();
    assertThat(auditLogCount).isEqualTo(3); // 3 logs with same op log id  for the 3 properties

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::category).contains(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.MIGRATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteProcessDefinition() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    var processDefinition = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("simpleProcess")
        .singleResult();
    historyMigrator.migrate();  // Migrate before deletion to ensure process definition is available in C8

    identityService.setAuthenticatedUserId("demo");
    repositoryService.deleteProcessDefinition(processDefinition.getId());

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_DELETE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForEvaluateDecision() {
    // given
    deployer.deployCamunda7Decision("dish-decision.dmn");

    identityService.setAuthenticatedUserId("demo");
    decisionService.evaluateDecisionByKey("Dish")
        .variables(createVariables()
            .putValue("season", "Winter")
            .putValue("guestCount", 5))
        .evaluate();

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_EVALUATE)
        .count();
    assertThat(auditLogCount).isEqualTo(2); // two properties with same op log id

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.DECISION);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.EVALUATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteDecisionHistory() {
    // given
    deployer.deployCamunda7Decision("dish-decision.dmn");

    identityService.setAuthenticatedUserId("demo");
    decisionService.evaluateDecisionByKey("Dish")
        .variables(createVariables()
            .putValue("season", "Winter")
            .putValue("guestCount", 5))
        .evaluate();

    // Get the historic decision instance
    var historicDecisionInstance = historyService.createHistoricDecisionInstanceQuery()
        .decisionDefinitionKey("Dish")
        .singleResult();

    identityService.setAuthenticatedUserId("demo");
    historyService.deleteHistoricDecisionInstanceByInstanceId(historicDecisionInstance.getId());

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_DELETE_HISTORY)
        .count();
    assertThat(auditLogCount).isEqualTo(2); 

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(2);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.DELETE);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.DECISION);
  }

  @Test
  public void shouldMigrateAuditLogsForCreateDeployment() {
    // given
    identityService.setAuthenticatedUserId("demo");
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_CREATE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.RESOURCE);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.CREATE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteDeployment() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    Deployment deployment = repositoryService.createDeploymentQuery()
        .singleResult();

    identityService.setAuthenticatedUserId("demo");
    repositoryService.deleteDeployment(deployment.getId(), true);

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_DELETE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.DELETE);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.RESOURCE);
  }

  @Test
  public void shouldMigrateAuditLogsForRemoveVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Remove variable to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    runtimeService.removeVariable(processInstance.getId(), "testVar");

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_REMOVE_VARIABLE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.VARIABLE);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForDeleteVariableHistory() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Delete variable history to generate audit logs
    identityService.setAuthenticatedUserId("demo");
    historyService.deleteHistoricVariableInstance(
        historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstance.getId())
            .variableName("testVar")
            .singleResult()
            .getId());

    // Verify audit logs exist in C7
    long auditLogCount = historyService.createUserOperationLogQuery()
        .processInstanceId(processInstance.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_DELETE_HISTORY)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.VARIABLE);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.DELETE);
  }

  @Test
  public void shouldMigrateAuditLogsForIncidentResolution() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Incident incident = runtimeService.createIncident("foo", processInstance.getId(), "userTask1", "bar");
    identityService.setAuthenticatedUserId("demo");
    runtimeService.resolveIncident(incident.getId());
    assertThat(historyService.createUserOperationLogQuery().count()).isEqualTo(1L);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(1);
    assertThat(logs).extracting(AuditLogEntity::entityType).contains(AuditLogEntity.AuditLogEntityType.INCIDENT);
    assertThat(logs).extracting(AuditLogEntity::operationType).contains(AuditLogEntity.AuditLogOperationType.RESOLVE);
  }

  @Test
  public void shouldSkipAuditLogsForActivateSuspend() {
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
    assertThat(auditLogCount).isEqualTo(2);

    // when
    historyMigrator.migrate();

    // then
    // Verify process instance was migrated
    List<ProcessInstanceEntity> c8ProcessInstance = searchHistoricProcessInstances("simpleProcess");
    assertThat(c8ProcessInstance).hasSize(1);
    List<AuditLogEntity> logs = searchAuditLogs("simpleProcess");
    assertThat(logs).hasSize(0);
  }

  @Test
  public void shouldSkipMigrationOfAuditLogsWithoutProcessInstance() {
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
    assertThat(auditLogCountBefore).isEqualTo(1);

    // when
    historyMigrator.migrateAuditLogs();

    // then
    List<AuditLogEntity> logs = searchAuditLogsByCategory(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES.name());
    assertThat(logs).hasSize(0);
  }
}
