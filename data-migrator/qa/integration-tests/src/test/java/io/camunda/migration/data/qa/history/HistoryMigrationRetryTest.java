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
import io.camunda.migration.data.impl.history.DecisionDefinitionMigrator;
import io.camunda.migration.data.impl.history.DecisionInstanceMigrator;
import io.camunda.migration.data.impl.history.DecisionRequirementsMigrator;
import io.camunda.migration.data.impl.history.FlowNodeMigrator;
import io.camunda.migration.data.impl.history.IncidentMigrator;
import io.camunda.migration.data.impl.history.ProcessDefinitionMigrator;
import io.camunda.migration.data.impl.history.ProcessInstanceMigrator;
import io.camunda.migration.data.impl.history.UserTaskMigrator;
import io.camunda.migration.data.impl.history.VariableMigrator;
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
  protected LogCapturer logs = LogCapturer.create()
      .captureForType(HistoryMigrator.class, Level.DEBUG)
      .captureForType(ProcessDefinitionMigrator.class, Level.DEBUG)
      .captureForType(ProcessInstanceMigrator.class, Level.DEBUG)
      .captureForType(FlowNodeMigrator.class, Level.DEBUG)
      .captureForType(UserTaskMigrator.class, Level.DEBUG)
      .captureForType(VariableMigrator.class, Level.DEBUG)
      .captureForType(IncidentMigrator.class, Level.DEBUG)
      .captureForType(DecisionRequirementsMigrator.class, Level.DEBUG)
      .captureForType(DecisionDefinitionMigrator.class, Level.DEBUG)
      .captureForType(DecisionInstanceMigrator.class, Level.DEBUG);

  @Test
  public void shouldMigratePreviouslySkippedProcessDefinition() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    // First migration skipps with a real-world scenario due to missing process definition migration
    getHistoryMigrator().migrateProcessInstances(); // Skips because definition not migrated

    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(0);

    // when: Now migrate definitions and retry skipped instances
    getHistoryMigrator().migrateProcessDefinitions();
    getHistoryMigrator().setMode(MigratorMode.RETRY_SKIPPED);
    getHistoryMigrator().migrate();

    // then: Process definition is migrated
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(1);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionRequirementsDefinition() {
    // given: Deploy decision with requirements
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // Migrate decision definitions
    getHistoryMigrator().migrateDecisionDefinitions();
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId")).hasSize(0);

    // Migrate dependency
    getHistoryMigrator().migrateDecisionRequirementsDefinitions();

    // when: Retry migration (should not duplicate)
    getHistoryMigrator().setMode(MigratorMode.RETRY_SKIPPED);
    getHistoryMigrator().migrate();

    // then: Decision requirements definition exists
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId")).hasSize(1);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionInstancesWithInputsAndOutputs() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");

    // Start process instance with variables that will become decision inputs
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        org.camunda.bpm.engine.variable.Variables.createVariables()
            .putValue("inputA", "A"));

    // Try to migrate decision instances without definitions (will skip)
    getHistoryMigrator().migrateDecisionInstances();

    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();

    // Migrate everything else
    getHistoryMigrator().migrate();

    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();

    getHistoryMigrator().setMode(MigratorMode.RETRY_SKIPPED);

    // when
    getHistoryMigrator().migrate();

    // then: Decision instance is migrated with inputs and outputs
    var decisionInstances = searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(decisionInstances).hasSize(1);
    assertThat(decisionInstances.getFirst().evaluatedInputs())
        .hasSize(1)
        .first()
        .satisfies(input -> {
          assertThat(input.inputName()).isEqualTo("inputA");
          assertThat(input.inputValue()).isEqualTo("\"A\"");
        });
    assertThat(decisionInstances.getFirst().evaluatedOutputs())
        .hasSize(1)
        .first()
        .satisfies(output -> {
          assertThat(output.outputName()).isEqualTo("outputB");
          assertThat(output.outputValue()).isEqualTo("\"B\"");
        });
  }

  @Test
  public void shouldMigrateOnlyPreviouslySkippedElementsOnRetry() {
    // given
    deployer.deployCamunda7Process("includeAllSupportedElementsProcess.bpmn");
    runtimeService.startProcessInstanceByKey("allElementsProcessId");
    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    // Create real-world skip scenario
    getHistoryMigrator().migrateProcessInstances();
    getHistoryMigrator().migrateFlowNodes();
    getHistoryMigrator().migrateUserTasks();
    getHistoryMigrator().migrateVariables();
    getHistoryMigrator().migrateIncidents();

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
    getHistoryMigrator().migrateProcessDefinitions();
    getHistoryMigrator().setMode(MigratorMode.RETRY_SKIPPED);
    getHistoryMigrator().migrate();

    // then only previously skipped entities are migrated
    assertThat(searchHistoricProcessDefinitions("allElementsProcessId")).hasSize(1);
    processInstances = searchHistoricProcessInstances("allElementsProcessId");
    assertThat(processInstances).hasSize(1);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey())).hasSize(1);
    assertThat(searchHistoricVariables("userTaskVar")).hasSize(1);
  }

  @Test
  public void shouldNotMigratePreviouslySkippedElementsOnRerun() {
    // given state in c7
    // Start one process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // Try to migrate without process definition
    getHistoryMigrator().migrateProcessInstances();
    getHistoryMigrator().migrateFlowNodes();
    getHistoryMigrator().migrateUserTasks();

    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(0);
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).hasSize(0);

    // Start 4 more process instances
    for (int i = 0; i < 4; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // Migrate normally
    getHistoryMigrator().migrate();

    // then only non skipped entities are migrated
    // Assert that 4 process instances were migrated, not 5
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(1);
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).hasSize(4);
  }

}
