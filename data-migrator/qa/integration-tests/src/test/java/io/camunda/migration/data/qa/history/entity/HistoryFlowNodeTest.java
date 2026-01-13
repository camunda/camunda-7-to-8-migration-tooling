/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

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
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;

@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryFlowNodeTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldGenerateTreePathForFlowNodes() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    
    ProcessInstanceEntity processInstance = processInstances.getFirst();
    Long processInstanceKey = processInstance.processInstanceKey();
    
    // Verify all flow node types have treePath set
    List<FlowNodeInstanceEntity> startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    FlowNodeInstanceEntity startEvent = startEvents.getFirst();
    assertThat(startEvent.treePath()).isNotNull().isEqualTo(processInstanceKey + "/" + startEvent.flowNodeInstanceKey());
    
    List<FlowNodeInstanceEntity> userTasks = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTasks).hasSize(1);
    FlowNodeInstanceEntity userTask = userTasks.getFirst();
    assertThat(userTask.treePath()).isNotNull().isEqualTo(processInstanceKey + "/" + userTask.flowNodeInstanceKey());
    
    List<FlowNodeInstanceEntity> endEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, END_EVENT);
    assertThat(endEvents).hasSize(1);
    FlowNodeInstanceEntity endEvent = endEvents.getFirst();
    assertThat(endEvent.treePath()).isNotNull().isEqualTo(processInstanceKey + "/" + endEvent.flowNodeInstanceKey());
  }

  @Test
  public void shouldMigrateCompletedFlowNodeState() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    List<FlowNodeInstanceEntity> flowNodes = historyMigration.searchHistoricFlowNodes(processInstances.getFirst().processInstanceKey());
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
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> userTasks = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTasks).singleElement().satisfies(userTask ->
      assertThat(userTask.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.TERMINATED)
    );
  }

  @Test
  public void shouldSetFlowNodeScopeKey() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    ProcessInstanceEntity processInstance = processInstances.getFirst();
    Long processInstanceKey = processInstance.processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes =
        historyMigration.searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);

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
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();
    // Subprocesses and the start inside a subprocess have the same createTime, leading to the start sometimes being
    // skipped on first round. Retrying skipped to avoid test flakiness
    historyMigration.getMigrator().setMode(MigratorMode.RETRY_SKIPPED);
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("subProcess");
    assertThat(processInstances).hasSize(1);
    ProcessInstanceEntity processInstance = processInstances.getFirst();
    Long processInstanceKey = processInstance.processInstanceKey();
    List<FlowNodeInstanceDbModel> flowNodes =
        historyMigration.searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
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
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> parentProcessInstances = historyMigration.searchHistoricProcessInstances("callingProcessId");
    assertThat(parentProcessInstances).hasSize(1);
    ProcessInstanceEntity parentProcessInstance = parentProcessInstances.getFirst();
    List<ProcessInstanceEntity> childProcessInstances = historyMigration.searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(childProcessInstances).hasSize(1);
    ProcessInstanceEntity childProcessInstance = childProcessInstances.getFirst();
    Long childProcessInstanceKey = childProcessInstance.processInstanceKey();
    assertThat(childProcessInstance.parentProcessInstanceKey()).isEqualTo(parentProcessInstance.processInstanceKey());
    List<FlowNodeInstanceDbModel> childFlowNodes =
        historyMigration.searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(childProcessInstanceKey);

    assertThat(childFlowNodes).isNotEmpty();
    for (FlowNodeInstanceDbModel flowNode : childFlowNodes) {
      assertThat(flowNode.flowNodeScopeKey())
          .isEqualTo(childProcessInstanceKey);
    }
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
