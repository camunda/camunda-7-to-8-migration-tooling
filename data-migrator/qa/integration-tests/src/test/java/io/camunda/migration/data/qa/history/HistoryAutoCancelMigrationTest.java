/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.qa.util.DateTimeAssert.*;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;
import static io.camunda.search.entities.UserTaskEntity.UserTaskState.CANCELED;
import static io.camunda.search.entities.UserTaskEntity.UserTaskState.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.migration.data.qa.util.ProcessDefinitionDeployer;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for auto-cancellation of active process instances during history migration.
 *
 * <p>When migrating history, active process instances are automatically canceled to ensure
 * consistency in C8. This test class verifies that:</p>
 * <ul>
 *   <li>Active process instances are auto-canceled with state CANCELED</li>
 *   <li>End dates are set appropriately during auto-cancellation</li>
 *   <li>Completed process instances retain their original state and end dates</li>
 *   <li>Child entities (flow nodes, user tasks) are terminated/canceled consistently</li>
 *   <li>End dates cascade correctly to terminated child entities</li>
 * </ul>
 */
@SpringBootTest
public class HistoryAutoCancelMigrationTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Autowired
  protected ProcessDefinitionDeployer deployer;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  @Test
  public void shouldAutoCancelActiveProcessInstance() {
    // given - deploy and start a process instance that remains active
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    Date migrationTime = new Date();
    ClockUtil.setCurrentTime(migrationTime);

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - process instance should be auto-canceled with endDate set to "now"
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    // Verify state conversion: ACTIVE -> CANCELED
    assertThat(migratedInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
    // Verify endDate was set to "migrationTime" during migration
    assertThat(migratedInstance.endDate())
        .isEqualTo(convertDate(migrationTime));
  }

  @Test
  public void shouldNotCancelCompletedProcessInstance() {
    // given - deploy and complete a process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Complete the user task at a later time
    Date completionTime = new Date();
    ClockUtil.setCurrentTime(completionTime);
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - completed process instance should keep its original state and end date
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    // Verify state remains COMPLETED
    assertThat(migratedInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.COMPLETED);
    // Verify endDate exists and matches the completion time (from C7)
    assertThat(migratedInstance.endDate())
        .isEqualTo(convertDate(completionTime));
  }

  @Test
  public void shouldCascadeEndDateToTerminatedFlowNodes() {
    // given - deploy and start a process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - terminated flow nodes should inherit the process instance's end date
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();
    OffsetDateTime processInstanceEndDate = migratedInstance.endDate();

    assertThat(processInstanceEndDate).isNotNull();

    // Check start event - should NOT have cascaded end date (it's COMPLETED)
    var startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    FlowNodeInstanceEntity startEvent = startEvents.getFirst();
    assertThat(startEvent.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.COMPLETED);
    assertThat(startEvent.endDate())
        .as("Start event should have its own completion endDate, not cascaded from process instance")
        .isNotEqualTo(processInstanceEndDate);

    // Check user task flow node - should have cascaded end date (it's TERMINATED)
    var userTaskFlowNodes = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTaskFlowNodes).hasSize(1);
    FlowNodeInstanceEntity userTaskFlowNode = userTaskFlowNodes.getFirst();
    assertThat(userTaskFlowNode.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.TERMINATED);
    assertThat(userTaskFlowNode.endDate())
        .as("Terminated user task flow node should have cascaded endDate from process instance")
        .isEqualTo(processInstanceEndDate);
  }

  @Test
  public void shouldCancelActiveUserTasksWhenAutoCanceling() {
    // given - deploy and start a process instance with active user task
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - user task should be canceled
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();

    List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).hasSize(1);

    UserTaskEntity userTask = userTasks.getFirst();
    assertThat(userTask.state()).isEqualTo(CANCELED);
  }

  @Test
  public void shouldCascadeCompletionDateToUserTasksWhenAutoCanceling() {
    // given - deploy and start a process instance with active user task
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - user task should inherit the process instance's end date as completion date
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();
    OffsetDateTime processInstanceEndDate = migratedInstance.endDate();

    List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).hasSize(1);

    UserTaskEntity userTask = userTasks.getFirst();
    assertThat(userTask.state()).isEqualTo(CANCELED);
    // Verify the completionDate was cascaded from process instance (allow small timing differences)
    assertThat(userTask.completionDate())
        .as("User task completion date should be cascaded from process instance")
        .isEqualTo(processInstanceEndDate);
  }

  @Test
  public void shouldNotCascadeEndDatesToCompletedEntities() {
    // given - deploy and complete a process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Date startTime = new Date();
    ClockUtil.setCurrentTime(startTime);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Complete the user task at a later time
    Date completionTime = new Date(startTime.getTime() + 5_000); // +5 seconds
    ClockUtil.setCurrentTime(completionTime);
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - completed entities should keep their original end dates
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();
    OffsetDateTime processInstanceEndDate = migratedInstance.endDate();

    // Flow nodes should have their own end dates
    var startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    assertThat(startEvents.getFirst().endDate())
        .isEqualTo(convertDate(startTime))
        .isNotEqualTo(processInstanceEndDate);

    var userTaskFlowNodes = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTaskFlowNodes).hasSize(1);
    assertThat(userTaskFlowNodes.getFirst().endDate())
        .isEqualTo(convertDate(completionTime));

    // User task should have its own completion date
    List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).hasSize(1);
    UserTaskEntity userTask = userTasks.getFirst();
    assertThat(userTask.state()).isEqualTo(COMPLETED);
    assertThat(userTask.completionDate())
        .isEqualTo(convertDate(completionTime))
        .isEqualTo(processInstanceEndDate);
  }

  @Test
  public void shouldAutoCancelMultipleActiveProcessInstancesConsistently() {
    // given - deploy and start multiple active process instances
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }

    Date migrationTime = new Date();
    ClockUtil.setCurrentTime(migrationTime);

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - all process instances should be auto-canceled with consistent behavior
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(5);

    for (ProcessInstanceEntity instance : processInstances) {
      // All should be auto-canceled with endDate set
      assertThat(instance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
      assertThat(instance.endDate()).isEqualTo(convertDate(migrationTime));
    }
  }

  @Test
  public void shouldCascadeEndDateToAllChildEntitiesConsistently() {
    // given - deploy and start multiple active process instances
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - all instances and their cancelled children should have cascaded end dates
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(5);

    for (ProcessInstanceEntity instance : processInstances) {
      assertThat(instance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
      assertThat(instance.endDate()).isNotNull();

      // Verify only TERMINATED child flow nodes have cascaded end dates
      Long processInstanceKey = instance.processInstanceKey();

      // Start events should be COMPLETED with their own end dates
      var startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
      for (FlowNodeInstanceEntity flowNode : startEvents) {
        assertThat(flowNode.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.COMPLETED);
        assertThat(flowNode.endDate())
            .as("Completed start event should have its own endDate, not cascaded")
            .isNotEqualTo(instance.endDate());
      }

      // User task flow nodes should be TERMINATED with cascaded end dates
      var userTaskFlowNodes = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
      for (FlowNodeInstanceEntity flowNode : userTaskFlowNodes) {
        assertThat(flowNode.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.TERMINATED);
        assertThat(flowNode.endDate())
            .as("Terminated flow node should have cascaded endDate")
            .isEqualTo(instance.endDate());
      }
    }
  }

  @Test
  public void shouldHandleMixedStateProcessInstancesCorrectly() {
    // given - deploy and start mixed process instances (some active, some completed)
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Date startTime = new Date();
    ClockUtil.setCurrentTime(startTime);

    // Start 3 active instances
    for (int i = 0; i < 3; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }

    // Start and complete 2 instances
    for (int i = 0; i < 2; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
      Task task = taskService.createTaskQuery().list().get(i);
      taskService.complete(task.getId());
    }

    Date migrationTime = new Date(startTime.getTime() + 10_000); // +10 seconds
    ClockUtil.setCurrentTime(migrationTime);

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - verify state-specific behavior
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(5);

    // Verify all have end dates
    var canceledPis = processInstances.stream()
        .filter(pi -> pi.state() == ProcessInstanceEntity.ProcessInstanceState.CANCELED)
        .toList();

    assertThat(canceledPis).hasSize(3);

    for (ProcessInstanceEntity instance : canceledPis) {
      assertThat(instance.endDate()).isEqualTo(convertDate(migrationTime));
    }

    var completedPis = processInstances.stream()
        .filter(pi -> pi.state() == ProcessInstanceEntity.ProcessInstanceState.COMPLETED)
        .toList();

    assertThat(completedPis).hasSize(2);

    for (ProcessInstanceEntity instance : completedPis) {
      assertThat(instance.endDate()).isEqualTo(convertDate(startTime));
    }
  }

  @Test
  public void shouldUseOriginalEndDateForCompletedFlowNodes() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Date initialTime = new Date();
    ClockUtil.setCurrentTime(initialTime);

    // Start process instance at T0
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Advance time before completing task
    Date taskCompletionTime = new Date(initialTime.getTime() + 10_000); // +10 seconds
    ClockUtil.setCurrentTime(taskCompletionTime);

    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - completed flow nodes should keep their original end dates
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();

    // Start event should have its own end date from T0
    var startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    assertThat(startEvents.getFirst().endDate())
        .isEqualTo(convertDate(initialTime));

    // User task flow node should have end date from task completion time
    var userTaskFlowNodes = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTaskFlowNodes).hasSize(1);
    assertThat(userTaskFlowNodes.getFirst().endDate())
        .isEqualTo(convertDate(taskCompletionTime));

    // End event should have the same end date as process instance (task completion time)
    var endEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, END_EVENT);
    assertThat(endEvents).hasSize(1);
    assertThat(endEvents.getFirst().endDate())
        .isEqualTo(convertDate(taskCompletionTime));
  }

  @Test
  public void shouldUseOriginalCompletionDateForCompletedUserTasks() {
    // given - deploy and complete a process instance
    deployer.deployCamunda7Process("twoUserTasksProcess.bpmn");
    runtimeService.startProcessInstanceByKey("twoUserTasksProcessId");

    Date taskCompletionTime = new Date();
    ClockUtil.setCurrentTime(taskCompletionTime);
    var tasks = taskService.createTaskQuery().list();
    taskService.complete(tasks.getFirst().getId());

    Date processCompletionTime = new Date(taskCompletionTime.getTime() + 10_000); // +10 seconds
    ClockUtil.setCurrentTime(processCompletionTime);

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - completed user task should keep its original completion date
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("twoUserTasksProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();
    OffsetDateTime processInstanceEndDate = migratedInstance.endDate();

    List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey)
        .stream().sorted(Comparator.comparing(UserTaskEntity::completionDate)).toList();
    assertThat(userTasks).hasSize(2);

    assertThatDateTime(userTasks.getFirst().completionDate()).isEqualToLocalTime(taskCompletionTime);
    assertThat(userTasks.getFirst().state()).isEqualTo(COMPLETED);

    assertThatDateTime(userTasks.getLast().completionDate()).isEqualToLocalTime(processInstanceEndDate);
    assertThat(userTasks.getLast().state()).isEqualTo(CANCELED);
  }

}