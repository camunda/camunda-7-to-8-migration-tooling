/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.ZoneOffset;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

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
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
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
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(c7Process.getId())
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances.size()).isEqualTo(1);
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
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(parentInstance.getId())
        .singleResult();
    HistoricProcessInstance historicSubProcessInstance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(subInstance.getId())
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

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
    HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
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
    HistoricActivityInstance callActivity = historyService.createHistoricActivityInstanceQuery()
        .activityId("callActivityId")
        .processInstanceId(subInstance.getId())
        .singleResult();
    historyMigration.getDbClient().insert(callActivity.getId(), null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricProcessInstances("calledProcessInstanceId")).isEmpty();
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
    logs.assertContains(formatMessage(HistoryMigratorLogs.MIGRATING_INSTANCE_COMPLETE, "process", historicProcessInstance.getId()));

    assertThat(processInstance.processDefinitionId()).isEqualTo(processDefinitionId);
    assertThat(processInstance.state()).isEqualTo(processInstanceState);
    assertThat(processInstance.processInstanceKey()).isNotNull();
    assertThat(processInstance.processDefinitionKey()).isNotNull();
    assertThat(processInstance.tenantId()).isEqualTo(tenantId);
    assertThat(processInstance.startDate())
        .isEqualTo(historicProcessInstance.getStartTime().toInstant().atOffset(ZoneOffset.UTC));
    assertThat(processInstance.endDate())
        .isEqualTo(historicProcessInstance.getEndTime().toInstant().atOffset(ZoneOffset.UTC));
    assertThat(processInstance.processDefinitionVersion()).isEqualTo(1);
    assertThat(processInstance.processDefinitionVersionTag()).isEqualTo(versionTag);

    if (hasParent) {
      assertThat(processInstance.parentProcessInstanceKey()).isNotNull();
      assertThat(processInstance.parentFlowNodeInstanceKey()).isNull();
    } else {
      assertThat(processInstance.parentProcessInstanceKey()).isNull();
      assertThat(processInstance.parentFlowNodeInstanceKey()).isNull();
    }

    assertThat(processInstance.hasIncident()).isEqualTo(hasIncidents);
    // https://github.com/camunda/camunda-bpm-platform/issues/5359
    assertThat(processInstance.treePath()).isNull();
    assertThat(processInstance.parentFlowNodeInstanceKey()).isNull();
  }

}
