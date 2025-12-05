/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({ OutputCaptureExtension.class })
public class HistoryMigrationRetryTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Test
  public void shouldMigratePreviouslySkippedProcessDefinition() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    // First migration skipps with a real-world scenario due to missing process definition migration
    historyMigrator.migrateProcessInstances(); // Skips because definition not migrated

    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(0);

    // when: Now migrate definitions and retry skipped instances
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then: Process definition is migrated
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId").size()).isEqualTo(1);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionRequirementsDefinition() {
    // given: Deploy decision with requirements
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // Migrate decision definitions
    historyMigrator.migrateDecisionDefinitions();
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId")).hasSize(0);

    // Migrate dependency
    historyMigrator.migrateDecisionRequirementsDefinitions();

    // when: Retry migration (should not duplicate)
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then: Decision requirements definition exists
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId").size()).isEqualTo(1);
  }

  @Test
  public void shouldMigrateOnlyPreviouslySkippedElementsOnRetry() {
    // given
    deployer.deployCamunda7Process("includeAllSupportedElementsProcess.bpmn");
    runtimeService.startProcessInstanceByKey("allElementsProcessId");
    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    // Create real-world skip scenario
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks();
    historyMigrator.migrateVariables();
    historyMigrator.migrateIncidents();

    assertThat(searchHistoricProcessDefinitions("allElementsProcessId")).hasSize(0);
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("allElementsProcessId");
    assertThat(processInstances).hasSize(0);
    assertThat(searchHistoricIncidents("allElementsProcessId")).hasSize(0);
    assertThat(searchHistoricVariables("userTaskVar")).hasSize(0);

    // Create more instances that will be skipped
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("allElementsProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    // when: Retry skipped entities
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then only previously skipped entities are migrated
    assertThat(searchHistoricProcessDefinitions("allElementsProcessId").size()).isEqualTo(1);
    processInstances = searchHistoricProcessInstances("allElementsProcessId");
    assertThat(processInstances.size()).isEqualTo(1);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey()).size()).isEqualTo(1);
    assertThat(searchHistoricIncidents("allElementsProcessId").size()).isEqualTo(1);
    assertThat(searchHistoricVariables("userTaskVar").size()).isEqualTo(1);
  }

  @Test
  public void shouldNotMigratePreviouslySkippedElementsOnRerun() {
    // given state in c7
    // Start one process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // Try to migrate without process definition
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks();

    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(0);
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).hasSize(0);

    // Start 4 more process instances
    for (int i = 0; i < 4; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // Migrate normally
    historyMigrator.migrate();

    // then only non skipped entities are migrated
    // Assert that 4 process instances were migrated, not 5
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId").size()).isEqualTo(1);
    assertThat(searchHistoricProcessInstances("userTaskProcessId").size()).isEqualTo(4);
  }

}