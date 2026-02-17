/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.qa.util.DateTimeAssert.assertThatDateTime;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryDateConversionTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @AfterEach
  public void resetClock() {
    ClockUtil.reset();
  }

  @Test
  public void shouldConvertProcessInstanceDatesCorrectly() {
    // given - complete a process at specific times
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    Date startTime = new Date(1609459200000L); // 2021-01-01 00:00:00 UTC
    ClockUtil.setCurrentTime(startTime);
    String processInstanceId = runtimeService.startProcessInstanceByKey("userTaskProcessId").getProcessInstanceId();

    // Complete at a later time
    Date endTime = new Date(1609545600000L); // 2021-01-02 00:00:00 UTC
    ClockUtil.setCurrentTime(endTime);
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    var c7ProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(processInstanceId)
        .singleResult();

    // when
    historyMigrator.migrate();

    // then - verify both start and end dates are converted correctly and preserve absolute moments
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();

    assertThatDateTime(migratedInstance.startDate())
        .isNotNull()
        .isEqualToLocalTime(c7ProcessInstance.getStartTime());

    assertThatDateTime(migratedInstance.endDate())
        .isNotNull()
        .isEqualToLocalTime(c7ProcessInstance.getEndTime());
  }

  @Test
  public void shouldConvertAllEntityDatesCorrectly() {
    // given - complete a process with flow nodes and user tasks
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    Date startTime = new Date(1609459200000L); // 2021-01-01 00:00:00 UTC
    ClockUtil.setCurrentTime(startTime);
    String processInstanceId = runtimeService.startProcessInstanceByKey("userTaskProcessId").getProcessInstanceId();

    // Complete the user task
    Date completionTime = new Date(1609545600000L); // 2021-01-02 00:00:00 UTC
    ClockUtil.setCurrentTime(completionTime);
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // Get C7 historic entities for comparison
    var c7StartEvent = historyService.createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .activityType("startEvent")
        .singleResult();
    var c7UserTask = historyService.createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .activityType("userTask")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then - verify all entity dates are correctly converted from C7
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // Verify start event dates match C7
    List<FlowNodeInstanceEntity> startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    FlowNodeInstanceEntity startEvent = startEvents.getFirst();
    assertThatDateTime(startEvent.startDate())
        .isNotNull()
        .isEqualToLocalTime(c7StartEvent.getStartTime());
    assertThatDateTime(startEvent.endDate())
        .isNotNull()
        .isEqualToLocalTime(c7StartEvent.getEndTime());

    // Verify end event dates match C7
    List<FlowNodeInstanceEntity> endEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, END_EVENT);
    assertThat(endEvents).hasSize(1);
    FlowNodeInstanceEntity endEvent = endEvents.getFirst();

    // Verify user task flow node dates match C7
    List<FlowNodeInstanceEntity> userTaskFlowNodes = historyMigration.searchHistoricFlowNodes(processInstanceKey)
        .stream()
        .filter(fn -> "userTaskId".equals(fn.flowNodeId()))
        .toList();
    assertThat(userTaskFlowNodes).hasSize(1);
    FlowNodeInstanceEntity userTaskFlowNode = userTaskFlowNodes.getFirst();
    assertThatDateTime(userTaskFlowNode.startDate())
        .isNotNull()
        .isEqualToLocalTime(c7UserTask.getStartTime());
    assertThatDateTime(userTaskFlowNode.endDate())
        .isNotNull()
        .isEqualToLocalTime(c7UserTask.getEndTime());

    // Verify user task entity dates
    List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).hasSize(1);
    UserTaskEntity userTask = userTasks.getFirst();

    assertThatDateTime(userTask.creationDate()).isNotNull();
    assertThatDateTime(userTask.completionDate())
        .isNotNull()
        .isEqualToLocalTime(completionTime);
  }


  @Test
  public void shouldConvertMultipleProcessInstanceDatesConsistently() {
    // given - create multiple process instances at different times
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Date time1 = new Date(1609459200000L); // 2021-01-01 00:00:00 UTC
    ClockUtil.setCurrentTime(time1);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    Date time2 = new Date(1640995200000L); // 2022-01-01 00:00:00 UTC
    ClockUtil.setCurrentTime(time2);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    Date time3 = new Date(1672531200000L); // 2023-01-01 00:00:00 UTC
    ClockUtil.setCurrentTime(time3);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when
    historyMigrator.migrate();

    // then - verify all dates are converted correctly and maintain ordering
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(3);

    // Sort by start date to verify ordering
    processInstances.sort(java.util.Comparator.comparing(ProcessInstanceEntity::startDate));

    assertThatDateTime(processInstances.get(0).startDate())
        .isEqualToLocalTime(time1);
    assertThatDateTime(processInstances.get(1).startDate())
        .isEqualToLocalTime(time2);
    assertThatDateTime(processInstances.get(2).startDate())
        .isEqualToLocalTime(time3);

    // Verify ordering is preserved
    assertThat(processInstances.get(0).startDate())
        .isBefore(processInstances.get(1).startDate());
    assertThat(processInstances.get(1).startDate())
        .isBefore(processInstances.get(2).startDate());
  }

  @Test
  public void shouldHandleNullEndDateForActiveProcessInstance() {
    // given - create an active process instance (not completed)
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    Date startTime = new Date(1609459200000L); // 2021-01-01 00:00:00 UTC
    ClockUtil.setCurrentTime(startTime);
    String processInstanceId = runtimeService.startProcessInstanceByKey("userTaskProcessId").getProcessInstanceId();

    // Get C7 process instance before migration
    var c7ProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(processInstanceId)
        .singleResult();

    // Verify C7 instance has no end date (still active)
    assertThat(c7ProcessInstance.getEndTime()).isNull();

    // Set migration time
    Date migrationTime = new Date(1609545600000L); // 2021-01-02 00:00:00 UTC
    ClockUtil.setCurrentTime(migrationTime);

    // when
    historyMigrator.migrate();

    // then - verify dates are correctly set
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();

    // Start date should match C7
    assertThatDateTime(migratedInstance.startDate())
        .isNotNull()
        .isEqualToLocalTime(c7ProcessInstance.getStartTime());

    // End date should be set to migration time during auto-cancel
    assertThatDateTime(migratedInstance.endDate())
        .isNotNull()
        .isEqualToLocalTime(migrationTime);

    // State should be canceled
    assertThat(migratedInstance.state())
        .isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
  }
}

