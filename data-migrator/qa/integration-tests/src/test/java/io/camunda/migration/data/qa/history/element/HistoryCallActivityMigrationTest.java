/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.TERMINATED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.CALL_ACTIVITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryCallActivityMigrationTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigrateCallActivity() {
    // given
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    runtimeService.startProcessInstanceByKey("callingProcessId");

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> callingProcessInstances = searchHistoricProcessInstances("callingProcessId");
    assertThat(callingProcessInstances).hasSize(1);
    Long callingProcessInstanceKey = callingProcessInstances.getFirst().processInstanceKey();

    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(callingProcessInstanceKey, CALL_ACTIVITY);
    assertThat(flowNodes.size()).isEqualTo(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(TERMINATED);
  }

}

