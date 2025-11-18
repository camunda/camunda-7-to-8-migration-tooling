/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.MigratorMode;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryMigrationRetryTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigratePreviouslySkippedProcessDefinition() {
    // given: A decision with requirements where requirements will be skipped first
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    String decisionRequirementsId = repositoryService.createDecisionRequirementsDefinitionQuery().singleResult().getId();
    
    // Simulate that decision requirements was previously skipped during an earlier migration attempt
    // This causes child decision definitions to be naturally skipped due to missing parent
    simulateSkippedEntity(decisionRequirementsId, IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);
    historyMigrator.migrate();
    
    // Verify decision definitions were skipped due to missing decision requirements
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION)).isEqualTo(2);
    
    // when: We retry migration which will now migrate the decision requirements and previously skipped definitions
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then: Previously skipped entities are now migrated
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id").size()).isEqualTo(1);
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id").size()).isEqualTo(1);
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId").size()).isEqualTo(1);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION)).isEqualTo(0);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT)).isEqualTo(0);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionDefinition() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    String c7Id = repositoryService.createDecisionDefinitionQuery().singleResult().getId();
    simulateSkippedEntity(c7Id, IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION);

    // when 
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionDefinitions("simpleDecisionId").size()).isEqualTo(1);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION)).isEqualTo(0);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionRequirementsDefinition() {
    // given
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    String c7Id = repositoryService.createDecisionRequirementsDefinitionQuery().singleResult().getId();
    simulateSkippedEntity(c7Id, IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);

    // when 
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId").size()).isEqualTo(1);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT)).isEqualTo(0);
  }

  @Test
  public void shouldMigrateOnlyPreviouslySkippedElementsOnRetry() {
    // given state in c7
    deployer.deployCamunda7Process("includeAllSupportedElementsProcess.bpmn");
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("allElementsProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    String procDefId = repositoryService.createProcessDefinitionQuery().singleResult().getId();
    String procInstId = historyService.createHistoricProcessInstanceQuery().list().getFirst().getId();
    String actInstId = historyService.createHistoricActivityInstanceQuery()
        .activityType("userTask")
        .processInstanceId(procInstId)
        .list()
        .getFirst()
        .getId();
    String taskId = historyService.createHistoricTaskInstanceQuery()
        .activityInstanceIdIn(actInstId)
        .list()
        .getFirst()
        .getId();
    String incidentId = historyService.createHistoricIncidentQuery()
        .processInstanceId(procInstId)
        .list()
        .getFirst()
        .getId();
    String varId = historyService.createHistoricVariableInstanceQuery()
        .activityInstanceIdIn(actInstId)
        .singleResult()
        .getId();
    simulateSkippedEntity(procDefId, HISTORY_PROCESS_DEFINITION);
    simulateSkippedEntity(procInstId, HISTORY_PROCESS_INSTANCE);
    simulateSkippedEntity(actInstId, HISTORY_FLOW_NODE);
    simulateSkippedEntity(taskId, HISTORY_USER_TASK);
    simulateSkippedEntity(varId, HISTORY_VARIABLE);
    simulateSkippedEntity(incidentId, HISTORY_INCIDENT);

    // when migration is retried
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then only previously skipped entities are migrated
    assertThat(searchHistoricProcessDefinitions("allElementsProcessId").size()).isEqualTo(1);
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("allElementsProcessId");
    assertThat(processInstances.size()).isEqualTo(1);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey()).size()).isEqualTo(1);
    assertThat(searchHistoricIncidents("allElementsProcessId").size()).isEqualTo(1);
    assertThat(searchHistoricVariables("userTaskVar").size()).isEqualTo(1);

    // and nothing marked as skipped
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(procDefId, HISTORY_PROCESS_DEFINITION)).isTrue();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(procInstId, HISTORY_PROCESS_INSTANCE)).isTrue();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(actInstId, HISTORY_FLOW_NODE)).isTrue();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(taskId, HISTORY_USER_TASK)).isTrue();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(incidentId, HISTORY_INCIDENT)).isTrue();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(varId, HISTORY_VARIABLE)).isTrue();
  }

  @Test
  public void shouldNotMigratePreviouslySkippedElementsOnRerun() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // and some entities manually set as skipped
    String procInstId = historyService.createHistoricProcessInstanceQuery().list().getFirst().getId();
    String actInstId = historyService.createHistoricActivityInstanceQuery()
        .activityType("userTask")
        .processInstanceId(procInstId)
        .list()
        .getFirst()
        .getId();
    String taskId = historyService.createHistoricTaskInstanceQuery()
        .activityInstanceIdIn(actInstId)
        .list()
        .getFirst()
        .getId();

    simulateSkippedEntity(procInstId, HISTORY_PROCESS_INSTANCE);
    simulateSkippedEntity(actInstId, HISTORY_FLOW_NODE);
    simulateSkippedEntity(taskId, HISTORY_USER_TASK);

    // when migration is run on migrate mode
    historyMigrator.migrate();

    // then only non skipped entities are migrated
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId").size()).isEqualTo(1);
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances.size()).isEqualTo(4);

    // and skipped entities are still skipped
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(procInstId, HISTORY_PROCESS_INSTANCE)).isFalse();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(actInstId, HISTORY_FLOW_NODE)).isFalse();
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(taskId, HISTORY_USER_TASK)).isFalse();
  }
}
