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

import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.IncidentEntity;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;

public class HistoryIncidentTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigrateIncidentTenant() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    deployer.deployCamunda7Process("incidentProcess2.bpmn", "tenant1");
    ProcessInstance c7ProcessDefaultTenant = runtimeService.startProcessInstanceByKey("incidentProcessId");
    ProcessInstance c7ProcessTenant1 = runtimeService.startProcessInstanceByKey("incidentProcessId2");
    triggerIncident(c7ProcessDefaultTenant.getId());
    triggerIncident(c7ProcessTenant1.getId());

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidentsDefaultTenant = searchHistoricIncidents("incidentProcessId");
    List<IncidentEntity> incidentsTenant1 = searchHistoricIncidents("incidentProcessId2");
    assertThat(incidentsDefaultTenant).singleElement().extracting(IncidentEntity::tenantId).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(incidentsTenant1).singleElement().extracting(IncidentEntity::tenantId).isEqualTo("tenant1");
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForActiveTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    createIncident("userTaskId");

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7Incident, c7ProcessInstance, null);
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForCompleteTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    createIncident("userTaskId");
    String userTaskId = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult().getId();
    taskService.complete(userTaskId);

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7Incident, c7ProcessInstance, null);
  }

  @Test
  public void shouldMigrateIncidentForNestedProcessInstance() {
    // given nested processes with incident in child instance
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    ProcessInstance parentProcess = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance childProcess = runtimeService.createProcessInstanceQuery().processDefinitionKey("calledProcessInstanceId").singleResult();
    createIncident("userTaskId"); // create incident in child's task

    HistoricIncident c7ChildIncident = historyService.createHistoricIncidentQuery().processInstanceId(childProcess.getProcessInstanceId()).singleResult();
    assertThat(c7ChildIncident).isNotNull();

    // when
    historyMigrator.migrate();
    // need to run with retry to migrate child instances with flow node dependencies
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents(childProcess.getProcessDefinitionKey());
    assertThat(incidents).hasSize(1);

    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7ChildIncident, childProcess, parentProcess);
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForActiveTaskWithAsyncBefore() {
    // given
    deployer.deployCamunda7Process("userTaskProcessAsyncBefore.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    executeJob(c7ProcessInstance);
    createIncident("userTaskId");

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7Incident, c7ProcessInstance, null);
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForActiveTaskWithAsyncAfter() {
    // given
    deployer.deployCamunda7Process("userTaskProcessAsyncAfter.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    createIncident("userTaskId");

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7Incident, c7ProcessInstance, null);
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForCompleteTaskWithAsyncBefore() {
    // given
    deployer.deployCamunda7Process("userTaskProcessAsyncBefore.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    executeJob(c7ProcessInstance);
    createIncident("userTaskId");
    String userTaskId = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult().getId();
    taskService.complete(userTaskId);

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7Incident, c7ProcessInstance, null);
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForCompleteTaskWithAsyncAfter() {
    // given
    deployer.deployCamunda7Process("userTaskProcessAsyncAfter.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    createIncident("userTaskId");
    String userTaskId = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult().getId();
    taskService.complete(userTaskId);

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity incident = incidents.getFirst();
    assertOnIncidentBasicFields(incident, c7Incident, c7ProcessInstance, null);
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForServiceTask() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("incidentProcessId");
    triggerIncident(c7ProcessInstance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("incidentProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity c8Incident = incidents.getFirst();

    // specific values
    assertThat(c8Incident.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(c8Incident.processDefinitionId()).isEqualTo(prefixDefinitionId(c7ProcessInstance.getProcessDefinitionKey()));
    assertThat(c8Incident.flowNodeId()).isEqualTo(c7Incident.getActivityId());
    assertThat(c8Incident.state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
    assertThat(c8Incident.errorMessage()).isEqualTo(c7Incident.getIncidentMessage());
    assertThat(c8Incident.processInstanceKey()).isEqualTo(findMigratedProcessInstanceKey(c7ProcessInstance.getProcessDefinitionKey()));
    String expectedRootProcessKey = c7ProcessInstance.getProcessDefinitionKey();
    assertThat(c8Incident.rootProcessInstanceKey()).isEqualTo(findMigratedProcessInstanceKey(expectedRootProcessKey));

    // non-null values
    assertThat(c8Incident.incidentKey()).isNotNull();
    assertThat(c8Incident.creationTime()).isNotNull();

    // null values
    assertThat(c8Incident.jobKey()).isNull();
    assertThat(c8Incident.flowNodeInstanceKey()).isNull(); // service task's flow node hasn't been migrated so it doens't have a key
  }

  private void assertOnIncidentBasicFields(IncidentEntity c8Incident, HistoricIncident c7Incident, ProcessInstance c7ChildInstance, ProcessInstance c7ParentInstance) {
    // specific values
    assertThat(c8Incident.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(c8Incident.processDefinitionId()).isEqualTo(prefixDefinitionId(c7ChildInstance.getProcessDefinitionKey()));
    assertThat(c8Incident.flowNodeId()).isEqualTo(c7Incident.getActivityId());
    assertThat(c8Incident.state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
    assertThat(c8Incident.errorMessage()).isEqualTo(c7Incident.getIncidentMessage());
    assertThat(c8Incident.processInstanceKey()).isEqualTo(findMigratedProcessInstanceKey(c7ChildInstance.getProcessDefinitionKey()));
    String expectedRootProcessKey = c7ParentInstance != null ? c7ParentInstance.getProcessDefinitionKey() : c7ChildInstance.getProcessDefinitionKey();
    assertThat(c8Incident.rootProcessInstanceKey()).isEqualTo(findMigratedProcessInstanceKey(expectedRootProcessKey));

    // non-null values
    assertThat(c8Incident.incidentKey()).isNotNull();
    assertThat(c8Incident.creationTime()).isNotNull();
    assertThat(c8Incident.flowNodeInstanceKey()).isNotNull();

    // null values
    assertThat(c8Incident.jobKey()).isNull();
  }

  protected void executeJob(ProcessInstance c7ProcessInstance) {
    Job job = managementService
        .createJobQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();

    if (job != null) {
      managementService.executeJob(job.getId());
    }
  }

  protected Long findMigratedProcessInstanceKey(String processDefinitionKey) {
    return searchHistoricProcessInstances(processDefinitionKey).getFirst().processInstanceKey();
  }

  protected void createIncident(String taskId) {
    Task task = taskService.createTaskQuery().taskDefinitionKey(taskId).singleResult();
    String executionId = task.getExecutionId();
    runtimeService.createIncident("foo", executionId, "bar");
  }
}
