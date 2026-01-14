/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.TERMINATED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.EVENT_BASED_GATEWAY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.PARALLEL_GATEWAY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HistoryGatewayMigrationTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected RuntimeService runtimeService;

  @Test
  public void shouldMigrateEventBasedActivityInstance() {
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
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/321")
  public void shouldSkipActiveParallelGatewayActivityInstance() {
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
    assertThat(flowNodes.size()).isEqualTo(2);
  }

  // TODO exclusive gateway

  // TODO inclusive gateway
}

