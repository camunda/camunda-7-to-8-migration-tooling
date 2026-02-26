/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
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

    // when: jobs and process instances are migrated
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: exactly one C8 job entry was created (deduplicated by job ID across multiple log entries)
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 job (deduplication by job ID)").hasSize(1);

    JobEntity job = c8Jobs.getFirst();
    assertThat(job.jobKey()).isNotNull();
    assertThat(job.kind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(job.listenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(job.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(job.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.processDefinitionKey()).isNotNull();
    assertThat(job.elementId()).isEqualTo("asyncUserTaskId");

    // and: the job is tracked in the migration table by C7 job ID
    assertThat(dbClient.checkExistsByC7IdAndType(c7JobId, HISTORY_JOB)).isTrue();
  }

  @Test
  public void shouldMigrateFailedJobAndPopulateJobKeyOnIncident() {
    // given: a failing service task that creates an incident
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    var job = jobs.getFirst();

    // Execute the job to cause it to fail and create an incident
    for (int i = 0; i < 3; i++) {
      try {
        managementService.executeJob(job.getId());
      } catch (Exception e) {
        // expected - job will fail
      }
    }
    assertThat(historyService.createHistoricIncidentQuery().count())
        .as("Expected one incident to be created").isEqualTo(1);

    // when: full migration runs (jobs migrated before incidents)
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: the failed job was migrated to C8
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("Exactly one C8 job entry (deduplication by job ID)").hasSize(1);
    JobEntity c8Job = c8Jobs.getFirst();
    assertThat(c8Job.elementId()).isEqualTo("serviceTaskId");
    assertThat(c8Job.tenantId()).isEqualTo(C8_DEFAULT_TENANT);

    // and: the incident was migrated with jobKey pointing to the C8 job
    var incidents = searchHistoricIncidents("failingServiceTaskProcessId");
    assertThat(incidents).hasSize(1);
    assertThat(incidents.getFirst().jobKey())
        .as("Incident's jobKey should reference the migrated job")
        .isNotNull();
    assertThat(incidents.getFirst().jobKey()).isEqualTo(c8Job.jobKey());
  }

  @Test
  public void shouldDeduplicateJobsByJobId() {
    // given: a failing service task with multiple job log entries (creation + multiple failures)
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

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

    // when
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: only ONE C8 job entry created despite multiple log entries (tracked by job ID)
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(processInstances).hasSize(1);
    List<JobEntity> c8Jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(c8Jobs).as("Exactly one C8 job per C7 job despite multiple log entries").hasSize(1);

    // and: the job is tracked in the migration table by C7 job ID
    assertThat(dbClient.checkExistsByC7IdAndType(job.getId(), HISTORY_JOB)).isTrue();
  }
}
