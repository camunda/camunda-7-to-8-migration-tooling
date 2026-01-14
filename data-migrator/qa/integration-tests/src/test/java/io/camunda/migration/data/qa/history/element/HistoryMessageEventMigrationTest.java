/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INTERMEDIATE_CATCH_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryMessageEventMigrationTest extends HistoryAbstractElementMigrationTest {

  @Test
  public void shouldMigrateMessageCatchEvent() {
    // given
    deployer.deployCamunda7Process("messageCatchEventProcess.bpmn");
    runtimeService.startProcessInstanceByKey("messageCatchEventProcessId");

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("messageCatchEventProcessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, INTERMEDIATE_CATCH_EVENT);
    assertThat(flowNodes.size()).isEqualTo(1);
  }

}

