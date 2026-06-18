/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.migration.data.qa.history.element.HistoryAbstractElementMigrationTest.PROCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class HistoryJobTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigrateJobsForAsyncBeforeTask() {
    // given: a process with an async-before user task
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    // execute the async-before job to enter the user task
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    String c7JobId = jobs.getFirst().getId();
    managementService.executeJob(c7JobId);

    // when: jobs flow nodes and process instances are migrated
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by job ID across multiple log entries)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 job (deduplication by job ID)").hasSize(1);

    JobEntity job = c8Jobs.getFirst();
    assertJobProperties(job, processInstanceKey, "asyncBeforeUserTaskProcessId", "asyncUserTaskId",
        processInstances.getFirst().processDefinitionKey());
  }

  @Test
  public void shouldMigrateJobsForAsyncAfter() {
    // given: a process with an async-after
    deployModel();
    runtimeService.startProcessInstanceByKey(PROCESS);

    // execute job
    executeAllJobsWithRetry();

    // when
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by job ID across multiple log entries)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 job (deduplication by job ID)").hasSize(1);

    JobEntity job = c8Jobs.getFirst();
    assertJobProperties(job, processInstanceKey, PROCESS, "startEvent", processInstances.getFirst().processDefinitionKey());

    List<FlowNodeInstanceDbModel> startEvent = searchFlowNodeInstancesByName("startEvent");
    assertThat(startEvent).hasSize(1);
    assertThat(job.elementInstanceKey()).isEqualTo(startEvent.getFirst().flowNodeInstanceKey());

    // and: the incident was migrated with jobKey pointing to the C8 job
    var incidents = searchHistoricIncidents(PROCESS);
    assertThat(incidents).hasSize(1);
    assertThat(incidents.getFirst().jobKey())
        .as("Incident's jobKey should reference the migrated job")
        .isNotNull();
    assertThat(incidents.getFirst().jobKey()).isEqualTo(job.jobKey());
  }

  @Test
  public void shouldMigrateJobWithTenant() {
    // given: an async-after start event with a failing downstream task, deployed under a tenant.
    // The async-after job migrates because the start event's HistoricActivityInstance is committed.
    deployModel("tenantId");
    runtimeService.startProcessInstanceByKey(PROCESS);
    executeAllJobsWithRetry();

    // when: full migration runs
    historyMigrator.migrate();

    // then: the migrated job carries the expected tenant
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).as("Exactly one C8 job entry").hasSize(1);
    assertThat(c8Jobs.getFirst().tenantId()).isEqualTo("tenantId");
  }

  @Test
  public void shouldDeduplicateJobsByJobId() {
    // given: an async-after start event whose continuation can't advance to the broken downstream
    // service task — repeated executeJob attempts produce multiple HistoricJobLog entries for
    // the same C7 job ID. The async-after job's activity (the start event) has a committed FNI.
    deployModel();
    runtimeService.startProcessInstanceByKey(PROCESS);

    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    var job = jobs.getFirst();

    // Execute multiple times to create multiple historic job log entries
    for (int i = 0; i < 2; i++) {
      try {
        managementService.executeJob(job.getId());
      } catch (Exception e) {
        // expected
      }
    }

    // Verify multiple log entries exist in C7 for the same job
    var jobLogCount = historyService.createHistoricJobLogQuery().jobId(job.getId()).count();
    assertThat(jobLogCount).as("Should have multiple job log entries").isGreaterThan(1);

    // when (flow nodes must be migrated before jobs since C8 requires non-null elementInstanceKey)
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: only ONE C8 job entry created despite multiple log entries (tracked by job ID)
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).as("Exactly one C8 job per C7 job despite multiple log entries").hasSize(1);
  }

  @Test
  public void shouldMigrateJobsInNestedProcess() {
    // given: a process with an async-after
    deployModel();
    deployCallingModel();
    runtimeService.startProcessInstanceByKey("callingProcessId");

    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> root = searchHistoricProcessInstances("callingProcessId");
    assertThat(root).hasSize(1);
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);

    // and: exactly one C8 job entry was created
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).as("One C8 job entry per C7 job ").hasSize(1);

    assertThat(c8Jobs.getFirst().rootProcessInstanceKey()).isEqualTo(root.getFirst().processInstanceKey());
  }

  @Test
  public void shouldSkipTimerJob() {
    // given
    deployer.deployCamunda7Process("timerDurationBoundaryEventProcess.bpmn");
    ProcessInstance c7instance = runtimeService.startProcessInstanceByKey("timerDurationBoundaryEventProcessId");
    runtimeService.setVariable(c7instance.getId(), "leftoverDuration", "P0D");
    var jobs = managementService.createJobQuery().processInstanceId(c7instance.getId()).list();
    assertThat(jobs).hasSize(1);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("timerDurationBoundaryEventProcessId");
    assertThat(processInstances).hasSize(1);

    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).isEmpty();
  }

  @Test
  public void shouldSkipJobWhenC7HasNoHistoricActivityInstance() {
    // given: an async-before service task that fails on all retry
    // C7 records a HistoricJobLog but no HistoricActivityInstance for the service task
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

    executeAllJobsWithRetry();

    assertThat(historyService.createHistoricJobLogQuery().count())
        .as("C7 should have recorded job log entries for the failing task").isGreaterThan(0);
    assertThat(historyService.createHistoricActivityInstanceQuery().activityId("serviceTaskId").count())
          .as("C7 must not have a HistoricActivityInstance for the failing task").isZero();

    // when: full migration runs
    historyMigrator.migrate();

    // then: the process instance migrated, but the job was skipped because C8 requires
    // non-null elementInstanceKey and no C8 flow-node instance exists for this activity
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(processInstances).hasSize(1);

    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs)
        .as("Job must be skipped when C7 has no HistoricActivityInstance for its activity")
        .isEmpty();
  }

  @Test
  public void shouldSkipAsyncBeforeJobWhenFlowNodeNotMigrated() {
    // given: a process with an async-before user task where flow nodes are NOT migrated
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    // execute the async-before job to enter the user task
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    String c7JobId = jobs.getFirst().getId();
    managementService.executeJob(c7JobId);

    // when: migrate jobs WITHOUT migrating flow nodes
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    // Note: HISTORY_FLOW_NODE is NOT migrated
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: the job is skipped because C8 requires non-null elementInstanceKey
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs)
        .as("Async-before job should be skipped when no flow-node instance exists in C8")
        .isEmpty();
  }

  @Test
  public void shouldSkipAsyncAfterJobWhenFlowNodeNotMigrated() {
    // given: a process with an async-after start event where flow nodes are NOT migrated
    deployModel(); // deploys a process with camundaAsyncAfter() on start event
    runtimeService.startProcessInstanceByKey(PROCESS);
    executeAllJobsWithRetry();

    // when: migrate jobs WITHOUT migrating flow nodes
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    // Note: HISTORY_FLOW_NODE is NOT migrated
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: the job is skipped because C8 requires non-null elementInstanceKey
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);

    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs)
        .as("Async-after job should be skipped when no flow-node instance exists in C8")
        .isEmpty();
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103")
  public void shouldMigrateMultiInstanceFlowNodeReference() {
    // given
    deployer.deployCamunda7Process("miProcess-async.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("miProcess");

    var jobs = managementService.createJobQuery().processInstanceId(c7ProcessInstance.getId()).list();
    jobs.forEach(j -> managementService.executeJob(j.getId()));

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("miProcess");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs.getFirst().elementInstanceKey()).isNotEqualTo(c8Jobs.getLast().elementInstanceKey());
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103")
  public void shouldMigrateMultiInstanceFlowNodeReferenceSubprocess() {
    // given
    deployer.deployCamunda7Process("miProcess-subprocess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("miProcess");

    var jobs = managementService.createJobQuery().processInstanceId(c7ProcessInstance.getId()).list();
    jobs.forEach(j -> managementService.executeJob(j.getId()));

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("miProcess");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs.getFirst().elementInstanceKey()).isNotEqualTo(c8Jobs.getLast().elementInstanceKey());
  }

  protected void assertJobProperties(JobEntity job, long processInstanceKey, String c7ProcessDefinitionKey,
                                     String taskId, Long processDefinitionKey) {
    assertThat(job.jobKey()).isNotNull();
    assertThat(job.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.rootProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.elementInstanceKey()).isNotNull();

    assertThat(job.type()).isEqualTo("async-continuation");
    assertThat(job.worker()).isNotNull();
    assertThat(job.state()).isEqualTo(JobState.COMPLETED);
    assertThat(job.kind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(job.listenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(job.retries()).isEqualTo(0);
    assertThat(job.elementId()).isEqualTo(taskId);
    assertThat(job.processDefinitionId()).isEqualTo(prefixDefinitionId(c7ProcessDefinitionKey));
    assertThat(job.tenantId()).isEqualTo(C8_DEFAULT_TENANT);

    assertThat(job.creationTime()).isNotNull();
    assertThat(job.deadline()).isNull();
    assertThat(job.deniedReason()).isNull();
    assertThat(job.endTime()).isNull();
    assertThat(job.errorCode()).isNull();
    assertThat(job.errorMessage()).isNull();
    assertThat(job.hasFailedWithRetriesLeft()).isFalse();
    // returns `false` in 8.9; returns `null` in 8.10
    assertThat(job.isDenied()).isIn(null, false);
    assertThat(job.lastUpdateTime())
        .isNotNull()
        .isAfterOrEqualTo(job.creationTime());
  }

  protected void deployModel() {
    deployer.deployC7ModelInstance(PROCESS, buildAsyncAfterFailingModel());
  }

  protected void deployModel(String tenantId) {
    deployer.deployC7ModelInstance(PROCESS, buildAsyncAfterFailingModel(), tenantId);
  }

  private BpmnModelInstance buildAsyncAfterFailingModel() {
    return Bpmn.createExecutableProcess(PROCESS)
        .startEvent("startEvent")
        .camundaAsyncAfter()
        .serviceTask("serviceTaskId")
          .camundaClass("foo")
        .endEvent()
        .done();
  }

  protected void deployCallingModel() {
    BpmnModelInstance c7BusinessRuleProcess = Bpmn.createExecutableProcess("callingProcessId")
        .startEvent()
        .userTask(USER_TASK_ID)
        .callActivity()
        .calledElement(PROCESS)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance("callingProcessId", c7BusinessRuleProcess);
  }
}
