/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_EXTERNAL_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryExternalTaskTest extends HistoryMigrationAbstractTest {

  private static final String PROCESS_KEY = "externalTaskProcess";
  private static final String TASK_ID = "externalTask";
  private static final String TOPIC_NAME = "myTopic";
  private static final String WORKER_ID = "myWorker";

  @Autowired
  protected ExternalTaskService externalTaskService;

  @Test
  public void shouldMigrateExternalTask() {
    // given: a process with an external task
    var c7Model = Bpmn.createExecutableProcess(PROCESS_KEY)
        .startEvent()
        .serviceTask(TASK_ID)
          .camundaExternalTask(TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(PROCESS_KEY, c7Model);
    runtimeService.startProcessInstanceByKey(PROCESS_KEY);

    // lock and complete the external task
//    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, WORKER_ID)
//        .topic(TOPIC_NAME, 10000L)
//        .execute();
//    assertThat(tasks).hasSize(1);
//    externalTaskService.complete(tasks.getFirst().getId(), WORKER_ID);

    // verify that history has been recorded
    assertThat(historyService.createHistoricExternalTaskLogQuery().count()).isGreaterThan(0);

    // when: migration runs
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_EXTERNAL_TASK);

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by external task ID)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 external task (deduplication by external task ID)").hasSize(1);

    // and: the job has the expected properties
    JobEntity job = c8Jobs.getFirst();
    assertExternalTaskJobProperties(job, processInstanceKey, processInstances.getFirst().processDefinitionKey(),
        null);
  }

  @Test
  public void shouldMigrateCompletedExternalTask() {
    // given: a process with an external task
    var c7Model = Bpmn.createExecutableProcess(PROCESS_KEY)
        .startEvent()
        .serviceTask(TASK_ID)
          .camundaExternalTask(TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(PROCESS_KEY, c7Model);
    runtimeService.startProcessInstanceByKey(PROCESS_KEY);

    // lock and complete the external task
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.complete(tasks.getFirst().getId(), WORKER_ID);

    // verify that history has been recorded
    assertThat(historyService.createHistoricExternalTaskLogQuery().count()).isGreaterThan(0);

    // when: migration runs
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_EXTERNAL_TASK);

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by external task ID)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 external task (deduplication by external task ID)").hasSize(1);

    // and: the job has the expected properties
    JobEntity job = c8Jobs.getFirst();
    assertExternalTaskJobProperties(job, processInstanceKey, processInstances.getFirst().processDefinitionKey(),
        WORKER_ID);
  }

  @Test
  public void shouldDeduplicateExternalTasksByExternalTaskId() {
    // given: a process with an external task that is retried (multiple log entries)
    var c7Model = Bpmn.createExecutableProcess(PROCESS_KEY)
        .startEvent()
        .serviceTask(TASK_ID)
          .camundaExternalTask(TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(PROCESS_KEY, c7Model);
    runtimeService.startProcessInstanceByKey(PROCESS_KEY);

    // lock, report failure, and complete - creating multiple log entries for the same task
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    String externalTaskId = tasks.getFirst().getId();

    // Report failure to create a failure log entry
    externalTaskService.handleFailure(externalTaskId, WORKER_ID, "test error", 2, 0L);

    // Re-fetch and complete
    tasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.complete(tasks.getFirst().getId(), WORKER_ID);

    // Verify multiple log entries exist for the same external task
    long logCount = historyService.createHistoricExternalTaskLogQuery()
        .externalTaskId(externalTaskId)
        .count();
    assertThat(logCount).as("Should have multiple log entries for the same external task").isGreaterThan(1);

    // when: migration runs
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_EXTERNAL_TASK);

    // then: only ONE C8 job entry created despite multiple log entries
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).as("Exactly one C8 job per C7 external task despite multiple log entries").hasSize(1);
  }

  @Test
  public void shouldMigrateExternalTaskWithTenant() {
    // given: a process with an external task and a tenant
    var c7Model = Bpmn.createExecutableProcess(PROCESS_KEY)
        .startEvent()
        .serviceTask(TASK_ID)
          .camundaExternalTask(TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(PROCESS_KEY, c7Model, "tenantId");
    runtimeService.startProcessInstanceByKey(PROCESS_KEY);

    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.complete(tasks.getFirst().getId(), WORKER_ID);

    // when: full migration runs
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).hasSize(1);
    assertThat(c8Jobs.getFirst().tenantId()).isEqualTo("tenantId");
  }

  @Test
  public void shouldMigrateFailedExternalTaskAndPopulateJobKeyOnIncident() {
    // given: a process with an external task that creates an incident (retries exhausted)
    var c7Model = Bpmn.createExecutableProcess(PROCESS_KEY)
        .startEvent()
        .serviceTask(TASK_ID)
          .camundaExternalTask(TOPIC_NAME)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(PROCESS_KEY, c7Model);
    runtimeService.startProcessInstanceByKey(PROCESS_KEY);

    // lock and report failure with 0 retries to create an incident
    List<LockedExternalTask> tasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.handleFailure(tasks.getFirst().getId(), WORKER_ID, "test error", 0, 0L);

    // verify incident was created
    assertThat(historyService.createHistoricIncidentQuery().count())
        .as("Expected one incident to be created").isEqualTo(1);

    // when: full migration runs (external tasks migrated before incidents)
    historyMigrator.migrate();

    // then: the external task was migrated to C8
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS_KEY);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("Exactly one C8 job entry").hasSize(1);
    JobEntity job = c8Jobs.getFirst();

    // and: the incident was migrated with jobKey pointing to the C8 job
    var incidents = searchHistoricIncidents(PROCESS_KEY);
    assertThat(incidents).hasSize(1);
    assertThat(incidents.getFirst().jobKey())
        .as("Incident's jobKey should reference the migrated external task job")
        .isNotNull();
    assertThat(incidents.getFirst().jobKey()).isEqualTo(job.jobKey());
  }

  protected void assertExternalTaskJobProperties(JobEntity job, long processInstanceKey, Long processDefinitionKey,
                                                 String worker) {
    assertThat(job.jobKey()).isNotNull();
    assertThat(job.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.rootProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.type()).isEqualTo(TOPIC_NAME);
    assertThat(job.worker()).isEqualTo(worker);
    assertThat(job.state()).isEqualTo(JobState.COMPLETED);
    assertThat(job.kind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(job.listenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(job.retries()).isEqualTo(0);
    assertThat(job.elementId()).isEqualTo(TASK_ID);
    assertThat(job.processDefinitionId()).isEqualTo(prefixDefinitionId(PROCESS_KEY));
    assertThat(job.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
  }
}
