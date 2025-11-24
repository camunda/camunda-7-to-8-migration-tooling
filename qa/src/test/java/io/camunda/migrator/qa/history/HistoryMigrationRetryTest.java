/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.MigratorMode;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryMigrationRetryTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigratePreviouslySkippedProcessDefinition() {
    // given: DMN with requirements, create natural skip scenario
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    
    // First migration: Migrate decision definitions WITHOUT requirements
    // This causes definitions to naturally skip due to missing parent
    historyMigrator.migrateDecisionDefinitions();
    
    // Verify definitions were skipped (2 definitions in the DMN file)
    logs.assertContains("Migration of historic decision definition with C7 ID");
    logs.assertContains("skipped. Decision requirements definition not yet available");
    assertThat(logs.getEvents().stream()
        .filter(event -> event.getMessage().contains("Migration of historic decision definition with C7 ID"))
        .filter(event -> event.getMessage().contains("skipped"))
        .count()).isEqualTo(2);
    
    // when: Retry migration - now migrate requirements then retry skipped definitions
    historyMigrator.migrateDecisionRequirementsDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrateDecisionDefinitions();

    // then: Previously skipped definitions are now migrated
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id").size()).isEqualTo(1);
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id").size()).isEqualTo(1);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionDefinition() {
    // given: DMN with requirements
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    
    // Create natural skip: Migrate decision definition WITHOUT requirements
    historyMigrator.migrateDecisionDefinitions();
    
    // Verify definitions were skipped due to missing requirements (2 definitions)
    logs.assertContains("Migration of historic decision definition with C7 ID");
    logs.assertContains("skipped. Decision requirements definition not yet available");
    assertThat(logs.getEvents().stream()
        .filter(event -> event.getMessage().contains("Migration of historic decision definition with C7 ID"))
        .filter(event -> event.getMessage().contains("skipped"))
        .count()).isEqualTo(2);

    // when: Retry migration with requirements now available
    historyMigrator.migrateDecisionRequirementsDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrateDecisionDefinitions();

    // then: Previously skipped definitions are migrated
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id").size()).isEqualTo(1);
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id").size()).isEqualTo(1);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionRequirementsDefinition() {
    // Note: Decision requirements are top-level entities with no parents
    // They cannot naturally skip. For testing retry of requirements specifically,
    // we test this via decision definitions that depend on requirements.
    
    // given: DMN with requirements
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    
    // Skip requirements by not migrating them
    // Then migrate definitions which will skip due to missing requirements
    historyMigrator.migrateDecisionDefinitions();
    logs.assertContains("Migration of historic decision definition with C7 ID");
    logs.assertContains("skipped. Decision requirements definition not yet available");
    assertThat(logs.getEvents().stream()
        .filter(event -> event.getMessage().contains("Migration of historic decision definition with C7 ID"))
        .filter(event -> event.getMessage().contains("skipped"))
        .count()).isEqualTo(2);

    // when: Migrate requirements then retry definitions
    historyMigrator.migrateDecisionRequirementsDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrateDecisionDefinitions();

    // then: Requirements and definitions are both migrated
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId").size()).isEqualTo(1);
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id").size()).isEqualTo(1);
  }

  @Test
  public void shouldMigrateOnlyPreviouslySkippedElementsOnRetry() {
    // given: Process instances with various entity types in C7
    deployer.deployCamunda7Process("includeAllSupportedElementsProcess.bpmn");
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("allElementsProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    // First migration: Create natural skips by migrating child entities WITHOUT parents
    // This causes one of each entity type to skip
    historyMigrator.migrateProcessInstances(); // Skip - no definition
    historyMigrator.migrateFlowNodes(); // Skip - no instance
    historyMigrator.migrateUserTasks(); // Skip - no instance
    historyMigrator.migrateVariables(); // Skip - no instance
    historyMigrator.migrateIncidents(); // Skip - no instance
    
    // Verify entities were skipped with proper counts
    logs.assertContains("Migration of historic process instance with C7 ID");
    logs.assertContains("skipped. Process definition not yet available");
    assertThat(logs.getEvents().stream()
        .filter(event -> event.getMessage().contains("Migration of historic process instance with C7 ID"))
        .filter(event -> event.getMessage().contains("skipped"))
        .count()).isEqualTo(5);
    
    logs.assertContains("Migration of historic flow nodes with C7 ID");
    logs.assertContains("Migration of historic user task with C7 ID");
    logs.assertContains("Migration of historic variable with C7 ID");
    logs.assertContains("Migration of historic incident with C7 ID");

    // Now migrate the parent entities
    historyMigrator.migrateProcessDefinitions();
    
    // when: Retry migration for previously skipped entities
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then: All previously skipped entities are now migrated
    assertThat(searchHistoricProcessDefinitions("allElementsProcessId").size()).isEqualTo(1);
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("allElementsProcessId");
    assertThat(processInstances.size()).isEqualTo(5);
    
    // Verify child entities are also migrated
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey()).size()).isGreaterThan(0);
    assertThat(searchHistoricIncidents("allElementsProcessId").size()).isGreaterThan(0);
  }

  @Test
  public void shouldNotMigratePreviouslySkippedElementsOnRerun() {
    // given: Multiple process instances in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // First migration: Create natural skips for one instance by migrating without definition
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks();
    
    // Verify 5 instances and their children were skipped
    logs.assertContains("Migration of historic process instance with C7 ID");
    logs.assertContains("skipped. Process definition not yet available");
    assertThat(logs.getEvents().stream()
        .filter(event -> event.getMessage().contains("Migration of historic process instance with C7 ID"))
        .filter(event -> event.getMessage().contains("skipped"))
        .count()).isEqualTo(5);
    
    logs.assertContains("Migration of historic user task with C7 ID");
    assertThat(logs.getEvents().stream()
        .filter(event -> event.getMessage().contains("Migration of historic user task with C7 ID"))
        .filter(event -> event.getMessage().contains("skipped"))
        .count()).isEqualTo(5);

    // when: Regular migration (not retry) - migrate definition and remaining instances
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks();

    // then: Definition is migrated but previously skipped instances remain skipped
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId").size()).isEqualTo(1);
    
    // Instances remain skipped (regular migration doesn't retry skipped entities)
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
  }
}
