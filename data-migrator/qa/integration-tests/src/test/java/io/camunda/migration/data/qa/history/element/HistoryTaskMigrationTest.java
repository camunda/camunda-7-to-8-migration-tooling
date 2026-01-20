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
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BUSINESS_RULE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.MANUAL_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.MULTI_INSTANCE_BODY;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.RECEIVE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SCRIPT_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SEND_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SERVICE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.stream.Stream;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HistoryTaskMigrationTest extends HistoryAbstractElementMigrationTest {

  @Override
  protected Stream<Arguments> elementScenarios_terminatedElementPostMigration() {
    return Stream.of(Arguments.of("sendTaskProcess.bpmn", "sendTaskProcessId", SEND_TASK),
        Arguments.of("receiveTaskProcess.bpmn", "receiveTaskProcessId", RECEIVE_TASK),
        Arguments.of("userTaskProcess.bpmn", "userTaskProcessId", USER_TASK));
  }

  @Override
  protected Stream<Arguments> elementScenarios_completedElementPostMigration() {
    return Stream.of(Arguments.of("parallelGateway.bpmn", "ParallelGatewayProcess", TASK),
        Arguments.of("serviceTaskProcessExpr.bpmn", "serviceTaskProcessId", SERVICE_TASK));
  }

  @Test
  public void shouldMigrateBusinessRuleTask() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");

    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("FAIL")));

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("businessRuleProcessId");
    assertThat(processInstances.size()).isEqualTo(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, BUSINESS_RULE_TASK);
    assertThat(flowNodes.size()).isEqualTo(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldMigrateScriptTask() {
    // given
    deployModelWithScriptTask();
    runtimeService.startProcessInstanceByKey(PROCESS);

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances.size()).isEqualTo(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, SCRIPT_TASK);
    assertThat(flowNodes.size()).isEqualTo(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldMigrateManualTask() {
    // given
    deployModelManualTask();
    runtimeService.startProcessInstanceByKey(PROCESS);

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(PROCESS);
    assertThat(processInstances.size()).isEqualTo(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, MANUAL_TASK);
    assertThat(flowNodes.size()).isEqualTo(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(COMPLETED);
  }

  @Test
  public void shouldMigrateMultiInstance() {
    // given
    deployer.deployCamunda7Process("miProcess.bpmn");

    runtimeService.startProcessInstanceByKey("miProcess");

    // when
    historyMigrator.start();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("miProcess");
    assertThat(processInstances.size()).isEqualTo(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodesForType(processInstanceKey, MULTI_INSTANCE_BODY);
    assertThat(flowNodes.size()).isEqualTo(1);
    assertThat(flowNodes.getFirst().state()).isEqualTo(TERMINATED);
  }


  protected void deployModelWithScriptTask() {
    String process = PROCESS;
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent()
        .scriptTask()
        .scriptText("print(\"expected in test\")")
        .endEvent()
        .done();

    deployer.deployC7ModelInstance(process, c7Model);
  }

  protected void deployModelManualTask() {
    String process = PROCESS;
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent()
        .manualTask()
        .intermediateThrowEvent()
        .endEvent()
        .done();

    deployer.deployC7ModelInstance(process, c7Model);
  }
}

