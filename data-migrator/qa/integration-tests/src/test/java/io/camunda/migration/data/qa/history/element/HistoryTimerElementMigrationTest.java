/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.COMPLETED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BOUNDARY_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

public class HistoryTimerElementMigrationTest extends HistoryAbstractElementMigrationTest {

  @Test
  public void shouldMigrateTimerInterruptingBoundary() {
    // given
    deployer.deployCamunda7Process("timerDurationBoundaryEventProcess.bpmn");
    ProcessInstance c7instance = runtimeService.startProcessInstanceByKey("timerDurationBoundaryEventProcessId");
    runtimeService.setVariable(c7instance.getId(), "leftoverDuration", "P0D");
    // fire timer job
    var jobs = managementService.createJobQuery().processInstanceId(c7instance.getId()).list();
    managementService.executeJob(jobs.getFirst().getId());

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("timerDurationBoundaryEventProcessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, BOUNDARY_EVENT);
    assertThat(flowNodes).hasSize(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }
}

