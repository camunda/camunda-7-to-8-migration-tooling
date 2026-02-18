/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_SA_TASKS;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({ OutputCaptureExtension.class })
public class HistoryMigrationInitialRetryLoopTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.WARN);

  @AfterEach
  public void tearDown() {
    for (var task : taskService.createTaskQuery().list()) {
      if (task.getProcessDefinitionId() == null) {
        taskService.deleteTask(task.getId(), true);
      }
    }
  }

  @Test
  public void shouldHandleMultipleEntityTypesWithMixedDependencies() {
    // given: Deploy complex process with entities that depend on each other
    deployer.deployCamunda7Process("retry-process.bpmn");

    // Create multiple process instances and standalone tasks
    List<String> standaloneTaskIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      runtimeService.startProcessInstanceByKey("retry-process");

      Task task = taskService.newTask();
      taskService.saveTask(task);
      standaloneTaskIds.add(task.getId());
    }

    // when: Call migrate() - should handle cross-entity-type dependencies automatically
    historyMigrator.migrate();

    // then: All entity types should be successfully migrated
    assertThat(searchHistoricProcessDefinitions("retry-process")).hasSize(1);
    var processInstances = searchHistoricProcessInstances("retry-process");
    assertThat(processInstances).hasSize(3);

    // Verify all related entities are migrated
    for (var pi : processInstances) {
      assertThat(searchHistoricFlowNodes(pi.processInstanceKey())).isNotEmpty();
      assertThat(searchHistoricUserTasks(pi.processInstanceKey())).isNotEmpty();
      assertThat(searchHistoricVariables(pi.processInstanceKey())).isNotEmpty();
    }

    for (String taskId : standaloneTaskIds) {
      var expectedLogMessage = formatMessage(SKIPPING, HISTORY_USER_TASK.getDisplayName(), taskId,
          SKIP_REASON_UNSUPPORTED_SA_TASKS);
      assertThat(logs.getEvents().stream()
          .map(LoggingEvent::getMessage)
          .filter(message -> message.contains(expectedLogMessage))
          .count())
          .as("Task %s should have skip log entry", taskId)
          .isEqualTo(1);
    }
  }
}

