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
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.search.entities.IncidentEntity.ErrorType.CONDITION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.DECISION_EVALUATION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.FORM_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.JOB_NO_RETRIES;
import static io.camunda.search.entities.IncidentEntity.ErrorType.RESOURCE_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNHANDLED_ERROR_EVENT;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryIncidentTest extends HistoryMigrationAbstractTest {

  protected static final String EXT_PROCESS_KEY = "externalTaskProcess";
  protected static final String EXT_TASK_ID = "externalTask";
  protected static final String EXT_TOPIC_NAME = "myTopic";
  protected static final String EXT_WORKER_ID = "myWorker";

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfigurationImpl;

  @Autowired
  protected ExternalTaskService externalTaskService;

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
        false, false);

    // parent incident is migrated
    List<IncidentEntity> parentIncidents = searchHistoricIncidents(parentProcess.getProcessDefinitionKey());
    assertThat(parentIncidents).hasSize(1);
    assertOnIncidentBasicFields(parentIncidents.getFirst(), c7ParentIncident, parentProcess, parentProcess);
  }

  @Test
  public void shouldMigrateFailedJobIncidentForNestedProcessInstance() {
    // given a parent process that calls a child process containing a failing async service task
    deployer.deployCamunda7Process("callActivityWithIncidentSubprocess.bpmn");
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance parentProcess = runtimeService.startProcessInstanceByKey("callActivityWithIncidentSubprocessId");
    ProcessInstance childProcess = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey("incidentProcessId")
        .singleResult();
    assertThat(childProcess).isNotNull();

    // trigger the failing job in the child process to create a failedJob incident
    triggerIncident(childProcess.getProcessInstanceId());

    // verify incidents exist in both child and parent process instances in C7
    HistoricIncident c7ChildIncident = historyService.createHistoricIncidentQuery()
        .processInstanceId(childProcess.getProcessInstanceId())
        .singleResult();
    assertThat(c7ChildIncident).isNotNull();
    assertThat(c7ChildIncident.getConfiguration()).as("leaf incident should have a job reference").isNotNull();

    HistoricIncident c7ParentIncident = historyService.createHistoricIncidentQuery()
        .processInstanceId(parentProcess.getProcessInstanceId())
        .singleResult();
    assertThat(c7ParentIncident).isNotNull();
    assertThat(c7ParentIncident.getConfiguration()).as("propagated parent incident should have no job reference").isNull();

    // when
    historyMigrator.migrate();

    // then both incidents are migrated

    // child incident has a job key (the failed job was migrated)
    List<IncidentEntity> childIncidents = searchHistoricIncidents(childProcess.getProcessDefinitionKey());
    assertThat(childIncidents).as("child process incident should be migrated").hasSize(1);
    assertThat(childIncidents.getFirst().jobKey()).as("child incident should have a job key").isNotNull();

    // parent incident is migrated without a job key (propagated incident has no direct job reference)
    List<IncidentEntity> parentIncidents = searchHistoricIncidents(parentProcess.getProcessDefinitionKey());
    assertThat(parentIncidents).as("parent process incident should be migrated").hasSize(1);
    assertThat(parentIncidents.getFirst().jobKey()).as("propagated parent incident should have no job key").isNull();
    assertThat(parentIncidents.getFirst().processInstanceKey()).isNotNull();
    assertThat(parentIncidents.getFirst().state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, RESOURCE_NOT_FOUND, true, true);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, FORM_NOT_FOUND, true, true);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, DECISION_EVALUATION_ERROR, true, true);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, CONDITION_ERROR, true, true);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, UNHANDLED_ERROR_EVENT, true, true);
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
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ProcessInstance, null, JOB_NO_RETRIES, true, true);
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

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103")
  public void shouldMigrateMultiInstanceFlowNodeReference() {
    // given
    deployer.deployCamunda7Process("miProcess.bpmn");
    runtimeService.startProcessInstanceByKey("miProcess");

    var task = taskService.createTaskQuery().taskDefinitionKey("userTask1").list();
    task.forEach(t -> {
      String executionId = t.getExecutionId();
      runtimeService.createIncident("foo", executionId, "bar");
    });

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("miProcess");
    assertThat(processInstances).hasSize(1);

    List<IncidentEntity> incidents = searchHistoricIncidents("miProcess");
    assertThat(incidents).hasSize(2);

    assertThat(incidents.getFirst().flowNodeInstanceKey()).isNotEqualTo(incidents.getLast().flowNodeInstanceKey());
  }

  @Test
  public void shouldMigrateIncidentForFailedExternalTask() {
    // given: a process with an external task that fails with an error message and details
    deployExternalTaskProcess();
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(EXT_PROCESS_KEY);

    // lock and report failure with 0 retries to create an incident
    lockAndFailExternalTask();

    List<HistoricIncident> list = historyService.createHistoricIncidentQuery().list();
    assertThat(list).hasSize(1);

    // when: migration runs
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(EXT_PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by external task ID)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 external task (deduplication by external task ID)").hasSize(1);

    List<IncidentEntity> incidents = searchHistoricIncidents(EXT_PROCESS_KEY);
    assertThat(incidents).hasSize(1);
    assertOnIncidentBasicFields(incidents.getFirst(), list.getFirst(), processInstance, null, UNKNOWN, false, true);
    assertThat(incidents.getFirst().jobKey()).isEqualTo(c8Jobs.getFirst().jobKey());
  }

  @Test
  public void shouldMigrateIncidentTenantForFailedExternalTask() {
    // given: external task processes deployed with default and custom tenants
    String processKey2 = "externalTaskProcess2";

    deployExternalTaskProcess();

    var c7Model2 = Bpmn.createExecutableProcess(processKey2)
        .startEvent()
        .serviceTask("externalTask2").camundaExternalTask(EXT_TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(processKey2, c7Model2, "tenant1");

    runtimeService.startProcessInstanceByKey(EXT_PROCESS_KEY);
    runtimeService.startProcessInstanceByKey(processKey2);

    // fail both external tasks with 0 retries to create incidents
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(2, EXT_WORKER_ID)
        .topic(EXT_TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(2);
    for (LockedExternalTask task : tasks) {
      externalTaskService.handleFailure(task.getId(), EXT_WORKER_ID, "test error", 0, 0L);
    }

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidentsDefaultTenant = searchHistoricIncidents(EXT_PROCESS_KEY);
    List<IncidentEntity> incidentsTenant1 = searchHistoricIncidents(processKey2);
    assertThat(incidentsDefaultTenant).singleElement()
        .extracting(IncidentEntity::tenantId)
        .isEqualTo(C8_DEFAULT_TENANT);
    assertThat(incidentsTenant1).singleElement()
        .extracting(IncidentEntity::tenantId)
        .isEqualTo("tenant1");
  }

  @Test
  public void shouldMigrateIncidentBasicFieldsForCompletedExternalTask() {
    // given: a process with an external task that fails, then is retried and completed
    deployExternalTaskProcess();
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey(EXT_PROCESS_KEY);

    // lock and fail with 0 retries to create an incident
    LockedExternalTask failedTask = lockAndFailExternalTask();

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // set retries to recover, then complete the task
    recoverAndCompleteExternalTask(failedTask.getId());

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents(EXT_PROCESS_KEY);
    assertThat(incidents).hasSize(1);
    assertOnIncidentBasicFields(incidents.getFirst(), c7Incident, c7ProcessInstance, null, UNKNOWN, false, true);
  }

  @Test
  public void shouldMigrateIncidentForFailedExternalTaskInNestedProcess() {
    // given: a calling process with a user task followed by a call activity to a child with an external task
    deployExternalTaskProcess();
    deployCallingModelForExternalTask(EXT_PROCESS_KEY);

    ProcessInstance parentProcess = runtimeService.startProcessInstanceByKey("callingProcessId");

    // complete the user task to trigger the call activity
    completeAllUserTasksWithDefaultUserTaskId();

    ProcessInstance childProcess = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(EXT_PROCESS_KEY)
        .singleResult();
    assertThat(childProcess).isNotNull();

    // fail the external task with 0 retries to create an incident in the child process
    lockAndFailExternalTask();

    // verify incidents exist in both child and parent
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

    // then: child incident is migrated with jobKey
    List<IncidentEntity> childIncidents = searchHistoricIncidents(EXT_PROCESS_KEY);
    assertThat(childIncidents).as("child process incident should be migrated").hasSize(1);
    assertThat(childIncidents.getFirst().jobKey()).as("child incident should have a job key").isNotNull();

    // and: parent incident is migrated without jobKey (propagated)
    List<IncidentEntity> parentIncidents = searchHistoricIncidents("callingProcessId");
    assertThat(parentIncidents).as("parent process incident should be migrated").hasSize(1);
    assertThat(parentIncidents.getFirst().jobKey()).as("propagated parent incident should have no job key").isNull();
    assertThat(parentIncidents.getFirst().processInstanceKey()).isNotNull();
    assertThat(parentIncidents.getFirst().state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
  }

  @Test
  public void shouldGenerateTreePathForExternalTaskIncidentWithFlowNodeInstanceKey() {
    // given: a process with an external task that fails, is recovered and completed
    deployExternalTaskProcess();
    runtimeService.startProcessInstanceByKey(EXT_PROCESS_KEY);

    // fail with 0 retries to create an incident, then recover and complete
    LockedExternalTask failedTask = lockAndFailExternalTask();
    recoverAndCompleteExternalTask(failedTask.getId());

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(EXT_PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<FlowNodeInstanceDbModel> flowNodes = searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    List<FlowNodeInstanceDbModel> serviceTaskNodes = flowNodes.stream()
        .filter(fn -> EXT_TASK_ID.equals(fn.flowNodeId()))
        .toList();
    assertThat(serviceTaskNodes).hasSize(1);
    Long flowNodeInstanceKey = serviceTaskNodes.getFirst().flowNodeInstanceKey();

    List<IncidentDbModel> incidents = searchIncidentsByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    assertThat(incidents).singleElement()
        .extracting(IncidentDbModel::treePath)
        .isNotNull()
        .isEqualTo("PI_" + processInstanceKey + "/FNI_" + flowNodeInstanceKey);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103")
  public void shouldMigrateMultiInstanceFlowNodeReferenceForExternalTask() {
    // given: a process with a multi-instance external task (cardinality 2)
    String processKey = "miExternalTaskProcess";

    var c7Model = Bpmn.createExecutableProcess(processKey)
        .startEvent()
        .serviceTask(EXT_TASK_ID)
          .camundaExternalTask(EXT_TOPIC_NAME)
          .multiInstance()
            .cardinality("2")
          .multiInstanceDone()
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(processKey, c7Model);
    runtimeService.startProcessInstanceByKey(processKey);

    // fail both external task instances with 0 retries to create incidents
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(2, EXT_WORKER_ID)
        .topic(EXT_TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(2);
    for (LockedExternalTask task : tasks) {
      externalTaskService.handleFailure(task.getId(), EXT_WORKER_ID, "test error", 0, 0L);
    }

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(processKey);
    assertThat(processInstances).hasSize(1);

    List<IncidentEntity> incidents = searchHistoricIncidents(processKey);
    assertThat(incidents).hasSize(2);

    assertThat(incidents.getFirst().flowNodeInstanceKey())
        .isNotEqualTo(incidents.getLast().flowNodeInstanceKey());
  }

  protected void assertOnIncidentBasicFields(IncidentEntity c8Incident,
                                             HistoricIncident c7Incident,
                                             ProcessInstance c7ChildInstance,
                                             ProcessInstance c7ParentInstance) {
    assertOnIncidentBasicFields(c8Incident, c7Incident, c7ChildInstance, c7ParentInstance, UNKNOWN, false, false);
  }

  protected void assertOnIncidentBasicFields(IncidentEntity c8Incident,
                                             HistoricIncident c7Incident,
                                             ProcessInstance c7ChildInstance,
                                             ProcessInstance c7ParentInstance,
                                             IncidentEntity.ErrorType errorType,
                                             boolean waitingExecution,
                                             boolean hasMigratedJob) {
    // specific values
    assertThat(c8Incident.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(c8Incident.processDefinitionId()).isEqualTo(
        prefixDefinitionId(c7ChildInstance.getProcessDefinitionKey()));
    assertThat(c8Incident.flowNodeId()).isEqualTo(c7Incident.getActivityId());
    assertThat(c8Incident.state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
    String expectedIncidentMessage = Objects.toString(c7Incident.getIncidentMessage(), "");
    assertThat(c8Incident.errorMessage()).isEqualTo(expectedIncidentMessage);
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

    if (hasMigratedJob) {
      assertThat(c8Incident.jobKey()).isNotNull();
    } else {
      assertThat(c8Incident.jobKey()).isNull();
    }

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

  protected void deployCallingModelForExternalTask(String calledProcessKey) {
    BpmnModelInstance callingProcess = Bpmn.createExecutableProcess("callingProcessId")
        .startEvent()
        .userTask(USER_TASK_ID)
        .callActivity()
        .calledElement(calledProcessKey)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance("callingProcessId", callingProcess);
  }

  protected void deployExternalTaskProcess() {
    var c7Model = Bpmn.createExecutableProcess(EXT_PROCESS_KEY)
        .startEvent()
        .serviceTask(EXT_TASK_ID).camundaExternalTask(EXT_TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(EXT_PROCESS_KEY, c7Model);
  }

  protected LockedExternalTask lockAndFailExternalTask() {
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, EXT_WORKER_ID)
        .topic(EXT_TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    LockedExternalTask task = tasks.getFirst();
    externalTaskService.handleFailure(task.getId(), EXT_WORKER_ID, "test error", 0, 0L);
    return task;
  }

  protected void recoverAndCompleteExternalTask(String externalTaskId) {
    externalTaskService.setRetries(externalTaskId, 1);
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, EXT_WORKER_ID)
        .topic(EXT_TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.complete(tasks.getFirst().getId(), EXT_WORKER_ID);
  }

}
