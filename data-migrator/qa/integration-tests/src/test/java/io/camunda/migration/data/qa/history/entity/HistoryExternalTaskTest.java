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
import static io.camunda.search.entities.JobEntity.JobKind.BPMN_ELEMENT;
import static io.camunda.search.entities.JobEntity.JobState.COMPLETED;
import static io.camunda.search.entities.JobEntity.ListenerEventType.UNSPECIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.JobQuery;
import java.util.List;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryExternalTaskTest extends HistoryMigrationAbstractTest {

  private static final String EXTERNAL_TASK_PROCESS_ID = "externalTaskProcessId";
  private static final String WORKER_ID = "testWorker";
  private static final String TOPIC_NAME = "test-topic";

  @Autowired
  private ExternalTaskService externalTaskService;

  @Test
  public void shouldMigrateCompletedExternalTask() {
    // given
    deployer.deployCamunda7Process("externalTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey(EXTERNAL_TASK_PROCESS_ID);

    List<LockedExternalTask> tasks = externalTaskService
        .fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10_000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.complete(tasks.getFirst().getId(), WORKER_ID);

    // when
    historyMigrator.migrate();

    // then
    List<JobEntity> jobs = searchHistoricJobs();
    assertThat(jobs).singleElement()
        .satisfies(job -> {
          assertThat(job.jobKey()).isNotNull();
          assertThat(job.type()).isEqualTo(TOPIC_NAME);
          assertThat(job.worker()).isEqualTo(WORKER_ID);
          assertThat(job.state()).isEqualTo(COMPLETED);
          assertThat(job.kind()).isEqualTo(BPMN_ELEMENT);
          assertThat(job.listenerEventType()).isEqualTo(UNSPECIFIED);
          assertThat(job.processDefinitionId()).isEqualTo(prefixDefinitionId(EXTERNAL_TASK_PROCESS_ID));
          assertThat(job.processInstanceKey()).isNotNull();
          assertThat(job.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
        });
  }

  @Test
  public void shouldMigrateExternalTaskOnce() {
    // given - a process with multiple log entries per external task (creation + success)
    deployer.deployCamunda7Process("externalTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey(EXTERNAL_TASK_PROCESS_ID);

    List<LockedExternalTask> tasks = externalTaskService
        .fetchAndLock(1, WORKER_ID)
        .topic(TOPIC_NAME, 10_000L)
        .execute();
    assertThat(tasks).hasSize(1);
    externalTaskService.complete(tasks.getFirst().getId(), WORKER_ID);

    // verify there are multiple log entries for the same external task
    long logCount = historyService.createHistoricExternalTaskLogQuery()
        .externalTaskId(tasks.getFirst().getId())
        .count();
    assertThat(logCount).isGreaterThanOrEqualTo(2);

    // when
    historyMigrator.migrate();

    // then - only one job should be created per external task, not one per log entry
    assertThat(searchHistoricJobs()).hasSize(1);
  }

  protected List<JobEntity> searchHistoricJobs() {
    List<Long> processInstanceKeys = searchHistoricProcessInstances(EXTERNAL_TASK_PROCESS_ID)
        .stream()
        .map(pi -> pi.processInstanceKey())
        .toList();

    if (processInstanceKeys.isEmpty()) {
      return List.of();
    }

    return rdbmsService.getJobReader()
        .search(JobQuery.of(q -> q.filter(f -> f.processInstanceKeys(processInstanceKeys.getFirst()))))
        .items();
  }
}
