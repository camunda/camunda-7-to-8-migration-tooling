/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.COMPLETED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INTERMEDIATE_THROW_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HistoryBoundryMigrationTest extends HistoryAbstractElementMigrationTest {

  @Test
  public void shouldMigrateIntermediateThrow() {
    // given
    deployModel();
    runtimeService.startProcessInstanceByKey(PROCESS);

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances.size()).isEqualTo(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey,
        INTERMEDIATE_THROW_EVENT);
    assertThat(flowNodes.size()).isEqualTo(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  protected void deployModel() {
    String process = PROCESS;
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent()
        .intermediateThrowEvent()
        .signal("foo")
        .endEvent()
        .done();

    deployer.deployC7ModelInstance(process, c7Model);
  }
}

