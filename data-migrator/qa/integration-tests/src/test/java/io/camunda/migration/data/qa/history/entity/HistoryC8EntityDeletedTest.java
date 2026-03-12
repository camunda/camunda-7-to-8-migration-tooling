/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_DELETED_IN_C8;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_DELETED_IN_C8_ASSOCIATED;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_AUDIT_LOG;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

/**
 * White-box integration tests that verify {@code C8EntityNotFoundException} handling during history
 * migration. These tests simulate C8 history-cleanup by deleting already-migrated C8 entities
 * directly from the RDBMS and then assert that dependent entities are correctly skipped with the
 * expected log message.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>Process instance skipped when its process definition was deleted in C8</li>
 *   <li>Flow nodes skipped when their parent process instance was deleted in C8</li>
 *   <li>Decision definition skipped when its decision requirements were deleted in C8</li>
 *   <li>Decision instance skipped when its decision definition was deleted in C8</li>
 *   <li>Audit log skipped when its referenced user task was deleted in C8</li>
 *   <li>Job skipped when its async-after flow node was deleted in C8</li>
 * </ul>
 */
public class HistoryC8EntityDeletedTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  // ---- Process definition / process instance scenarios ----------------------------------------

  @Test
  @WhiteBox
  public void shouldSkipProcessInstanceWhenProcessDefinitionWasDeletedInC8() {
    // given – a simple process instance in history
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    String processInstanceId =
        runtimeService.startProcessInstanceByKey("simpleStartEndProcessId").getId();

    // migrate process definitions first so the mapping exists
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);

    // simulate C8 history cleanup by deleting the process definition record
    List<ProcessDefinitionEntity> definitions = searchHistoricProcessDefinitions("simpleStartEndProcessId");
    assertThat(definitions).hasSize(1);
    Long processDefinitionKey = definitions.getFirst().processDefinitionKey();
    rdbmsQuery.update("DELETE FROM PROCESS_DEFINITION WHERE PROCESS_DEFINITION_KEY = ?", processDefinitionKey);

    // when – migrate process instances after the process definition no longer exists in C8
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);

    // then – the process instance must be skipped with a "deleted in C8" message
    assertThat(searchHistoricProcessInstances("simpleStartEndProcessId")).isEmpty();
    logs.assertContains(formatMessage(
        SKIPPING,
        HISTORY_PROCESS_INSTANCE.getDisplayName(),
        processInstanceId,
        String.format(SKIP_REASON_DELETED_IN_C8, HISTORY_PROCESS_DEFINITION.getDisplayName(), processDefinitionKey)));
  }

  @Test
  @WhiteBox
  public void shouldSkipFlowNodesWhenProcessInstanceWasDeletedInC8() {
    // given – deploy and run a simple auto-completing process
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    String processInstanceId = runtimeService.startProcessInstanceByKey("simpleStartEndProcessId").getId();

    // migrate process definitions and process instances
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);

    // simulate C8 history cleanup by deleting the process instance record
    List<ProcessInstanceEntity> instances = searchHistoricProcessInstances("simpleStartEndProcessId");
    assertThat(instances).hasSize(1);
    Long processInstanceKey = instances.getFirst().processInstanceKey();
    rdbmsQuery.update("DELETE FROM PROCESS_INSTANCE WHERE PROCESS_INSTANCE_KEY = ?", processInstanceKey);

    // collect the C7 flow node IDs (start event + end event) before flow node migration
    List<String> flowNodeIds = historyService.createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .list()
        .stream()
        .map(HistoricActivityInstance::getId)
        .toList();
    assertThat(flowNodeIds).isNotEmpty();

    // when – migrate flow nodes after the process instance is gone from C8
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);

    // then – all flow nodes must be skipped because their parent process instance was deleted
    assertThat(searchHistoricFlowNodes(processInstanceKey)).isEmpty();
    for (String flowNodeId : flowNodeIds) {
      logs.assertContains(formatMessage(
          SKIPPING,
          HISTORY_FLOW_NODE.getDisplayName(),
          flowNodeId,
          String.format(SKIP_REASON_DELETED_IN_C8, HISTORY_PROCESS_INSTANCE.getDisplayName(), processInstanceKey)));
    }
  }

  // ---- Decision requirements / definition / instance scenarios --------------------------------

  @Test
  @WhiteBox
  public void shouldSkipDecisionDefinitionWhenDecisionRequirementsWasDeletedInC8() {
    // given – a DMN with an explicit DRD (simpleDmnWithReqsId)
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // migrate decision requirements so the mapping exists
    historyMigrator.migrateByType(HISTORY_DECISION_REQUIREMENT);

    List<DecisionRequirementsEntity> requirements =
        searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId");
    assertThat(requirements).hasSize(1);
    Long decisionRequirementsKey = requirements.getFirst().decisionRequirementsKey();

    // collect the C7 decision definition IDs before definition migration
    List<String> decisionDefinitionIds = repositoryService.createDecisionDefinitionQuery()
        .list().stream()
        .map(DecisionDefinition::getId)
        .toList();
    assertThat(decisionDefinitionIds).hasSize(2);

    // simulate C8 history cleanup by deleting the decision requirements record
    rdbmsQuery.update("DELETE FROM DECISION_REQUIREMENTS WHERE DECISION_REQUIREMENTS_KEY = ?", decisionRequirementsKey);

    // when – migrate decision definitions after requirements no longer exist in C8
    historyMigrator.migrateByType(HISTORY_DECISION_DEFINITION);

    // then – all decision definitions must be skipped with a "deleted in C8" message
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id")).isEmpty();
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id")).isEmpty();
    for (String definitionId : decisionDefinitionIds) {
      logs.assertContains(formatMessage(
          SKIPPING,
          HISTORY_DECISION_DEFINITION.getDisplayName(),
          definitionId,
          String.format(SKIP_REASON_DELETED_IN_C8,
              HISTORY_DECISION_REQUIREMENT.getDisplayName(), decisionRequirementsKey)));
    }
  }

  @Test
  @WhiteBox
  public void shouldSkipDecisionInstanceWhenDecisionDefinitionWasDeletedInC8() {
    // given – a standalone decision evaluated in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDecisionId", variables);

    // migrate requirements and definitions so the mapping exists
    historyMigrator.migrateByType(HISTORY_DECISION_REQUIREMENT);
    historyMigrator.migrateByType(HISTORY_DECISION_DEFINITION);

    List<DecisionDefinitionEntity> definitions = searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(definitions).hasSize(1);
    Long decisionDefinitionKey = definitions.getFirst().decisionDefinitionKey();

    // collect the C7 decision instance IDs before instance migration
    List<String> decisionInstanceIds = historyService.createHistoricDecisionInstanceQuery()
        .decisionDefinitionKey("simpleDecisionId")
        .list().stream()
        .map(di -> di.getId())
        .toList();
    assertThat(decisionInstanceIds).isNotEmpty();

    // simulate C8 history cleanup by deleting the decision definition record
    rdbmsQuery.update("DELETE FROM DECISION_DEFINITION WHERE DECISION_DEFINITION_KEY = ?", decisionDefinitionKey);

    // when – migrate decision instances after the definition no longer exists in C8
    historyMigrator.migrateByType(HISTORY_DECISION_INSTANCE);

    // then – all decision instances must be skipped with a "deleted in C8" message
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    for (String instanceId : decisionInstanceIds) {
      logs.assertContains(formatMessage(
          SKIPPING,
          HISTORY_DECISION_INSTANCE.getDisplayName(),
          instanceId,
          String.format(SKIP_REASON_DELETED_IN_C8,
              HISTORY_DECISION_DEFINITION.getDisplayName(), decisionDefinitionKey)));
    }
  }

  // ---- Audit log / user task scenario ---------------------------------------------------------

  @Test
  @WhiteBox
  public void shouldSkipAuditLogWhenUserTaskWasDeletedInC8() {
    // given – a process with a user task that produces audit log entries on completion
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // migrate process definitions, process instances, and user tasks so the mapping exists
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).isNotEmpty();
    Long userTaskKey = userTasks.getFirst().userTaskKey();

    // collect the operation IDs for audit log entries that reference this user task
    // (C7Entity uses operationId as the C7 ID for audit log entries)
    List<String> operationIds = historyService.createUserOperationLogQuery()
        .list().stream()
        .filter(entry -> entry.getTaskId() != null)
        .map(UserOperationLogEntry::getOperationId)
        .distinct()
        .toList();
    assertThat(operationIds).isNotEmpty();

    // simulate C8 history cleanup by deleting the user task record
    rdbmsQuery.update("DELETE FROM USER_TASK WHERE USER_TASK_KEY = ?", userTaskKey);

    // when – migrate audit logs after the user task no longer exists in C8
    historyMigrator.migrateByType(HISTORY_AUDIT_LOG);

    // then – audit log entries that reference the deleted user task must be skipped
    logs.assertContains(
        String.format(SKIP_REASON_DELETED_IN_C8, HISTORY_USER_TASK.getDisplayName(), userTaskKey));
  }

  // ---- Job / flow node scenario ---------------------------------------------------------------

  @Test
  @WhiteBox
  public void shouldSkipJobWhenFlowNodeWasDeletedInC8() {
    // given – a process where the start event has camundaAsyncAfter() so a flow node instance
    // is created in C8 history after the async-after job executes
    BpmnModelInstance c7Model = Bpmn.createExecutableProcess("asyncAfterProcess")
        .startEvent("startEvent")
        .camundaAsyncAfter()
        .serviceTask("serviceTaskId")
          .camundaClass("foo")
        .endEvent()
        .done();
    deployer.deployC7ModelInstance("asyncAfterProcess", c7Model);
    runtimeService.startProcessInstanceByKey("asyncAfterProcess");
    executeAllJobsWithRetry();

    // collect the C7 process instance ID for the log assertion
    String c7ProcessInstanceId = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey("asyncAfterProcess")
        .singleResult()
        .getId();

    // migrate process definitions, process instances, and flow nodes so the mapping exists
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);

    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("asyncAfterProcess");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // find the start event flow node instance (the async-after element)
    List<FlowNodeInstanceDbModel> flowNodes =
        searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(processInstanceKey);
    FlowNodeInstanceDbModel startEventFlowNode = flowNodes.stream()
        .filter(fn -> "startEvent".equals(fn.flowNodeId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected startEvent flow node instance to exist in C8"));
    Long flowNodeInstanceKey = startEventFlowNode.flowNodeInstanceKey();

    // simulate C8 history cleanup by deleting the flow node instance record
    rdbmsQuery.update("DELETE FROM FLOW_NODE_INSTANCE WHERE FLOW_NODE_INSTANCE_KEY = ?", flowNodeInstanceKey);

    // when – migrate jobs after the async-after flow node no longer exists in C8
    historyMigrator.migrateByType(HISTORY_JOB);

    // then – the job must be skipped because its async-after flow node was deleted
    logs.assertContains(
        String.format(SKIP_REASON_DELETED_IN_C8_ASSOCIATED,
            HISTORY_FLOW_NODE.getDisplayName(), c7ProcessInstanceId, "startEvent"));
  }
}
