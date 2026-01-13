/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.PresetProcessInstanceInterceptor",
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer",
    "camunda.migrator.interceptors[1].enabled=false",
    "camunda.migrator.interceptors[2].className=io.camunda.migration.data.qa.history.entity.interceptor.bean"
        + ".PresetDecisionInstanceInterceptor",
    "camunda.migrator.interceptors[3].className=io.camunda.migration.data.qa.history.entity.interceptor.bean"
        + ".PresetFlowNodeInterceptor",
    "camunda.migrator.interceptors[4].className=io.camunda.migration.data.qa.history.entity.interceptor.bean"
        + ".PresetIncidentInterceptor",
    "camunda.migrator.interceptors[5].className=io.camunda.migration.data.qa.history.entity.interceptor.bean"
        + ".PresetUserTaskInterceptor",
    "camunda.migrator.interceptors[6].className=io.camunda.migration.data.qa.history.entity.interceptor.bean"
        + ".PresetVariableInterceptor", })
public class HistoryPresetParentPropertiesTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldExecuteProcessInstancePresetInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigration.getMigrator().migrateProcessInstances();

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances = historyMigration.searchHistoricProcessInstances("simpleProcess", true);
    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity processInstanceEntity = migratedProcessInstances.getFirst();
    assertThat(processInstanceEntity.processInstanceKey()).isEqualTo(88888L);
    assertThat(processInstanceEntity.processDefinitionKey()).isEqualTo(12345L);
  }

  @Test
  public void shouldExecuteProcessInstancePresetInterceptorWithoutMigratedParent() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    // Complete the process
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }
    // Delete the historic calling process instance to simulate missing parent
    historyMigration.getHistoryService().deleteHistoricProcessInstance(processInstance.getId());

    // Run history migration
    historyMigration.getMigrator().migrateProcessInstances();

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances = historyMigration.searchHistoricProcessInstances("calledProcessInstanceId",
        true);
    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity processInstanceEntity = migratedProcessInstances.getFirst();
    assertThat(processInstanceEntity.processInstanceKey()).isEqualTo(88888L);
    assertThat(processInstanceEntity.processDefinitionKey()).isEqualTo(12345L);
    assertThat(processInstanceEntity.parentProcessInstanceKey()).isEqualTo(67890L);
  }

  @Test
  public void shouldMigrateDecisionInstanceWithPresetProperties() {
    Date now = ClockUtil.now();
    ClockUtil.setCurrentTime(now);
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    // when
    historyMigration.getMigrator().migrateDecisionInstances();

    // then: decision instance is migrated
    List<DecisionInstanceEntity> migratedInstances = historyMigration.searchHistoricDecisionInstances("simpleDecisionId");

    assertThat(migratedInstances).singleElement()
        .satisfies(instance -> assertDecisionInstance(instance, "simpleDecisionId", now, 7L, 4L, 1L, 2L,
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE, "\"B\"", "inputA", "\"A\"", "outputB",
            "\"B\""));
  }

  @Test
  public void shouldMigrateFlowNodeWithPresetProperties() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrateFlowNodes();

    // then
    List<FlowNodeInstanceDbModel> flowNodes = searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(1L);

    assertThat(flowNodes).isNotEmpty();
    for (FlowNodeInstanceDbModel flowNode : flowNodes) {
      assertThat(flowNode.flowNodeScopeKey()).isEqualTo(1L);
    }
  }

  @Test
  public void shouldMigrateTaskWithPresetProperties() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.complete(task.getId());

    HistoricTaskInstance c7Task = historyMigration.getHistoryService().createHistoricTaskInstanceQuery()
        .processInstanceId(processInstance.getId())
        .singleResult();

    // when
    historyMigration.getMigrator().migrateUserTasks();

    // then
    List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(2L);
    assertThat(userTasks).hasSize(1);

    UserTaskEntity userTask = userTasks.getFirst();
    assertThat(userTask.userTaskKey()).isNotNull();
    assertThat(userTask.processDefinitionId()).isEqualTo(prefixDefinitionId(c7Task.getProcessDefinitionKey()));
    assertThat(userTask.processDefinitionKey()).isEqualTo(1L);
    assertThat(userTask.elementInstanceKey()).isEqualTo(3L);
  }

  @Test
  public void shouldMigrateVariableWithPresetProperties() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.complete(task.getId());

    // when
    historyMigration.getMigrator().migrateVariables();

    // then
    List<VariableEntity> c8Variables = historyMigration.searchHistoricVariables("stringVar");
    assertThat(c8Variables).hasSize(1);
    VariableEntity variable = c8Variables.getFirst();
    assertThat(variable.processInstanceKey()).isEqualTo(1L);
    assertThat(variable.scopeKey()).isEqualTo(2L);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/364")
  public void shouldMigrateIncidentWithPresetProperties() {
    // given: Process with failing service task in C7
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");

    // execute the job to trigger the incident
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);

    // Try executing the job multiple times to ensure incident is created
    for (int i = 0; i < 3; i++) {
      try {
        managementService.executeJob(jobs.getFirst().getId());
      } catch (Exception e) {
        // expected - job will fail due to empty delegate expression
      }
    }

    // when
    historyMigration.getMigrator().migrateIncidents();

    // then
    List<IncidentEntity> incidents = historyMigration.searchHistoricIncidents("failingServiceTaskProcessId");
    assertThat(incidents).isNotEmpty();

    IncidentEntity incident = incidents.getFirst();
    assertThat(incident.processDefinitionKey()).isEqualTo(1L);
    assertThat(incident.processInstanceKey()).isEqualTo(2L);
    assertThat(incident.jobKey()).isEqualTo(3L);
    assertThat(incident.flowNodeInstanceKey()).isEqualTo(4L);

  }
}
