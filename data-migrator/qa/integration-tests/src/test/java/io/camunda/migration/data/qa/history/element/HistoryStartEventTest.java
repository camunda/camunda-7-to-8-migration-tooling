/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.COMPLETED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class HistoryStartEventTest extends HistoryAbstractElementMigrationTest {

  @Override
  protected Stream<Arguments> elementScenarios_completedElementPostMigration() {
    return Stream.of(Arguments.of("noneStartProcess.bpmn", "noneStartProcess", START_EVENT));
  }

  @Test
  public void shouldMigrateProcessWithStartMessageEvent() {
    // given
    deployer.deployCamunda7Process("messageStartEventProcess.bpmn");
    runtimeService.correlateMessage("msgRef");

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("MessageStartEventProcessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(flowNodes).hasSize(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldMigrateNoneStartEvent() {
    // given
    deployer.deployCamunda7Process("multipleStartEvent.bpmn");

    String id = runtimeService.startProcessInstanceByKey("multipleStartEvent").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).as(
        "Unexpected process state: process instance should exist").isNotNull();

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("multipleStartEvent");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(flowNodes).hasSize(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldMigrateNoneStartEventInSubprocess() {
    // given
    deployer.deployCamunda7Process("messageStartEventWithSubprocess.bpmn");

    String id = runtimeService.startProcessInstanceByKey("messageStartEventWithSubprocess").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).as(
        "Unexpected process state: process instance should exist").isNotNull();

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("messageStartEventWithSubprocess");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(flowNodes).hasSize(2);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }
}

