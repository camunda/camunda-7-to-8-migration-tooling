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
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryExternalTaskTest extends HistoryMigrationAbstractTest {

  private static final String EXTERNAL_TASK_PROCESS = "externalTaskProcess";
  private static final String EXTERNAL_TASK_ID = "externalTaskId";
  private static final String EXTERNAL_TASK_TOPIC = "myExternalTaskTopic";
  private static final String WORKER_ID = "testWorker";

  @Autowired
  private ExternalTaskService externalTaskService;

  @Test
  public void shouldMigrateCompletedExternalTask() {
    // given: a process with a completed external task
    deployExternalTaskProcess();
    runtimeService.startProcessInstanceByKey(EXTERNAL_TASK_PROCESS);

    // lock and complete the external task
    List<LockedExternalTask> locked = lockExternalTask();
    assertThat(locked).hasSize(1);
    String c7ExternalTaskId = locked.getFirst().getId();
    externalTaskService.complete(c7ExternalTaskId, WORKER_ID);

    // when: external tasks and process instances are migrated
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_EXTERNAL_TASK);

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(EXTERNAL_TASK_PROCESS);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by external task ID)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs)
        .as("One C8 job entry per C7 external task (deduplication by external task ID)")
        .hasSize(1);

    JobEntity job = c8Jobs.getFirst();
    assertExternalTaskJobProperties(job, processInstanceKey, processInstances.getFirst().processDefinitionKey());
  }

  @Test
  public void shouldDeduplicateExternalTasksByExternalTaskId() {
    // given: a process with an external task that has multiple log entries (lock+failure+success)
    deployExternalTaskProcess();
    runtimeService.startProcessInstanceByKey(EXTERNAL_TASK_PROCESS);

    // Lock and fail the external task (creates failure log entry)
    List<LockedExternalTask> locked = lockExternalTask();
    assertThat(locked).hasSize(1);
    String c7ExternalTaskId = locked.getFirst().getId();
    externalTaskService.handleFailure(c7ExternalTaskId, WORKER_ID, "temporary error", 2, 0);

    // Lock and complete (creates success log entry)
    locked = lockExternalTask();
    assertThat(locked).hasSize(1);
    externalTaskService.complete(locked.getFirst().getId(), WORKER_ID);

    // Verify multiple log entries exist in C7
    long logCount = historyService.createHistoricExternalTaskLogQuery()
        .externalTaskId(c7ExternalTaskId).count();
    assertThat(logCount).as("Should have multiple external task log entries").isGreaterThan(1);

    // when
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_EXTERNAL_TASK);

    // then: only ONE C8 job entry created despite multiple log entries (tracked by external task ID)
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(EXTERNAL_TASK_PROCESS);
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs)
        .as("Exactly one C8 job per C7 external task despite multiple log entries")
        .hasSize(1);
  }

  @Test
  public void shouldMigrateExternalTaskWithTenant() {
    // given: a process deployed with a tenant
    deployer.deployCamunda7Process("serviceTaskProcess.bpmn", "tenantId");
    runtimeService.startProcessInstanceByKey("serviceTaskProcessId");

    // lock and complete the external task (topic "${true}" evaluates to "true")
    List<LockedExternalTask> locked = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic("true", Long.MAX_VALUE)
        .execute();
    assertThat(locked).hasSize(1);
    externalTaskService.complete(locked.getFirst().getId(), WORKER_ID);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("serviceTaskProcessId");
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).hasSize(1);
    assertThat(c8Jobs.getFirst().tenantId()).isEqualTo("tenantId");
  }

  @Test
  public void shouldMigrateExternalTaskIncidentAndPopulateJobKey() {
    // given: a process with an external task that fails all retries, creating an incident
    deployExternalTaskProcess();
    runtimeService.startProcessInstanceByKey(EXTERNAL_TASK_PROCESS);

    // Lock and fail with 0 retries to create an incident
    List<LockedExternalTask> locked = lockExternalTask();
    assertThat(locked).hasSize(1);
    String c7ExternalTaskId = locked.getFirst().getId();
    externalTaskService.handleFailure(c7ExternalTaskId, WORKER_ID, "Unrecoverable error", 0, 0);

    // Verify incident was created
    assertThat(historyService.createHistoricIncidentQuery().count())
        .as("Expected one incident to be created").isEqualTo(1);

    // when: full migration runs (external tasks migrated before incidents)
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(EXTERNAL_TASK_PROCESS);
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: the external task was migrated to C8 as a job
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("Exactly one C8 job for the external task").hasSize(1);
    JobEntity job = c8Jobs.getFirst();

    // and: the incident was migrated with jobKey pointing to the C8 job
    var incidents = searchHistoricIncidents(EXTERNAL_TASK_PROCESS);
    assertThat(incidents).hasSize(1);
    assertThat(incidents.getFirst().jobKey())
        .as("Incident's jobKey should reference the migrated external task job")
        .isNotNull();
    assertThat(incidents.getFirst().jobKey()).isEqualTo(job.jobKey());
  }

  protected List<LockedExternalTask> lockExternalTask() {
    return externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(EXTERNAL_TASK_TOPIC, Long.MAX_VALUE)
        .execute();
  }

  protected void deployExternalTaskProcess() {
    BpmnModelInstance model = Bpmn.createExecutableProcess(EXTERNAL_TASK_PROCESS)
        .startEvent()
        .serviceTask(EXTERNAL_TASK_ID)
          .camundaExternalTask(EXTERNAL_TASK_TOPIC)
        .endEvent()
        .done();
    deployer.deployC7ModelInstance(EXTERNAL_TASK_PROCESS, model);
  }

  protected void assertExternalTaskJobProperties(JobEntity job, long processInstanceKey,
                                                  Long processDefinitionKey) {
    assertThat(job.jobKey()).isNotNull();
    assertThat(job.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.rootProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.type()).isEqualTo(EXTERNAL_TASK_TOPIC);
    assertThat(job.worker()).isEqualTo(WORKER_ID);
    assertThat(job.state()).isEqualTo(JobState.COMPLETED);
    assertThat(job.kind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(job.listenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(job.retries()).isEqualTo(0);
    assertThat(job.elementId()).isEqualTo(EXTERNAL_TASK_ID);
    assertThat(job.processDefinitionId()).isEqualTo(prefixDefinitionId(EXTERNAL_TASK_PROCESS));
    assertThat(job.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(job.creationTime()).isNotNull();
  }
}
