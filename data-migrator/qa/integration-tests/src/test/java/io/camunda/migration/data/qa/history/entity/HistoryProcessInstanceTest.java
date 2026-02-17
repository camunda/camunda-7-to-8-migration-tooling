/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
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
<<<<<<< HEAD
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
=======
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
>>>>>>> main
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.VariableQuery;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;

@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryProcessInstanceTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Test
  public void shouldMigrateProcessInstance() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();
    HistoricProcessInstance historicProcessInstance = historyMigration.getHistoryService().createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    verifyProcessInstanceFields(processInstances.getFirst(), historicProcessInstance, "userTaskProcessId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, "custom-version-tag", C8_DEFAULT_TENANT, false, false);
  }

  @Test
  public void shouldMigrateProcessInstancesWithTenant() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn", "my-tenant1");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();
    HistoricProcessInstance historicProcessInstance = historyMigration.getHistoryService().createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    verifyProcessInstanceFields(processInstances.getFirst(), historicProcessInstance, "userTaskProcessId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, "custom-version-tag", "my-tenant1", false, false);
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
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();
    HistoricProcessInstance historicProcessInstance = historyMigration.getHistoryService().createHistoricProcessInstanceQuery()
        .processInstanceId(parentInstance.getId())
        .singleResult();
    HistoricProcessInstance historicSubProcessInstance = historyMigration.getHistoryService().createHistoricProcessInstanceQuery()
        .processInstanceId(subInstance.getId())
        .singleResult();

    // when
<<<<<<< HEAD
    historyMigration.getMigrator().migrate();
=======
    historyMigrator.migrate();
    // need to run with retry to migrate child instances with flow node dependencies
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();
>>>>>>> main

    // then
    List<ProcessInstanceEntity> parentProcessInstance = historyMigration.searchHistoricProcessInstances("callingProcessId");
    List<ProcessInstanceEntity> subProcessInstance = historyMigration.searchHistoricProcessInstances("calledProcessInstanceId");
    assertThat(parentProcessInstance).hasSize(1);
    assertThat(subProcessInstance).hasSize(1);

    var parent = parentProcessInstance.getFirst();
    verifyProcessInstanceFields(parent, historicProcessInstance, "callingProcessId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, null, C8_DEFAULT_TENANT, false, false);

    var sub = subProcessInstance.getFirst();
    verifyProcessInstanceFields(sub, historicSubProcessInstance, "calledProcessInstanceId",
        ProcessInstanceEntity.ProcessInstanceState.COMPLETED, null, C8_DEFAULT_TENANT, true, false);

  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5400")
  public void shouldMigrateProcessInstanceWithIncident() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("incidentProcessId");
    triggerIncident(c7Process.getId());
    HistoricProcessInstance historicProcessInstance = historyMigration.getHistoryService().createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("incidentProcessId");
    assertThat(processInstances).hasSize(1);
    var processInstance = processInstances.getFirst();

    verifyProcessInstanceFields(processInstance, historicProcessInstance, "incidentProcessId",
        ProcessInstanceEntity.ProcessInstanceState.ACTIVE, null, C8_DEFAULT_TENANT, false, true);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5359")
  @WhiteBox
  public void shouldCheckCalledProcessParentElementKey() {
    // given
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance subInstance = runtimeService.createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();
    HistoricActivityInstance callActivity = historyMigration.getHistoryService().createHistoricActivityInstanceQuery()
        .activityId("callActivityId")
        .processInstanceId(subInstance.getId())
        .singleResult();
<<<<<<< HEAD
    historyMigration.getDbClient().insert(callActivity.getId(), null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
=======
    dbClient.insert(callActivity.getId(), null, TYPE.HISTORY_PROCESS_DEFINITION);
>>>>>>> main

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricProcessInstances("calledProcessInstanceId")).isEmpty();
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
    // need to run with retry to migrate child instances with flow node dependencies
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
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
    List<VariableEntity> variables = rdbmsService.getVariableReader()
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
    // need to run with retry to migrate child instances with flow node dependencies
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
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

    // Manually configure migrator to MIGRATE mode
    historyMigrator.setMode(MigratorMode.MIGRATE);

    // Mark parent as skipped
    dbClient.insert(parentInstance.getId(), (Long) null, new Date(), TYPE.HISTORY_PROCESS_INSTANCE, "test skip");

    // when - attempt to migrate (sub should be skipped because parent is skipped)
    historyMigrator.migrate();

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
                                             boolean hasIncidents) {
    // Verify migration completed successfully via logs
    logs.assertContains(formatMessage(MIGRATION_COMPLETED, TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(), historicProcessInstance.getId()));

    assertThat(processInstance.processDefinitionId()).isEqualTo(prefixDefinitionId(processDefinitionId));
    assertThat(processInstance.state()).isEqualTo(processInstanceState);
    assertThat(processInstance.processInstanceKey()).isNotNull();
    assertThat(processInstance.processDefinitionKey()).isNotNull();
    assertThat(processInstance.tenantId()).isEqualTo(tenantId);
    assertThat(processInstance.startDate())
        .isEqualTo(convertDate(historicProcessInstance.getStartTime()));
    assertThat(processInstance.endDate())
        .isEqualTo(convertDate(historicProcessInstance.getEndTime()));
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
    assertThat(processInstance.treePath()).isNull();
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
