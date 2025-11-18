/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Map;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({ OutputCaptureExtension.class })
public class HistoryMigrationSkippingTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected HistoryService historyService;

  @Test
  public void shouldSkipElementsWhenProcessDefinitionIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }

    // and the process definition is manually set as skipped
    String c7Id = repositoryService.createProcessDefinitionQuery().singleResult().getId();
    simulateSkippedEntity(c7Id, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

    // when history is migrated
    historyMigrator.migrate();

    // then nothing was migrated
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // and all elements for the definition were skipped
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(5);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE)).isEqualTo(15);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(5);
  }

  @Test
  public void shouldNotMigrateAlreadySkippedProcessInstance() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // and the process instance is manually set as skipped
    simulateSkippedEntity(processInstance.getId(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

    // when history is migrated
    historyMigrator.migrate();

    // then no process instances were migrated
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // verify the process instance was skipped exactly once
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(1);

    // and verify logs don't contain any additional skip operations for this process instance
    logs.assertDoesNotContain(
        "Migration of historic process instance with C7 ID [" + processInstance.getId() + "] skipped");
  }

  @Test
  public void shouldSkipUserTasksWhenProcessInstanceIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // and the process instance is manually set as skipped
    simulateSkippedEntity(processInstance.getId(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance and user task were skipped
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(1);
    logs.assertContains("Migration of historic user task with C7 ID [" + task.getId() + "] skipped");
  }

  @Test
  public void shouldNotMigrateAlreadySkippedUserTask() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // and the user task is manually set as skipped
    String taskId = historyService.createHistoricTaskInstanceQuery().singleResult().getId();
    simulateSkippedEntity(taskId, IdKeyMapper.TYPE.HISTORY_USER_TASK);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance was migrated but user task was not
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    var processInstance = historicProcesses.getFirst();
    assertThat(searchHistoricUserTasks(processInstance.processInstanceKey())).isEmpty();

    // verify the task was skipped exactly once
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(1);

    // and verify logs don't contain any additional skip operations for this task
    logs.assertDoesNotContain("Migration of C7 user task with id [" + task.getId() + "] skipped");

  }

  @Test
  public void shouldSkipIncidentsWhenProcessInstanceIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

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
    assertThat(historyService.createHistoricIncidentQuery().count()).as("Expected one incident to be created")
        .isEqualTo(1);
    String incidentId = historyService.createHistoricIncidentQuery().singleResult().getId();

    // and the process instance is manually set as skipped
    simulateSkippedEntity(processInstance.getId(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance and incidents were skipped
    assertThat(searchHistoricProcessInstances("failingServiceTaskProcessId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);
    logs.assertContains("Migration of historic incident with C7 ID [" + incidentId + "] skipped");
  }

  @Test
  public void shouldNotMigrateAlreadySkippedIncident() {
    // given state in c7 with a failing service task
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

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

    // and manually mark the incident as skipped
    String incidentId = historyService.createHistoricIncidentQuery().singleResult().getId();
    simulateSkippedEntity(incidentId, IdKeyMapper.TYPE.HISTORY_INCIDENT);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance was migrated but incident was not
    var historicProcesses = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    ProcessInstanceEntity c8ProcessInstance = historicProcesses.getFirst();
    assertThat(searchHistoricIncidents(c8ProcessInstance.processDefinitionId())).isEmpty();

    // verify the incident was skipped exactly once
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);

    // and verify logs don't contain any additional skip operations for this incident
    logs.assertDoesNotContain("Skipping historic incident " + incidentId);
  }

  @Disabled("TODO: https://github.com/camunda/camunda-bpm-platform/issues/5331")
  @Test
  public void shouldNotMigrateIncidentsWhenJobIsSkipped() {
    // given state in c7 with a failing service task
    deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

    // execute the job to trigger the incident
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    var job = jobs.getFirst();

    try {
      managementService.executeJob(job.getId());
    } catch (Exception e) {
      // expected - job will fail
    }

    // and manually mark the job as skipped
    simulateSkippedEntity(job.getId(), IdKeyMapper.TYPE.HISTORY_FLOW_NODE);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance was migrated but incident was skipped due to skipped job
    var historicProcesses = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    ProcessInstanceEntity c8processInstance = historicProcesses.getFirst();
    assertThat(searchHistoricIncidents(c8processInstance.processDefinitionId())).isEmpty();

    // verify the incident was skipped
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);
  }

  @Test
  public void shouldSkipVariablesWhenProcessInstanceIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId", Map.of("testVar", "testValue"));
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // and the process instance is manually set as skipped
    simulateSkippedEntity(processInstance.getId(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance and variables were skipped
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);
  }

  @Test
  public void shouldNotMigrateAlreadySkippedVariable() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId",
        Map.of("testVar", "testValue", "anotherVar", "anotherValue"));
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // Find a variable to mark as skipped
    var historicVariables = historyService.createHistoricVariableInstanceQuery().variableName("testVar").list();
    assertThat(historicVariables).hasSize(1);
    var variableToSkip = historicVariables.getFirst();

    // and the variable is manually set as skipped
    simulateSkippedEntity(variableToSkip.getId(), IdKeyMapper.TYPE.HISTORY_VARIABLE);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance was migrated but the variable was not
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    var variables = searchHistoricVariables("anotherVar");
    assertThat(variables.size()).isEqualTo(1);

    // verify the variable was skipped exactly once
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);
  }

  @Test
  public void shouldSkipTaskVariablesWhenTaskIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Create a task-level variable
    var task = taskService.createTaskQuery().singleResult();
    taskService.setVariableLocal(task.getId(), "taskLocalVar", "taskValue");
    taskService.complete(task.getId());

    // and the task is manually set as skipped
    String taskId = historyService.createHistoricTaskInstanceQuery().singleResult().getId();
    simulateSkippedEntity(taskId, IdKeyMapper.TYPE.HISTORY_USER_TASK);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance was migrated but task and its variables were not
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    var migratedProcessInstance = historicProcesses.getFirst();

    // Verify task was skipped
    assertThat(searchHistoricUserTasks(migratedProcessInstance.processInstanceKey())).isEmpty();

    // Verify task variable was also skipped
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);

    // Find the variable that should have been skipped
    var taskVariable = historyService.createHistoricVariableInstanceQuery().taskIdIn(taskId).singleResult();

    logs.assertContains("Migration of historic variable with C7 ID [" + taskVariable.getId() + "] skipped");
  }

  @Test
  public void shouldSkipServiceTaskVariablesWhenServiceTaskIsSkipped() {
    // given state in c7 with a service task using JUEL expression
    deployer.deployCamunda7Process("serviceTaskWithInputMappingProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("serviceTaskWithInputMappingProcessId");

    // Find the service task in history
    var historicActivities = historyService.createHistoricActivityInstanceQuery()
        .processInstanceId(processInstance.getId())
        .activityId("serviceTaskId")
        .list();

    assertThat(historicActivities).isNotEmpty().as("Expected service task to be in history");

    String serviceTaskActivityInstanceId = historicActivities.getFirst().getId();

    // Find the service task local variable in history
    var serviceTaskVariable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("serviceTaskVar")
        .activityInstanceIdIn(serviceTaskActivityInstanceId)
        .singleResult();

    assertThat(serviceTaskVariable).isNotNull().as("Expected to find local variable on service task");

    // Mark the service task as skipped
    simulateSkippedEntity(serviceTaskActivityInstanceId, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);

    // when history is migrated
    historyMigrator.migrate();

    // then process instance was migrated but service task was skipped
    var historicProcesses = searchHistoricProcessInstances("serviceTaskWithInputMappingProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);

    // Verify service task variable was skipped
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);

    // Verify appropriate logging
    logs.assertContains("Migration of historic variable with C7 ID [" + serviceTaskVariable.getId() + "] skipped");
  }

  @Test
  public void shouldSkipDecisionDefinitionWhenDecisionRequirementsIsSkipped() {
    // given
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // and the decision requirements is manually set as skipped
    String decisionRequirementsId = repositoryService.createDecisionRequirementsDefinitionQuery()
        .decisionRequirementsDefinitionKey("simpleDmnWithReqsId")
        .singleResult()
        .getId();
    simulateSkippedEntity(decisionRequirementsId, IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id")).isEmpty();
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION)).isEqualTo(2);
  }

  @Test
  public void shouldSkipDecisionInstanceWhenDecisionDefinitionIsSkipped() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String decisionDefinitionId = repositoryService.createDecisionDefinitionQuery()
        .decisionDefinitionKey("simpleDecisionId")
        .singleResult()
        .getId();
    simulateSkippedEntity(decisionDefinitionId, IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION);

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE)).isEqualTo(1);
  }

  @Test
  public void shouldSkipDecisionInstanceWhenProcessDefinitionIsSkipped() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String processDefinitionId = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("businessRuleProcessId")
        .singleResult()
        .getId();
    simulateSkippedEntity(processDefinitionId, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE)).isEqualTo(1);
  }

  @Test
  public void shouldSkipDecisionInstanceWhenProcessInstanceIsSkipped() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    simulateSkippedEntity(processInstance.getId(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE)).isEqualTo(1);
  }

  @Test
  public void shouldSkipDecisionInstanceWhenFlowNodeIsSkipped() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String c7FlowNodeId =
        historyService.createHistoricActivityInstanceQuery().activityId("businessRuleTaskId").singleResult().getId();
    simulateSkippedEntity(c7FlowNodeId, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE)).isEqualTo(1);
  }

  @Test
  public void shouldNotSkipTaskVariablesWhenEntityWithSameIdButDifferentTypeIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    var taskId = taskService.createTaskQuery().singleResult().getId();

    // Simulate ID collision by manually inserting a record with the same ID as the task
    // but with a different type (HISTORY_INCIDENT)
    simulateSkippedEntity(taskId, IdKeyMapper.TYPE.HISTORY_INCIDENT);
    // Verify the collision record exists before completing the task
    assertThat(dbClient.checkExistsByC7IdAndType(taskId, IdKeyMapper.TYPE.HISTORY_INCIDENT)).as(
        "Record with task ID should exist").isTrue();

    // when history is migrated
    historyMigrator.migrate();

    // then
    // 1. Process instance should be migrated
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses).hasSize(1);
    var processInstanceKey = historicProcesses.getFirst().processInstanceKey();

    // 2. User task should be migrated (not skipped due to ID collision with HISTORY_INCIDENT)
    var userTasks = searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).as("User task should be migrated despite ID collision with HISTORY_INCIDENT").hasSize(1);
    assertThat(userTasks.getFirst().elementId()).as("User task should have correct id").isEqualTo("userTaskId");
  }
}
