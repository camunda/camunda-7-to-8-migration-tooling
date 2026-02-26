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
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SUB_PROCESS;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryEventSubprocessMigrationTest extends HistoryAbstractElementMigrationTest {

  @Test
  public void shouldMigrateNonInterruptingEventSubprocess() {
    // given
    deployer.deployCamunda7Process("eventSubprocess.bpmn");
     runtimeService.startProcessInstanceByKey("eventSubprocessId");
    // Send signal event, to trigger the eventSubprocess in C7
    runtimeService.signalEventReceived("SignalEventName");

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("eventSubprocessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> start = searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(start).hasSize(2);
    assertThat(start.getFirst().state()).isEqualTo(COMPLETED);
    start.forEach(startEvent -> assertThat(startEvent.state()).isEqualTo(COMPLETED));
    List<FlowNodeInstanceEntity> subprocess = searchHistoricFlowNodesForType(processInstanceKey, SUB_PROCESS);
    assertThat(subprocess).hasSize(1);
    assertThat(subprocess.getFirst().state()).isEqualTo(TERMINATED);
  }

  @Test
  public void shouldMigrateInterruptingEventSubprocess() {
    // given
    deployer.deployCamunda7Process("interruptingEventSubprocess.bpmn");
    runtimeService.startProcessInstanceByKey("eventSubprocessId");
    // Send signal event, to trigger the eventSubprocess in C7
    runtimeService.signalEventReceived("SignalEventName");

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("eventSubprocessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> start = searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(start).hasSize(2);
    start.forEach(startEvent -> assertThat(startEvent.state()).isEqualTo(COMPLETED));
    List<FlowNodeInstanceEntity> subprocess = searchHistoricFlowNodesForType(processInstanceKey, SUB_PROCESS);
    assertThat(subprocess).hasSize(1);
    assertThat(subprocess.getFirst().state()).isEqualTo(TERMINATED);
  }

  @Test
  public void shouldMigrateTransactionAsUnspecified() {
    // given
    deployer.deployCamunda7Process("transaction.bpmn");
    runtimeService.startProcessInstanceByKey("transaction");

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("transaction");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes.stream().filter(fn -> fn.type().equals(UNKNOWN)).toList()).isNotEmpty()
        .allMatch(fn -> fn.state() == TERMINATED);
  }

}

