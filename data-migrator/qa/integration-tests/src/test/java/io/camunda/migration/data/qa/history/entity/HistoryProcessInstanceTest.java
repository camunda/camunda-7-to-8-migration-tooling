/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_LEGACY_PREFIX;
import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.MIGRATION_COMPLETED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.VariableQuery;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

public class HistoryProcessInstanceTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Test
  public void shouldMigrateProcessInstance() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    verifyProcessInstanceFields(processInstances.getFirst(), historicProcessInstance, "userTaskProcessId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, "custom-version-tag", C8_DEFAULT_TENANT, false, false,
        true);
    List<VariableEntity> variableEntities = searchHistoricVariables(LEGACY_ID_VAR_NAME);
    assertThat(variableEntities).hasSize(1);
    assertThat(variableEntities.getFirst().value()).isEqualTo("\"" + c7Process.getId() + "\"");
    assertThat(variableEntities.getFirst().processInstanceKey()).isEqualTo(processInstances.getFirst().processInstanceKey());
  }

  @Test
  public void shouldMigrateProcessInstanceAsCancelled() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    verifyProcessInstanceFields(processInstances.getFirst(), historicProcessInstance, "userTaskProcessId",
        ProcessInstanceEntity.ProcessInstanceState.CANCELED, "custom-version-tag", C8_DEFAULT_TENANT, false, false,
        false);
  }

  @Test
  public void shouldMigrateProcessInstancesWithTenant() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn", "my-tenant1");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    verifyProcessInstanceFields(processInstances.getFirst(), historicProcessInstance, "userTaskProcessId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, "custom-version-tag", "my-tenant1", false, false, true);
  }

  @Test
  public void shouldMigrateCallActivityAndSubprocess() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance subInstance = runtimeService.createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();
    completeAllUserTasksWithDefaultUserTaskId();
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(parentInstance.getId())
        .singleResult();
    HistoricProcessInstance historicSubProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(subInstance.getId())
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> parentProcessInstance = searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> subProcessInstance = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(parentProcessInstance).hasSize(1);
    assertThat(subProcessInstance).hasSize(1);

    var parent = parentProcessInstance.getFirst();
    verifyProcessInstanceFields(parent, historicProcessInstance, "callingProcessId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, null, C8_DEFAULT_TENANT, false, false, true);

    var sub = subProcessInstance.getFirst();
    verifyProcessInstanceFields(sub, historicSubProcessInstance, "calledProcessInstanceId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, null, C8_DEFAULT_TENANT, true, false, true);

  }

  @Test
  public void shouldMigrateCallActivityAndSubprocessLegacyIdVariable() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance subInstance = runtimeService.createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> parentProcessInstance = searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> subProcessInstance = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(parentProcessInstance).hasSize(1);
    assertThat(subProcessInstance).hasSize(1);

    List<VariableEntity> parentVariables = searchHistoricVariables(parentProcessInstance.getFirst().processInstanceKey());
    assertThat(parentVariables).hasSize(1);
    assertThat(parentVariables.getFirst().name()).isEqualTo(LEGACY_ID_VAR_NAME);
    assertThat(parentVariables.getFirst().value()).isEqualTo("\"" + parentInstance.getId() + "\"");
    assertThat(parentVariables.getFirst().rootProcessInstanceKey()).isEqualTo(parentProcessInstance.getFirst().processInstanceKey());

    List<VariableEntity> subVariables = searchHistoricVariables(subProcessInstance.getFirst().processInstanceKey());
    assertThat(subVariables).hasSize(1);
    assertThat(subVariables.getFirst().name()).isEqualTo(LEGACY_ID_VAR_NAME);
    assertThat(subVariables.getFirst().value()).isEqualTo("\"" + subInstance.getId() + "\"");
    assertThat(subVariables.getFirst().rootProcessInstanceKey()).isEqualTo(parentProcessInstance.getFirst().processInstanceKey());
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/428")
  public void shouldMigrateProcessInstanceWithIncident() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("incidentProcessId");
    triggerIncident(c7Process.getId());
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("incidentProcessId");
    assertThat(processInstances).hasSize(1);
    var processInstance = processInstances.getFirst();

    verifyProcessInstanceFields(processInstance, historicProcessInstance, "incidentProcessId",
        ProcessInstanceEntity.ProcessInstanceState.CANCELED, null, C8_DEFAULT_TENANT, false, true, false);
  }

  @Test
  public void shouldPopulateTreePathForCallActivity() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    runtimeService.startProcessInstanceByKey("callingProcessId");

    // when
    historyMigrator.migrate();
    historyMigrator.retry();

    // then
    List<ProcessInstanceEntity> parentProcessInstances = searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> subProcessInstances = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(parentProcessInstances).hasSize(1);
    assertThat(subProcessInstances).hasSize(1);

    ProcessInstanceEntity parent = parentProcessInstances.getFirst();
    ProcessInstanceEntity sub = subProcessInstances.getFirst();

    assertThat(parent.treePath()).isNotNull().isEqualTo("PI_" + parent.processInstanceKey());

    assertThat(sub.treePath()).isNotNull()
        .isEqualTo("PI_" + parent.processInstanceKey() + "/PI_" + sub.processInstanceKey());
  }

  @Test
  public void shouldPopulateRootProcessInstanceKeyForCallActivity() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    runtimeService.startProcessInstanceByKey("callingProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> parentProcessInstances = searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> subProcessInstances = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(parentProcessInstances).hasSize(1);
    assertThat(subProcessInstances).hasSize(1);

    ProcessInstanceEntity parent = parentProcessInstances.getFirst();
    ProcessInstanceEntity sub = subProcessInstances.getFirst();

    assertThat(parent.rootProcessInstanceKey()).isEqualTo(parent.processInstanceKey());

    // Sub process should have rootProcessInstanceKey pointing to the parent
    assertThat(sub.rootProcessInstanceKey()).isEqualTo(parent.processInstanceKey());
    
    // Verify that flow nodes also have rootProcessInstanceKey
    List<FlowNodeInstanceEntity> subFlowNodes = searchHistoricFlowNodes(sub.processInstanceKey());
    assertThat(subFlowNodes).isNotEmpty();
    subFlowNodes.forEach(flowNode ->
        assertThat(flowNode.rootProcessInstanceKey()).isEqualTo(parent.processInstanceKey())
    );

    // Verify that user tasks also have rootProcessInstanceKey
    List<UserTaskEntity> userTasks = searchHistoricUserTasks(sub.processInstanceKey());
    assertThat(userTasks).isNotEmpty();
    userTasks.forEach(userTask ->
        assertThat(userTask.rootProcessInstanceKey()).isEqualTo(parent.processInstanceKey())
    );

    // Verify that variables also have rootProcessInstanceKey
    List<VariableEntity> variables = variableReader
        .search(VariableQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(sub.processInstanceKey()))))
        .items();
    if (!variables.isEmpty()) {
      variables.forEach(variable ->
          assertThat(variable.rootProcessInstanceKey()).isEqualTo(parent.processInstanceKey())
      );
    }
  }

  @Test
  public void shouldPopulateParentFlowNodeInstanceKeyForCallActivity() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    runtimeService.startProcessInstanceByKey("callingProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> parentProcessInstances = searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> subProcessInstances = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(subProcessInstances).hasSize(1);
    assertThat(parentProcessInstances).hasSize(1);
    ProcessInstanceEntity parent = parentProcessInstances.getFirst();
    ProcessInstanceEntity sub = subProcessInstances.getFirst();
    List<FlowNodeInstanceEntity> parentCallActivities =
        searchHistoricFlowNodesForType(parent.processInstanceKey(), FlowNodeInstanceEntity.FlowNodeType.CALL_ACTIVITY);
    assertThat(parentCallActivities).hasSize(1);

    assertThat(sub.parentFlowNodeInstanceKey()).isEqualTo(parentCallActivities.getFirst().flowNodeInstanceKey());
  }

  @Test
  @WhiteBox
  public void shouldSkipEntitiesWhenRootProcessInstanceNotMigrated() {
    // given
    deployModel();
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    ClockUtil.setCurrentTime(new Date());
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ClockUtil.offset(1_000 * 4L);
    completeAllUserTasksWithDefaultUserTaskId();
    ProcessInstance subInstance = runtimeService.createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();

    // Mark parent as skipped
    dbClient.insert(parentInstance.getId(), (Long) null, new Date(), TYPE.HISTORY_PROCESS_INSTANCE, "test skip");

    // when - attempt to migrate (sub should be skipped because parent is skipped)
    historyMigrator.migrateByType(TYPE.HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(TYPE.HISTORY_PROCESS_INSTANCE);

    // then - sub process should be skipped
    List<ProcessInstanceEntity> subProcessInstances = searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(subProcessInstances).isEmpty();

    // Verify skip was logged
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(), subInstance.getId(), SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE));
  }

  protected void verifyProcessInstanceFields(ProcessInstanceEntity processInstance,
                                             HistoricProcessInstance historicProcessInstance,
                                             String processDefinitionId,
                                             ProcessInstanceEntity.ProcessInstanceState processInstanceState,
                                             String versionTag,
                                             String tenantId,
                                             boolean hasParent,
                                             boolean hasIncidents, boolean isCompleted) {
    // Verify migration completed successfully via logs
    logs.assertContains(formatMessage(MIGRATION_COMPLETED, TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(), historicProcessInstance.getId()));

    assertThat(processInstance.processDefinitionId()).isEqualTo(prefixDefinitionId(processDefinitionId));
    assertThat(processInstance.state()).isEqualTo(processInstanceState);
    assertThat(processInstance.processInstanceKey()).isNotNull();
    assertThat(processInstance.processDefinitionKey()).isNotNull();
    assertThat(processInstance.tenantId()).isEqualTo(tenantId);
    assertThat(processInstance.startDate())
        .isEqualTo(convertDate(historicProcessInstance.getStartTime()));
    if (isCompleted) {
      assertThat(processInstance.endDate()).isEqualTo(convertDate(historicProcessInstance.getEndTime()));
    } else {
      assertThat(processInstance.endDate()).isNotNull();
    }
    assertThat(processInstance.processDefinitionVersion()).isEqualTo(1);
    assertThat(processInstance.processDefinitionVersionTag()).isEqualTo(versionTag);

    if (hasParent) {
      assertThat(processInstance.parentProcessInstanceKey()).isNotNull();
      assertThat(processInstance.parentFlowNodeInstanceKey()).isNotNull();
    } else {
      assertThat(processInstance.parentProcessInstanceKey()).isNull();
      assertThat(processInstance.parentFlowNodeInstanceKey()).isNull();
    }

    assertThat(processInstance.hasIncident()).isEqualTo(hasIncidents);

    assertThat(processInstance.treePath()).isNotNull();
    if (hasParent) {
      assertThat(processInstance.treePath()).matches("PI_\\d+/PI_" + processInstance.processInstanceKey());
    } else {
      assertThat(processInstance.treePath()).isEqualTo("PI_" + processInstance.processInstanceKey());
    }
  }

  @Test
  public void shouldMigrateProcessInstanceTagsWithBusinessKey() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", "customBusinessKey");

    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess");
    assertThat(migratedProcessInstances).isNotEmpty();

    assertThat(migratedProcessInstances.getFirst().tags()).containsOnly(C7_LEGACY_PREFIX);
    assertThat(migratedProcessInstances.getFirst().businessId()).isEqualTo("customBusinessKey");
  }

  @Test
  public void shouldMigrateProcessInstanceTags() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // when
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess");
    assertThat(migratedProcessInstances).isNotEmpty();

    assertThat(migratedProcessInstances.getFirst().tags()).containsOnly(C7_LEGACY_PREFIX);
    assertThat(migratedProcessInstances.getFirst().businessId()).isNull();
  }

  protected void deployModel() {
    BpmnModelInstance c7BusinessRuleProcess = Bpmn.createExecutableProcess("callingProcessId")
        .startEvent()
        .userTask(USER_TASK_ID)
        .callActivity()
        .calledElement("calledProcessInstanceId")
        .endEvent()
        .done();
    deployer.deployC7ModelInstance("callingProcessId", c7BusinessRuleProcess);
  }
}
