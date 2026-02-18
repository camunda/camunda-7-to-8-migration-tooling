/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.*;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.history.migrator.DecisionDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionRequirementsMigrator;
import io.camunda.migration.data.impl.history.migrator.FlowNodeMigrator;
import io.camunda.migration.data.impl.history.migrator.IncidentMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.UserTaskMigrator;
import io.camunda.migration.data.impl.history.migrator.VariableMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({ OutputCaptureExtension.class })
public class HistoryMigrationRetryTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected MigratorProperties properties;

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

  @AfterEach
  public void tearDown() {
    properties.setPageSize(MigratorProperties.DEFAULT_PAGE_SIZE);
    for (var task : taskService.createTaskQuery().list()) {
      taskService.deleteTask(task.getId(), true);
    }
  }

  @Test
  public void shouldMigratePreviouslySkippedProcessDefinition() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    // First migration skipps with a real-world scenario due to missing process definition migration
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE); // Skips because definition not migrated

    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(0);

    // when: Now migrate definitions and retry skipped instances
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.retry();
    historyMigrator.migrate();

    // then: Process definition is migrated
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(1);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionRequirementsDefinition() {
    // given: Deploy decision with requirements
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // Migrate decision definitions
    historyMigrator.migrateByType(HISTORY_DECISION_DEFINITION);
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId")).hasSize(0);

    // Migrate dependency
    historyMigrator.migrateByType(HISTORY_DECISION_REQUIREMENT);

    // when: Retry migration (should not duplicate)
    historyMigrator.retry();
    historyMigrator.migrate();

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
    historyMigrator.migrateByType(HISTORY_DECISION_INSTANCE);

    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();

    // Migrate everything else
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_DECISION_DEFINITION);
    historyMigrator.migrateByType(HISTORY_DECISION_REQUIREMENT);
    historyMigrator.migrateByType(HISTORY_DECISION_INSTANCE);

    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();

    historyMigrator.retry();

    // when
    historyMigrator.migrate();

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
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_USER_TASK);
    historyMigrator.migrateByType(HISTORY_VARIABLE);
    historyMigrator.migrateByType(HISTORY_INCIDENT);

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
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.retry();

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
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(0);
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).hasSize(0);

    // Start 4 more process instances
    for (int i = 0; i < 4; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // Migrate normally
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // then only non skipped entities are migrated
    // Assert that 4 process instances were migrated, not 5
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId")).hasSize(1);
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).hasSize(4);
  }

  @Test
  public void shouldOnlyRetryPermanentlySkippedEntitiesOnce() {
    // given: Use a small page size to trigger pagination (5 tasks, page size 3 = 2 pages)
    int taskCount = 5;
    properties.setPageSize(3);

    // Create standalone user tasks (permanently skipped since they have no process instance)
    List<String> standaloneTaskIds = new ArrayList<>();
    for (int i = 0; i < taskCount; i++) {
      Task task = taskService.newTask();
      task.setName("Standalone Task " + i);
      taskService.saveTask(task);
      standaloneTaskIds.add(task.getId());
    }

    // First migration - these will be skipped because they have no process instance
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // Verify each standalone task was skipped exactly once
    for (String taskId : standaloneTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_UNSUPPORTED_SA_TASKS);
      List<String> matchingLogs = logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .toList();
      assertThat(matchingLogs)
          .as("Task %s should be skipped exactly once during initial migration", taskId)
          .hasSize(1);
    }

    // when: Retry skipped entities
    historyMigrator.retry();

    // then: Each permanently skipped entity should only be retried once
    for (String taskId : standaloneTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_UNSUPPORTED_SA_TASKS);
      List<String> matchingLogs = logs.getEvents().stream()
          .filter(e -> Level.WARN.equals(e.getLevel()))
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .toList();
      assertThat(matchingLogs).hasSize(1);
    }
  }

  @Test
  public void shouldRetryAllSkippedEntitiesWhenAllSucceedWithPagination() {
    // Scenario: All entities succeed during retry, testing that pagination doesn't miss any
    // when entities are removed from the skipped list
    int taskCount = 5;
    properties.setPageSize(3);

    // given: Deploy process and create process instances with user tasks
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    for (int i = 0; i < taskCount; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }

    // Complete all user tasks to create historic task instances
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }

    // Capture user task IDs
    List<String> userTaskIds = historyService.createHistoricTaskInstanceQuery().list().stream()
        .map(HistoricTaskInstance::getId)
        .toList();
    assertThat(userTaskIds).hasSize(taskCount);

    // Migrate user tasks WITHOUT process definitions/instances first (will skip)
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // Verify all were skipped
    for (String taskId : userTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      assertThat(logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .as("Task %s should be skipped during initial migration", taskId)
          .isEqualTo(1);
    }

    // Now migrate process definitions and instances so retry will succeed
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);

    // when: Retry skipped user tasks - all should now succeed
    historyMigrator.retry();
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // then: All user tasks should be migrated (none skipped during retry)
    var processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(taskCount);

    int totalMigratedUserTasks = 0;
    for (var pi : processInstances) {
      totalMigratedUserTasks += searchHistoricUserTasks(pi.processInstanceKey()).size();
    }
    assertThat(totalMigratedUserTasks)
        .as("All %d user tasks should be migrated successfully", taskCount)
        .isEqualTo(taskCount);

    // Verify no additional skip logs during retry (all succeeded)
    for (String taskId : userTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      assertThat(logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .as("Task %s should only be skipped once (initial), not during retry", taskId)
          .isEqualTo(1);
    }
  }

  @Test
  public void shouldRetryAllSkippedEntitiesWhenSomeSucceedAndSomeFailWithPagination() {
    // Scenario: Mixed - some entities succeed (removed from skipped list), some remain skipped
    // This tests the tricky offset calculation where the list shrinks during iteration
    properties.setPageSize(3);

    // given: Create 5 user tasks - 3 with process instances (will succeed) and 2 standalone (will fail)
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    // Create 3 process instances with user tasks (these will eventually succeed)
    List<String> processTaskIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
      Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
      taskService.complete(task.getId());
      processTaskIds.add(historyService.createHistoricTaskInstanceQuery()
          .processInstanceId(processInstance.getId()).singleResult().getId());
    }

    // Create 2 standalone user tasks (these will permanently fail)
    List<String> standaloneTaskIds = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      Task task = taskService.newTask();
      task.setName("Standalone Task " + i);
      taskService.saveTask(task);
      standaloneTaskIds.add(task.getId());
    }

    // Migrate user tasks WITHOUT process definitions/instances first (all will skip)
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // Verify all 5 were skipped initially
    for (String taskId : standaloneTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_UNSUPPORTED_SA_TASKS);
      assertThat(logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .as("Task %s should be skipped during initial migration", taskId)
          .isEqualTo(1);
    }

    for (String taskId : processTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      assertThat(logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .as("Task %s should be skipped during initial migration", taskId)
          .isEqualTo(1);
    }

    // Now migrate process definitions and instances so process-bound tasks will succeed on retry
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);

    // when: Retry skipped user tasks
    // 3 process-bound tasks should succeed (removed from skipped list)
    // 2 standalone tasks should fail again (remain in skipped list)
    historyMigrator.retry();
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // then: Process-bound tasks should be migrated
    var processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(3);

    int totalMigratedUserTasks = 0;
    for (var pi : processInstances) {
      totalMigratedUserTasks += searchHistoricUserTasks(pi.processInstanceKey()).size();
    }
    assertThat(totalMigratedUserTasks)
        .as("All 3 process-bound user tasks should be migrated")
        .isEqualTo(3);

    // Process-bound tasks: skipped once (initial), NOT skipped during retry (succeeded)
    for (String taskId : processTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      assertThat(logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .isEqualTo(1);
    }

    // Standalone tasks: skipped twice (initial + retry)
    for (String taskId : standaloneTaskIds) {
      String expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_UNSUPPORTED_SA_TASKS);
      assertThat(logs.getEvents().stream()
          .filter(e -> Level.WARN.equals(e.getLevel()))
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .isEqualTo(1);
    }
  }

}
