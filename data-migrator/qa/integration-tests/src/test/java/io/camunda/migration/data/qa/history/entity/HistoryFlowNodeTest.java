/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
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
}
