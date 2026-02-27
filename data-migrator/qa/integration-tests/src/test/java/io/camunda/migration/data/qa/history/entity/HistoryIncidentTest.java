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
import static io.camunda.search.entities.IncidentEntity.ErrorType.CONDITION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.DECISION_EVALUATION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.FORM_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.JOB_NO_RETRIES;
import static io.camunda.search.entities.IncidentEntity.ErrorType.RESOURCE_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNHANDLED_ERROR_EVENT;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNKNOWN;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.impl.incident.IncidentHandling.createIncident;
import static org.camunda.bpm.engine.impl.jobexecutor.ExecuteJobHelper.executeJob;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.IncidentEntity;
import java.util.Collections;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryIncidentTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfigurationImpl;

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
    assertThat(incidentsDefaultTenant).singleElement()
        .extracting(IncidentEntity::tenantId)
        .isEqualTo(C8_DEFAULT_TENANT);
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
    ProcessInstance childProcess = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey("calledProcessInstanceId")
        .singleResult();
    createIncident("userTaskId"); // create incident in child's task

    HistoricIncident c7ChildIncident = historyService.createHistoricIncidentQuery()
        .processInstanceId(childProcess.getProcessInstanceId())
        .singleResult();
    assertThat(c7ChildIncident).isNotNull();

    HistoricIncident c7ParentIncident = historyService.createHistoricIncidentQuery()
        .processInstanceId(parentProcess.getProcessInstanceId())
        .singleResult();
    assertThat(c7ParentIncident).isNotNull();

    // when
    historyMigrator.migrate();

    // then

    // child incident is migrated
    List<IncidentEntity> childIncidents = searchHistoricIncidents(childProcess.getProcessDefinitionKey());
    assertThat(childIncidents).hasSize(1);
    assertOnIncidentBasicFields(childIncidents.getFirst(), c7ChildIncident, childProcess, parentProcess, UNKNOWN,
        false);

    // parent incident is migrated
    List<IncidentEntity> parentIncidents = searchHistoricIncidents(parentProcess.getProcessDefinitionKey());
    assertThat(parentIncidents).hasSize(1);
    assertOnIncidentBasicFields(parentIncidents.getFirst(), c7ParentIncident, parentProcess, parentProcess);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, RESOURCE_NOT_FOUND, true);
  }

  @Test
  public void shouldMigrateIncidentWithFormNotFoundErrorType() {
    // given
    deployer.deployCamunda7Process("formNotFoundProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("formNotFoundProcessId");
    triggerIncident(c7ProcessInstance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("formNotFoundProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity c8Incident = incidents.getFirst();
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, FORM_NOT_FOUND, true);
  }

  @Test
  public void shouldMigrateIncidentWithDecisionEvaluationErrorType() {
    // given
    deployer.deployCamunda7Process("ruleTaskProcess.bpmn");
    deployer.deployCamunda7Decision("mappingFailureDmn.dmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("ruleTaskProcessId",
        Collections.singletonMap("input", "single entry list"));
    triggerIncident(c7ProcessInstance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("ruleTaskProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity c8Incident = incidents.getFirst();
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, DECISION_EVALUATION_ERROR, true);
  }

  @Test
  public void shouldMigrateIncidentWithConditionalErrorType() {
    // given
    deployer.deployCamunda7Process("conditionErrorProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("conditionErrorProcessId");
    triggerIncident(c7ProcessInstance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("conditionErrorProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity c8Incident = incidents.getFirst();
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, CONDITION_ERROR, true);
  }

  @Test
  public void shouldMigrateIncidentWithUnhandledErrorType() {
    // given
    processEngineConfigurationImpl.setEnableExceptionsAfterUnhandledBpmnError(true);
    deployer.deployCamunda7Process("unhandledErrorProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("unhandledErrorProcessId");
    triggerIncident(c7ProcessInstance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("unhandledErrorProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity c8Incident = incidents.getFirst();
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, UNHANDLED_ERROR_EVENT, true);
  }

  @Test
  public void shouldMigrateIncidentWithNoJobRetriesErrorType() {
    // given
    processEngineConfigurationImpl.setEnableExceptionsAfterUnhandledBpmnError(true);
    deployer.deployCamunda7Process("noJobRetriesProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("noJobRetriesProcessId");
    triggerIncident(c7ProcessInstance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("noJobRetriesProcessId");
    assertThat(incidents).hasSize(1);
    IncidentEntity c8Incident = incidents.getFirst();
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, JOB_NO_RETRIES, true);
  }

  @Test
  public void shouldGenerateTreePathForIncidentsWithFlowNodeInstanceKey() {
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
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes = searchFlowNodeInstancesByName("UserTaskName");
    assertThat(flowNodes).hasSize(1);
    Long flownodeInstanceKey = flowNodes.getFirst().flowNodeInstanceKey();

    List<IncidentDbModel> incidents = searchIncidentsByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    assertThat(incidents).singleElement()
        .extracting(IncidentDbModel::treePath)
        .isNotNull()
        .isEqualTo("PI_" + processInstanceKey + "/FNI_" + flownodeInstanceKey);
  }

  @Test
  public void shouldGenerateTreePathForIncidentsWithoutFlowNodeInstanceKey() {
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
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("incidentProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<IncidentDbModel> incidents = searchIncidentsByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    assertThat(incidents).singleElement()
        .extracting(IncidentDbModel::treePath)
        .isNotNull()
        .isEqualTo("PI_" + processInstanceKey);
  }

  protected void assertOnIncidentBasicFields(IncidentEntity c8Incident,
                                             HistoricIncident c7Incident,
                                             ProcessInstance c7ChildInstance,
                                             ProcessInstance c7ParentInstance) {
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ChildInstance, c7ParentInstance, UNKNOWN, false);
  }

  protected void assertOnIncidentBasicFields(IncidentEntity c8Incident,
                                             HistoricIncident c7Incident,
                                             ProcessInstance c7ChildInstance,
                                             ProcessInstance c7ParentInstance,
                                             IncidentEntity.ErrorType errorType,
                                             boolean waitingExecution) {
    // specific values
    assertThat(c8Incident.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(c8Incident.processDefinitionId()).isEqualTo(
        prefixDefinitionId(c7ChildInstance.getProcessDefinitionKey()));
    assertThat(c8Incident.flowNodeId()).isEqualTo(c7Incident.getActivityId());
    assertThat(c8Incident.state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
    assertThat(c8Incident.errorMessage()).isEqualTo(c7Incident.getIncidentMessage());
    assertThat(c8Incident.processInstanceKey()).isEqualTo(
        findMigratedProcessInstanceKey(c7ChildInstance.getProcessDefinitionKey()));
    String expectedRootProcessKey = c7ParentInstance != null ?
        c7ParentInstance.getProcessDefinitionKey() :
        c7ChildInstance.getProcessDefinitionKey();
    assertThat(c8Incident.rootProcessInstanceKey()).isEqualTo(findMigratedProcessInstanceKey(expectedRootProcessKey));
    assertThat(c8Incident.errorType()).isEqualTo(errorType);

    // non-null values
    assertThat(c8Incident.incidentKey()).isNotNull();
    assertThat(c8Incident.creationTime()).isNotNull();

    // null values
    assertThat(c8Incident.jobKey()).isNull();

    // conditional
    if (waitingExecution) {
      assertThat(c8Incident.flowNodeInstanceKey()).isNull();
    } else {
      assertThat(c8Incident.flowNodeInstanceKey()).isNotNull();
    }
  }

  protected void executeJob(ProcessInstance c7ProcessInstance) {
    Job job = managementService.createJobQuery().processInstanceId(c7ProcessInstance.getId()).singleResult();

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
