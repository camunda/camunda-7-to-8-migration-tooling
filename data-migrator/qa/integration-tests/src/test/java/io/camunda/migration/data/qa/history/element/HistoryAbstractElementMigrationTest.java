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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class HistoryAbstractElementMigrationTest extends HistoryMigrationAbstractTest {

  public static final String PROCESS = "process";

  @EnabledIf("hasScenarios_terminatedElementPostMigration")
  @MethodSource("elementScenarios_terminatedElementPostMigration")
  @ParameterizedTest
  public void migrateSimpleElementScenarios_expectTerminatedElement(final String processFile,
                                                                    final String processId,
                                                                    final FlowNodeInstanceEntity.FlowNodeType elementType) {
    // given
    deployer.deployCamunda7Process(processFile);
    runtimeService.startProcessInstanceByKey(processId);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(processId);
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes.stream().filter(fn -> fn.type().equals(elementType)).toList())
        .isNotEmpty()
        .allMatch(fn -> fn.state() == TERMINATED);
  }

  @EnabledIf("hasScenarios_completedElementPostMigration")
  @MethodSource("elementScenarios_completedElementPostMigration")
  @ParameterizedTest
  public void migrateSimpleElementScenarios_expectCompletedElement(final String processFile,
                                                                   final String processId,
                                                                    final FlowNodeInstanceEntity.FlowNodeType elementType) {
    // given
    deployer.deployCamunda7Process(processFile);
    runtimeService.startProcessInstanceByKey(processId);

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(processId);
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes.stream().filter(fn -> fn.type().equals(elementType)).toList())
        .hasSize(1)
        .allMatch(fn -> fn.state() == COMPLETED);
  }

  /**
   * Test cases for elements with a real-world wait state in C7 and C8.
   * Post migration we expect an active process instance in the same element.
   *
   * @return Stream of 3 String arguments: processFile, processId, elementId
   */
  protected Stream<Arguments> elementScenarios_terminatedElementPostMigration() {
    return Stream.empty();
  }

  /**
   * Test cases for elements with a wait state in C7 but no wait state in C8.
   * Post migration we expect the process instance to have completed the element.
   *
   * @return Stream of 3 String arguments: processFile, processId, elementId
   */
  protected Stream<Arguments> elementScenarios_completedElementPostMigration() {
    return Stream.empty();
  }

  protected boolean hasScenarios_terminatedElementPostMigration(){
    return elementScenarios_terminatedElementPostMigration().findAny().isPresent();
  }

  protected boolean hasScenarios_completedElementPostMigration(){
    return elementScenarios_completedElementPostMigration().findAny().isPresent();
  }
}

