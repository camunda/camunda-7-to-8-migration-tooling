/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.INTERMEDIATE_CATCH_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.VariableEntity;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HistoryMigrationOrderedByCreateTimeTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldMigrateProcessDefinitionsDeployedBetweenRuns() {
    // given
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    Supplier<List<ProcessDefinitionEntity>> definitionSupplier = () -> historyMigration.searchHistoricProcessDefinitions(
        "simpleStartEndProcessId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(definitionSupplier.get()).singleElement();

    // given
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(definitionSupplier.get()).hasSize(2);
  }

  @Test
  public void shouldMigrateProcessDefinitionsWithSameDeploymentDate() {
    // given
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    ClockUtil.setCurrentTime(new Date());
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    ClockUtil.offset(1_000 * 4L);
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricProcessDefinitions("simpleStartEndProcessId")).hasSize(3);
  }

  @Test
  public void shouldMigrateDecisionDefinitionsDeployedBetweenRuns() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    Supplier<List<DecisionDefinitionEntity>> definitionSupplier = () -> historyMigration.searchHistoricDecisionDefinitions("simpleDecisionId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(definitionSupplier.get()).singleElement();

    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(definitionSupplier.get()).hasSize(2);
  }

  @Test
  public void shouldMigrateDecisionDefinitionsWithSameDeploymentDate() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    ClockUtil.setCurrentTime(new Date());
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    ClockUtil.offset(1_000 * 4L);
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricDecisionDefinitions("simpleDecisionId")).hasSize(3);
  }

  @Test
  public void shouldMigrateProcessInstancesStartedBetweenRuns() {
    // given
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");
    Supplier<List<ProcessInstanceEntity>> instanceSupplier = () -> historyMigration.searchHistoricProcessInstances(
        "simpleStartEndProcessId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(instanceSupplier.get()).singleElement();

    // given
    runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(instanceSupplier.get()).hasSize(2);
  }

  @Test
  public void shouldMigrateProcessInstancesWithSameCreateTime() {
    // given
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");
    runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");

    ClockUtil.offset(1_000 * 4L);
    runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricProcessInstances("simpleStartEndProcessId")).hasSize(4);
  }

  @Test
  public void shouldMigrateUserTasksStartedBetweenRuns() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();
    Supplier<List<ProcessInstanceEntity>> instanceSupplier = () -> historyMigration.searchHistoricProcessInstances("userTaskProcessId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(instanceSupplier.get()).singleElement()
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .satisfies(c8Key -> assertThat(historyMigration.searchHistoricUserTasks(c8Key)).singleElement());

    // given
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(instanceSupplier.get()).hasSize(2)
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .allSatisfy(c8Key -> assertThat(historyMigration.searchHistoricUserTasks(c8Key)).singleElement());
  }

  @Test
  public void shouldMigrateUserTasksWithSameCreateTime() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    ClockUtil.offset(1_000 * 4L);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricProcessInstances("userTaskProcessId")).hasSize(3)
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .allSatisfy(c8Key -> assertThat(historyMigration.searchHistoricUserTasks(c8Key)).singleElement());
  }

  @Test
  public void shouldMigrateFlowNodesStartedBetweenRuns() {
    // given
    deployer.deployCamunda7Process("multipleSignalProcess.bpmn");
    runtimeService.startProcessInstanceByKey("multipleSignalProcessId");
    runtimeService.signalEventReceived("signalRef1");
    Supplier<List<ProcessInstanceEntity>> proceInstSupplier = () -> historyMigration.searchHistoricProcessInstances(
        "multipleSignalProcessId");
    Function<Long, List<FlowNodeInstanceEntity>> flownodeSupplier = procInstanceKey -> historyMigration.searchHistoricFlowNodesForType(
        procInstanceKey, INTERMEDIATE_CATCH_EVENT);

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = proceInstSupplier.get();
    assertThat(processInstances).singleElement();
    long processInstanceKey = processInstances.get(0).processInstanceKey();
    assertThat(flownodeSupplier.apply(processInstanceKey)).hasSize(2)
        .extracting(FlowNodeInstanceEntity::flowNodeId)
        .containsExactlyInAnyOrder("signal1Id", "signal2Id");

    // given
    runtimeService.signalEventReceived("signalRef2");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(flownodeSupplier.apply(processInstanceKey)).hasSize(3)
        .extracting(FlowNodeInstanceEntity::flowNodeId)
        .containsExactlyInAnyOrder("signal1Id", "signal2Id", "signal3Id");
  }

  @Test
  public void shouldMigrateFlowNodesWithSameCreateTime() {
    // given
    deployer.deployCamunda7Process("multipleSignalProcess.bpmn");
    runtimeService.startProcessInstanceByKey("multipleSignalProcessId");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.signalEventReceived("signalRef1");

    ClockUtil.offset(1_000 * 4L);
    runtimeService.signalEventReceived("signalRef2");

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> instance = historyMigration.searchHistoricProcessInstances("multipleSignalProcessId");
    assertThat(instance).singleElement();
    assertThat(historyMigration.searchHistoricFlowNodesForType(instance.get(0).processInstanceKey(), INTERMEDIATE_CATCH_EVENT)).hasSize(
            3)
        .extracting(FlowNodeInstanceEntity::flowNodeId)
        .containsExactlyInAnyOrder("signal1Id", "signal2Id", "signal3Id");
  }

  @Test
  public void shouldMigrateIncidentsCreatedBetweenRuns() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    String instanceId = runtimeService.startProcessInstanceByKey("incidentProcessId").getProcessInstanceId();
    triggerIncident(instanceId);
    Supplier<List<IncidentEntity>> incidentSupplier = () -> historyMigration.searchHistoricIncidents("incidentProcessId");

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(incidentSupplier.get()).singleElement();

    // given
    instanceId = runtimeService.startProcessInstanceByKey("incidentProcessId").getProcessInstanceId();
    triggerIncident(instanceId);

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(incidentSupplier.get()).hasSize(2);
  }

  @Test
  public void shouldMigrateIncidentsWithSameCreationDate() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");

    ClockUtil.setCurrentTime(new Date());
    String processInstanceId1 = runtimeService.startProcessInstanceByKey("incidentProcessId").getProcessInstanceId();
    triggerIncident(processInstanceId1);
    String processInstanceId2 = runtimeService.startProcessInstanceByKey("incidentProcessId").getProcessInstanceId();
    triggerIncident(processInstanceId2);

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricIncidents("incidentProcessId")).hasSize(2);
  }

  @Test
  public void shouldMigrateVariablesCreatedBetweenRuns() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    VariableMap variables = Variables.createVariables().putValue("stringVar", "myStringVar");
    Supplier<List<VariableEntity>> variableSupplier = () -> historyMigration.searchHistoricVariables("stringVar");
    runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(variableSupplier.get()).singleElement();

    // given
    runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(variableSupplier.get()).hasSize(2);
  }

  @Test
  public void shouldMigrateVariablesWithSameCreationDate() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    VariableMap variables = Variables.createVariables().putValue("stringVar", "myStringVar");
    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess", variables);
    runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricVariables("stringVar")).hasSize(2);
  }


}
