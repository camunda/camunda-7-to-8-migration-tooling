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
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationRetryTest extends HistoryMigrationAbstractTest {

  @Autowired
  private MigratorProperties migratorProperties;

  @AfterEach
  void resetMigratorProperties() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  public void shouldMigratePreviouslySkippedProcessDefinition() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    String c7Id = repositoryService.createProcessDefinitionQuery().singleResult().getId();
    markEntityAsSkipped(c7Id, HISTORY_PROCESS_DEFINITION);
    // when history migration is retried
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then process definition is migrated and no longer skipped
    assertThat(searchHistoricProcessDefinitions("userTaskProcessId").size()).isEqualTo(1);
    assertThat(dbClient.countSkippedByType(HISTORY_PROCESS_DEFINITION)).isEqualTo(0);
  }

  @Test
  public void shouldMigratePreviouslySkippedDecisionDefinition() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    String c7Id = repositoryService.createDecisionDefinitionQuery().singleResult().getId();
    markEntityAsSkipped(c7Id, IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION);

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
    markEntityAsSkipped(c7Id, IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);

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
    markEntityAsSkipped(procDefId, HISTORY_PROCESS_DEFINITION);
    markEntityAsSkipped(procInstId, HISTORY_PROCESS_INSTANCE);
    markEntityAsSkipped(actInstId, HISTORY_FLOW_NODE);
    markEntityAsSkipped(taskId, HISTORY_USER_TASK);
    markEntityAsSkipped(varId, HISTORY_VARIABLE);
    markEntityAsSkipped(incidentId, HISTORY_INCIDENT);

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

    markEntityAsSkipped(procInstId, HISTORY_PROCESS_INSTANCE);
    markEntityAsSkipped(actInstId, HISTORY_FLOW_NODE);
    markEntityAsSkipped(taskId, HISTORY_USER_TASK);

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

  @Test
  public void shouldUpdateSkipReasonOnRetry(CapturedOutput output) {
    // given: enable skip reason saving
    migratorProperties.setSaveSkipReason(true);

    // given: process instances in c7 but NO process definition deployed in c8
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    for (int i = 0; i < 3; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // when: initial migration - process instances should be skipped due to missing c8 process definition
    historyMigrator.migrate();

    // then: verify process instances were skipped (not migrated to c8)
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // and: verify skip reason by listing skipped entities
    historyMigrator.setMode(MigratorMode.LIST_SKIPPED);
    historyMigrator.setRequestedEntityTypes(List.of(HISTORY_PROCESS_INSTANCE));
    historyMigrator.start();
    
    // Verify initial skip output (skip reasons should be visible if saveSkipReason is enabled)
    assertThat(output.getOut()).contains("Previously skipped [Historic Process Instance]");

    // when: deploy process definition to c8 and retry migration
    deployer.deployCamunda8Process("userTaskProcess.bpmn");
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then: verify process instances are now successfully migrated
    var migratedInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(migratedInstances).hasSize(3);

    // and: verify no more skipped entities after successful migration
    historyMigrator.setMode(MigratorMode.LIST_SKIPPED);
    historyMigrator.setRequestedEntityTypes(List.of(HISTORY_PROCESS_INSTANCE));
    historyMigrator.start();
    
    assertThat(output.getOut()).contains("No entities of type [Historic Process Instances] were skipped");
  }

  private void markEntityAsSkipped(String c7Id, IdKeyMapper.TYPE type) {
    dbClient.insert(c7Id, null, type);
  }
}
