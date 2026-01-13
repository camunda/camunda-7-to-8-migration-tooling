/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SUB_PROCESS;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class HistoryFlowNodeTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldGenerateTreePathForFlowNodes() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    
    ProcessInstanceEntity processInstance = processInstances.getFirst();
    Long processInstanceKey = processInstance.processInstanceKey();
    
    // Verify all flow node types have treePath set
    List<FlowNodeInstanceEntity> startEvents = searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    FlowNodeInstanceEntity startEvent = startEvents.getFirst();
    assertThat(startEvent.treePath()).isNotNull().isEqualTo(processInstanceKey + "/" + startEvent.flowNodeInstanceKey());
    
    List<FlowNodeInstanceEntity> userTasks = searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTasks).hasSize(1);
    FlowNodeInstanceEntity userTask = userTasks.getFirst();
    assertThat(userTask.treePath()).isNotNull().isEqualTo(processInstanceKey + "/" + userTask.flowNodeInstanceKey());
    
    List<FlowNodeInstanceEntity> endEvents = searchHistoricFlowNodesForType(processInstanceKey, END_EVENT);
    assertThat(endEvents).hasSize(1);
    FlowNodeInstanceEntity endEvent = endEvents.getFirst();
    assertThat(endEvent.treePath()).isNotNull().isEqualTo(processInstanceKey + "/" + endEvent.flowNodeInstanceKey());
  }

  @Test
  public void shouldMigrateCompletedFlowNodeState() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstances.getFirst().processInstanceKey());
    assertThat(flowNodes).hasSize(3)
        .extracting(FlowNodeInstanceEntity::state)
        .containsOnly(FlowNodeInstanceEntity.FlowNodeState.COMPLETED);
  }

  @Test
  public void shouldMigrateCancelledFlowNodeState() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    String processInstanceId = runtimeService.startProcessInstanceByKey("userTaskProcessId").getId();
    runtimeService.deleteProcessInstance(processInstanceId, "Expected cancellation");

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> userTasks = searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTasks).singleElement().satisfies(userTask ->
      assertThat(userTask.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.TERMINATED)
    );
  }

  @Test
  public void shouldSetFlowNodeScopeKey() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    ProcessInstanceEntity processInstance = processInstances.getFirst();
    Long processInstanceKey = processInstance.processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);

    assertThat(flowNodes).isNotEmpty();
    for (FlowNodeInstanceDbModel flowNode : flowNodes) {
      assertThat(flowNode.flowNodeScopeKey()).isEqualTo(processInstanceKey);
    }
  }

  @Test
  public void shouldSetFlowNodeScopeKeyForFlowNodeInSubprocess() {
    // given
    deploySubprocessModel();
    runtimeService.startProcessInstanceByKey("subProcess");
    completeAllUserTasksWithDefaultUserTaskId();

    // when migrating flow nodes we order by activity id and start time,
    // so that the subprocess flow node can be processed before its parent flow node, and they might be skipped initially;
    // we then retry the skipped ones to ensure they are migrated
    historyMigrator.migrate();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("subProcess");
    assertThat(processInstances).hasSize(1);
    ProcessInstanceEntity processInstance = processInstances.getFirst();
    Long processInstanceKey = processInstance.processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    FlowNodeInstanceDbModel subprocessFlowNode = flowNodes.stream()
        .filter(fn -> fn.type() == SUB_PROCESS)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Subprocess flow node not found"));
    assertThat(subprocessFlowNode.flowNodeScopeKey()).isEqualTo(processInstanceKey);
    List<FlowNodeInstanceDbModel> nestedFlowNodes = flowNodes.stream()
        .filter(fn -> !Set.of("start_outsideSub","subprocess", "end_outsideSub").contains(fn.flowNodeId()))
        .toList();

    assertThat(nestedFlowNodes).hasSize(3)
        .allSatisfy(fn -> assertThat(fn.flowNodeScopeKey()).isEqualTo(subprocessFlowNode.flowNodeInstanceKey()))
        .extracting(FlowNodeInstanceDbModel::flowNodeId)
        .containsExactlyInAnyOrder("start_insideSub", USER_TASK_ID, "end_insideSub");

    List<FlowNodeInstanceDbModel> topLevelFlowNodes = flowNodes.stream()
        .filter(
            fn -> !Set.of("start_insideSub", "subprocess", USER_TASK_ID, "end_insideSub").contains(fn.flowNodeId()))
        .toList();

    assertThat(topLevelFlowNodes).hasSize(2)
        .allSatisfy(fn -> assertThat(fn.flowNodeScopeKey()).isEqualTo(processInstanceKey))
        .extracting(FlowNodeInstanceDbModel::flowNodeId)
        .containsExactlyInAnyOrder("start_outsideSub", "end_outsideSub");
  }

  @Test
  public void shouldSetFlowNodeScopeKeyForFlowNodeInCalledProcess() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    runtimeService.startProcessInstanceByKey("callingProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> parentProcessInstances = searchHistoricProcessInstances("callingProcessId");
    assertThat(parentProcessInstances).hasSize(1);
    ProcessInstanceEntity parentProcessInstance = parentProcessInstances.getFirst();
    List<ProcessInstanceEntity> childProcessInstances = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(childProcessInstances).hasSize(1);
    ProcessInstanceEntity childProcessInstance = childProcessInstances.getFirst();
    Long childProcessInstanceKey = childProcessInstance.processInstanceKey();
    assertThat(childProcessInstance.parentProcessInstanceKey()).isEqualTo(parentProcessInstance.processInstanceKey());
    List<FlowNodeInstanceDbModel> childFlowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(childProcessInstanceKey);

    assertThat(childFlowNodes).isNotEmpty();
    for (FlowNodeInstanceDbModel flowNode : childFlowNodes) {
      assertThat(flowNode.flowNodeScopeKey())
          .isEqualTo(childProcessInstanceKey);
    }
  }

  @Test
  public void shouldSetPartitionIdForFlowNodes() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);

    assertThat(flowNodes).isNotEmpty()
        .allSatisfy(flowNode -> assertThat(flowNode.partitionId()).isEqualTo(C7_HISTORY_PARTITION_ID));
  }

  @Test
  public void shouldSetFlowNodeNameForFlowNodes() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);

    assertThat(flowNodes).hasSize(3)
        .extracting(FlowNodeInstanceDbModel::flowNodeName)
        .containsExactlyInAnyOrder("Start", "UserTaskName", "End");

  }

  private void deploySubprocessModel() {
    String process = "subProcess";
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_outsideSub")
        .subProcess("subprocess")
        .embeddedSubProcess()
        .startEvent("start_insideSub")
        .userTask(USER_TASK_ID)
        .endEvent("end_insideSub")
        .subProcessDone()
        .endEvent("end_outsideSub")
        .done();

    deployer.deployC7ModelInstance(process, c7Model);
  }
}
