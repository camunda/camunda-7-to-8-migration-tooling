/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.COMPLETED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.TERMINATED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.EVENT_BASED_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.EXCLUSIVE_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INCLUSIVE_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.PARALLEL_GATEWAY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HistoryGatewayMigrationTest extends HistoryAbstractElementMigrationTest {

  @Autowired
  protected RuntimeService runtimeService;

  @Test
  public void shouldMigrateEventBasedGateway() {
    // given
    deployer.deployCamunda7Process("eventGateway.bpmn");

    // For C8 correlation variables are required
    Map<String, Object> variables = Variables.createVariables()
        .putValue("catchEvent1CorrelationVariable", "12345")
        .putValue("catchEvent2CorrelationVariable", 99.9);

    runtimeService.startProcessInstanceByKey("eventGatewayProcessId", variables);

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("eventGatewayProcessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes.stream().filter(fn -> fn.type().equals(EVENT_BASED_GATEWAY)).toList()).isNotEmpty()
        .allMatch(fn -> fn.state() == TERMINATED);
  }

  @Test
  public void shouldMigrateParallelGateway() {
    // given
    deployer.deployCamunda7Process("parallelGateway.bpmn");

    runtimeService.startProcessInstanceByKey("ParallelGatewayProcess");

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("ParallelGatewayProcess");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, PARALLEL_GATEWAY);
    assertThat(flowNodes).hasSize(2);
  }

  @Test
  public void shouldMigrateExclusiveGateway() {
    // given
    deployModelExclusiveGateway();
    runtimeService.startProcessInstanceByKey(PROCESS, Variables.createVariables().putValue("testVar", "outputValue"));

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, EXCLUSIVE_GATEWAY);
    assertThat(flowNodes).hasSize(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldMigrateInclusiveGateway() {
    // given
    deployModelInclusiveGateway();
    runtimeService.startProcessInstanceByKey(PROCESS);

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, INCLUSIVE_GATEWAY);
    assertThat(flowNodes).hasSize(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  protected void deployModelExclusiveGateway() {
    String process = PROCESS;
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent()
        .exclusiveGateway()
        .condition("Condition_1", "${testVar == 'outputValue'}")
        .userTask("afterMessage")
        .endEvent("happyEnd")
        .moveToLastGateway()
        .condition("Condition_2", "${testVar != 'outputValue'}")
        .userTask("wrongOutcome")
        .endEvent("unhappyEnd")
        .done();

    deployer.deployC7ModelInstance(process, c7Model);
  }

  protected void deployModelInclusiveGateway() {
    String process = PROCESS;
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent()
        .inclusiveGateway("fork")
        .userTask("parallel1")
        .inclusiveGateway("join")

        .moveToNode("fork")
        .userTask("parallel2")
        .connectTo("join")

        .endEvent()
        .done();

    deployer.deployC7ModelInstance(process, c7Model);
  }
}

