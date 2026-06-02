/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the synthetic FlowNodeInstance (created when no HistoricActivityInstance exists in
 * C7 due to async-before transaction rollback) and the associated Job and Incident entities are all
 * searchable via the C8 search entity API without triggering requireNonNull violations.
 *
 * <p>This test complements {@code NullabilityContractTest} by asserting searchability of all three
 * entities produced by the synthetic FNI path.
 */
public class SyntheticFlowNodeInstanceSearchTest extends HistoryMigrationAbstractTest {

  /**
   * When an async-before service task fails on every retry (e.g., R0/PT0S), C7 rolls back the
   * HistoricActivityInstance. The migrator creates a synthetic FlowNodeInstance. This test verifies
   * that the synthetic FNI can be read via the FlowNodeInstanceEntity search API (which enforces
   * requireNonNull in its compact constructor).
   */
  @Test
  public void shouldSearchSyntheticFlowNodeInstanceViaSearchApi() {
    // given: an async-before failing service task that produces an incident
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");
    executeAllJobsWithRetry();

    // when: full migration (creates synthetic FNI for the service task)
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: the synthetic flow node instance is searchable via the entity search API
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes)
        .as("Synthetic FlowNodeInstance should be searchable via the C8 search API")
        .isNotEmpty();
    assertThat(flowNodes)
        .anyMatch(fn -> "serviceTaskId".equals(fn.flowNodeId()));
  }

  /**
   * Verifies that the Job entity (with elementInstanceKey populated from the synthetic FNI) is
   * searchable via the JobEntity search API without triggering requireNonNull violations.
   */
  @Test
  public void shouldSearchJobWithSyntheticElementInstanceKeyViaSearchApi() {
    // given: an async-before failing service task
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");
    executeAllJobsWithRetry();

    // when: full migration
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: the job is searchable via the JobEntity search API
    List<JobEntity> jobs = jobReader.search(
        JobQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)))).items();

    assertThat(jobs)
        .as("Job should be searchable via the C8 search API")
        .hasSize(1);
    assertThat(jobs.getFirst().elementInstanceKey())
        .as("JobEntity.elementInstanceKey — populated from synthetic FNI, must not be null")
        .isNotNull()
        .isGreaterThan(0L);
  }

  /**
   * Verifies that the Incident entity (with flowNodeInstanceKey populated from the synthetic FNI)
   * is searchable via the IncidentEntity search API without triggering requireNonNull violations.
   */
  @Test
  public void shouldSearchIncidentWithSyntheticFlowNodeInstanceKeyViaSearchApi() {
    // given: an async-before failing service task that produces an incident
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance c7ProcessInstance =
        runtimeService.startProcessInstanceByKey("incidentProcessId");
    triggerIncident(c7ProcessInstance.getId());

    // when: full migration (creates synthetic FNI for the incident's activity)
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("incidentProcessId");
    assertThat(processInstances).hasSize(1);

    // and: the incident is searchable via the IncidentEntity search API
    List<IncidentEntity> incidents = incidentReader.search(
        IncidentQuery.of(b -> b.filter(f -> f.processDefinitionIds(
            prefixDefinitionId("incidentProcessId"))))).items();

    assertThat(incidents)
        .as("Incident should be searchable via the C8 search API")
        .hasSize(1);
    assertThat(incidents.getFirst().flowNodeInstanceKey())
        .as("IncidentEntity.flowNodeInstanceKey — populated from synthetic FNI, must not be null")
        .isNotNull()
        .isGreaterThan(0L);
  }

  /**
   * Combined test: verifies that all three entities (synthetic FlowNodeInstance, Job, and Incident)
   * are searchable via the C8 search entity API in a single migration scenario.
   */
  @Test
  public void shouldSearchAllThreeEntitiesViaSearchApiForSyntheticFniScenario() {
    // given: an async-before failing service task that produces both a job and an incident
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    ProcessInstance c7ProcessInstance =
        runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");
    executeAllJobsWithRetry();

    assertThat(historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId()).count())
        .as("Expected an incident to be created")
        .isEqualTo(1);

    // when: full migration
    historyMigrator.migrate();

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // 1. Synthetic FlowNodeInstance is searchable
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes)
        .as("Synthetic FlowNodeInstance should be searchable via the C8 search API")
        .isNotEmpty();
    FlowNodeInstanceEntity syntheticFni = flowNodes.stream()
        .filter(fn -> "serviceTaskId".equals(fn.flowNodeId()))
        .findFirst()
        .orElse(null);
    assertThat(syntheticFni)
        .as("Synthetic FNI for serviceTaskId should exist")
        .isNotNull();

    // 2. Job is searchable and references the synthetic FNI
    List<JobEntity> jobs = jobReader.search(
        JobQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)))).items();
    assertThat(jobs)
        .as("Job should be searchable via the C8 search API")
        .hasSize(1);
    assertThat(jobs.getFirst().elementInstanceKey())
        .as("Job.elementInstanceKey should reference the synthetic FNI key")
        .isEqualTo(syntheticFni.flowNodeInstanceKey());

    // 3. Incident is searchable and references the synthetic FNI
    List<IncidentEntity> incidents = incidentReader.search(
        IncidentQuery.of(b -> b.filter(f -> f.processDefinitionIds(
            prefixDefinitionId("failingServiceTaskProcessId"))))).items();
    assertThat(incidents)
        .as("Incident should be searchable via the C8 search API")
        .hasSize(1);
    assertThat(incidents.getFirst().flowNodeInstanceKey())
        .as("Incident.flowNodeInstanceKey should reference the synthetic FNI key")
        .isEqualTo(syntheticFni.flowNodeInstanceKey());
  }
}
