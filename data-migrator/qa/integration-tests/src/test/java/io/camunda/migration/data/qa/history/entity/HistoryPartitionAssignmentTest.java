/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying correct partition ID assignment during history migration.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Process hierarchies (call activities) share the same partition as the root process instance</li>
 *   <li>All process-related entities (flow nodes, user tasks, variables) share the same partition as their process instance</li>
 *   <li>Standalone decisions and their child decision instances share the same partition</li>
 * </ul>
 *
 * <p>White-box tests for {@code C8EntityNotFoundException} scenarios are in
 * {@link HistoryC8EntityDeletedTest}.
 */
public class HistoryPartitionAssignmentTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldCallActivityProcessHierarchyShareSamePartitionAsRootProcessInstance() {
    // given – a parent process that calls a child process via call activity
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    runtimeService.startProcessInstanceByKey("callingProcessId");

    // when
    historyMigrator.migrate();

    // then – both the parent and child process instances should be migrated
    List<ProcessInstanceEntity> parentInstances = searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> childInstances = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(parentInstances).hasSize(1);
    assertThat(childInstances).hasSize(1);

    Long parentKey = parentInstances.getFirst().processInstanceKey();
    Long childKey = childInstances.getFirst().processInstanceKey();

    int parentPartition = searchProcessInstancePartitionId(parentKey);
    int childPartition = searchProcessInstancePartitionId(childKey);

    // parent and child process instances must share the same partition
    assertThat(childPartition).isEqualTo(parentPartition);

    // all flow nodes in both processes must share the same partition as the root process instance
    List<FlowNodeInstanceDbModel> parentFlowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(parentKey);
    List<FlowNodeInstanceDbModel> childFlowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(childKey);

    assertThat(parentFlowNodes).isNotEmpty()
        .allSatisfy(fn -> assertThat(fn.partitionId()).isEqualTo(parentPartition));
    assertThat(childFlowNodes).isNotEmpty()
        .allSatisfy(fn -> assertThat(fn.partitionId()).isEqualTo(parentPartition));
  }

  @Test
  public void shouldProcessRelatedEntitiesShareSamePartitionAsProcessInstance() {
    // given – a process with a user task and a process variable
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    Map<String, Object> processVariables = Variables.createVariables().putValue("testVar", stringValue("value"));
    runtimeService.startProcessInstanceByKey("userTaskProcessId", processVariables);
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    int processPartition = searchProcessInstancePartitionId(processInstanceKey);

    // all flow nodes must share the same partition as the process instance
    List<FlowNodeInstanceDbModel> flowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    assertThat(flowNodes).isNotEmpty()
        .allSatisfy(fn -> assertThat(fn.partitionId()).isEqualTo(processPartition));

    // all user tasks must share the same partition as the process instance
    List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).isNotEmpty()
        .allSatisfy(task -> assertThat(searchUserTaskPartitionId(task.userTaskKey())).isEqualTo(processPartition));

    // all variables (including the migrator-inserted legacy ID variable) must share the same partition
    List<VariableEntity> variables = searchHistoricVariables(processInstanceKey);
    assertThat(variables).isNotEmpty()
        .allSatisfy(v -> assertThat(searchVariablePartitionId(v.variableKey())).isEqualTo(processPartition));
  }

  @Test
  public void shouldStandaloneDecisionChildrenShareSamePartitionAsParentDecision() {
    // given – a DRD where decision2 depends on decision1; evaluated standalone (no BPMN process)
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDmnWithReqs2Id", variables);

    // when
    historyMigrator.migrate();

    // then – both parent and child decision instances should be present
    List<DecisionDefinitionEntity> definitions1 = searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id");
    List<DecisionDefinitionEntity> definitions2 = searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id");
    assertThat(definitions1).hasSize(1);
    assertThat(definitions2).hasSize(1);

    List<DecisionInstanceEntity> parentInstances = searchHistoricDecisionInstances("simpleDmnWithReqs2Id");
    List<DecisionInstanceEntity> childInstances = searchHistoricDecisionInstances("simpleDmnWithReqs1Id");
    assertThat(parentInstances).hasSize(1);
    assertThat(childInstances).hasSize(1);

    Long parentDecisionInstanceKey = parentInstances.getFirst().decisionInstanceKey();
    Long childDecisionInstanceKey = childInstances.getFirst().decisionInstanceKey();

    // child inherits the parent's decisionInstanceKey (set by the migrator)
    assertThat(childDecisionInstanceKey).isEqualTo(parentDecisionInstanceKey);

    // all rows stored under this decision instance key must have the same partition
    List<Integer> allPartitions = searchDecisionInstancePartitionIds(parentDecisionInstanceKey);
    assertThat(allPartitions).isNotEmpty()
        .allSatisfy(partitionId -> assertThat(partitionId).isEqualTo(allPartitions.getFirst()));
  }

}
