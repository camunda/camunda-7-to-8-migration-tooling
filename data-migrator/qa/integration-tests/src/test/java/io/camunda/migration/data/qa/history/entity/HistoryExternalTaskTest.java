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
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for migrating Camunda 7 historic external task logs to Camunda 8 job records.
 */
public class HistoryExternalTaskTest extends HistoryMigrationAbstractTest {

  private static final String WORKER_ID = "test-worker";
  private static final String TOPIC = "test-topic";
  private static final long LOCK_DURATION = 60_000L;

  @Autowired
  protected ExternalTaskService externalTaskService;

  @Test
  public void shouldMigrateCompletedExternalTaskLog() {
    // given
    deployer.deployCamunda7Process("externalTaskProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("externalTaskProcessId");

    List<LockedExternalTask> lockedTasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC, LOCK_DURATION)
        .execute();
    assertThat(lockedTasks).hasSize(1);
    externalTaskService.complete(lockedTasks.getFirst().getId(), WORKER_ID);

    List<ProcessInstanceEntity> processInstances = null;

    // when
    historyMigrator.migrate();

    // then
    processInstances = searchHistoricProcessInstances("externalTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<JobEntity> jobs = searchHistoricJobs(processInstanceKey);
    // Should have creation log + completion log = 2 entries
    assertThat(jobs).hasSizeGreaterThanOrEqualTo(1);

    // Verify completed job fields
    List<JobEntity> completedJobs = jobs.stream()
        .filter(j -> JobState.COMPLETED.equals(j.state()))
        .toList();
    assertThat(completedJobs).hasSize(1);
    JobEntity completedJob = completedJobs.getFirst();

    assertThat(completedJob.jobKey()).isNotNull();
    assertThat(completedJob.type()).isEqualTo(TOPIC);
    assertThat(completedJob.worker()).isEqualTo(WORKER_ID);
    assertThat(completedJob.kind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(completedJob.listenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(completedJob.elementId()).isEqualTo("externalTaskId");
    assertThat(completedJob.processDefinitionId()).isEqualTo(prefixDefinitionId("externalTaskProcessId"));
    assertThat(completedJob.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(completedJob.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(completedJob.endTime()).isNotNull();
    assertThat(completedJob.partitionId()).isNotNull();
  }

  @Test
  public void shouldMigrateFailedExternalTaskLog() {
    // given
    deployer.deployCamunda7Process("externalTaskProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("externalTaskProcessId");

    List<LockedExternalTask> lockedTasks = externalTaskService.fetchAndLock(1, WORKER_ID)
        .topic(TOPIC, LOCK_DURATION)
        .execute();
    assertThat(lockedTasks).hasSize(1);
    String externalTaskId = lockedTasks.getFirst().getId();
    externalTaskService.handleFailure(externalTaskId, WORKER_ID, "Test error message", 2, 0L);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("externalTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    List<JobEntity> jobs = searchHistoricJobs(processInstanceKey);
    assertThat(jobs).hasSizeGreaterThanOrEqualTo(1);

    // Verify failed job fields
    List<JobEntity> failedJobs = jobs.stream()
        .filter(j -> JobState.FAILED.equals(j.state()))
        .toList();
    assertThat(failedJobs).hasSize(1);
    JobEntity failedJob = failedJobs.getFirst();

    assertThat(failedJob.jobKey()).isNotNull();
    assertThat(failedJob.type()).isEqualTo(TOPIC);
    assertThat(failedJob.worker()).isEqualTo(WORKER_ID);
    assertThat(failedJob.kind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(failedJob.listenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(failedJob.elementId()).isEqualTo("externalTaskId");
    assertThat(failedJob.errorMessage()).isEqualTo("Test error message");
    assertThat(failedJob.retries()).isEqualTo(2);
    assertThat(failedJob.hasFailedWithRetriesLeft()).isTrue();
    assertThat(failedJob.state()).isEqualTo(JobState.FAILED);
    assertThat(failedJob.endTime()).isNotNull();
  }

}
