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
  protected HistoryService historyService;

  @Test
  public void shouldSkipElementsWhenProcessDefinitionIsSkipped() {
    // given: Process with instances and tasks in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }

    // when: Migrate process instances and their children WITHOUT migrating process definitions first
    // This creates a natural skip scenario - instances need their definition to exist
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks();

    // then: All child elements are naturally skipped due to missing process definition
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    logs.assertContains("Migration of historic process instance with C7 ID");
    logs.assertContains("skipped. Process definition not yet available");
    logs.assertContains("Migration of historic flow nodes with C7 ID");
    logs.assertContains("skipped. Process instance yet not available");
    logs.assertContains("Migration of historic user task with C7 ID");
    logs.assertContains("skipped. Process instance yet not available");
  }

  @Test
  public void shouldNotMigrateAlreadySkippedProcessInstance() {
    // given: Process deployed and instance created in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Phase 1: Migrate instances without definition (they skip naturally)
    historyMigrator.migrateProcessInstances();
    
    // Verify the instance was skipped
    logs.assertContains("Migration of historic process instance with C7 ID [" + processInstance.getId() + "] skipped");
    
    // Phase 2: Now migrate with definition - already skipped instance should not be reprocessed
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();

    // then: The process instance remains skipped and wasn't migrated
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    // Verify it was only logged once (no duplicate processing)
    logs.assertContains("Migration of historic process instance with C7 ID [" + processInstance.getId() + "] skipped");
  }

  @Test
  public void shouldSkipUserTasksWhenProcessInstanceIsSkipped() {
    // given: Process with user task in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when: Migrate user tasks WITHOUT migrating process instances first
    // This creates a natural skip - tasks need their process instance to exist
    historyMigrator.migrateProcessDefinitions(); // Need definition for reference
    historyMigrator.migrateUserTasks(); // Try to migrate tasks without instances

    // then: User task is naturally skipped due to missing process instance
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    logs.assertContains("Migration of historic user task with C7 ID [" + task.getId() + "] skipped");
  }

  @Test
  public void shouldNotMigrateAlreadySkippedUserTask() {
    // given: Process with user task in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when: First migration - migrate user tasks WITHOUT process instances (naturally skips)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks(); // Skips due to missing process instance
    
    // Verify task was skipped
    logs.assertContains("Migration of historic user task with C7 ID [" + task.getId() + "] skipped");
    
    // Second migration: Now migrate everything including process instances
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateUserTasks(); // Should not reprocess already-skipped task

    // then: Process instance was migrated but user task remains skipped
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    var processInstance = historicProcesses.getFirst();
    assertThat(searchHistoricUserTasks(processInstance.processInstanceKey())).isEmpty();

    // Verify the task was skipped (remains in skipped state)
    logs.assertContains("Migration of historic user task with C7 ID [" + task.getId() + "] skipped");

  }

  @Test
  public void shouldSkipIncidentsWhenProcessInstanceIsSkipped() {
    // given: Process with failing service task in C7
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

    // when: Migrate incidents WITHOUT process instances (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateIncidents(); // Skips due to missing process instance

    // then: Incidents are naturally skipped due to missing process instance
    assertThat(searchHistoricProcessInstances("failingServiceTaskProcessId")).isEmpty();
    logs.assertContains("Migration of historic incident with C7 ID [" + incidentId + "] skipped");
  }

  @Test
  public void shouldNotMigrateAlreadySkippedIncident() {
    // given: Process with failing service task in C7
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

    String incidentId = historyService.createHistoricIncidentQuery().singleResult().getId();
    
    // First migration: Migrate incidents WITHOUT process instances (naturally skips)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateIncidents(); // Skips due to missing process instance
    
    // Verify incident was skipped
    logs.assertContains("Migration of historic incident with C7 ID [" + incidentId + "] skipped");

    // Second migration: Migrate process instances, but already-skipped incident should not be reprocessed
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateIncidents();

    // then: Process instance was migrated but incident remains skipped
    var historicProcesses = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    ProcessInstanceEntity c8ProcessInstance = historicProcesses.getFirst();
    assertThat(searchHistoricIncidents(c8ProcessInstance.processDefinitionId())).isEmpty();

    // Verify the incident remains skipped
    logs.assertContains("Migration of historic incident with C7 ID [" + incidentId + "] skipped");
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
    dbClient.insert(job.getId(), null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);

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
    // given: Process with variable in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId", Map.of("testVar", "testValue"));
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when: Migrate variables WITHOUT process instances (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateVariables(); // Skips due to missing process instance

    // then: Variables are naturally skipped due to missing process instance
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    logs.assertContains("Migration of historic variable with C7 ID");
    logs.assertContains("skipped. Process instance not yet available");
  }

  @Test
  public void shouldNotMigrateAlreadySkippedVariable() {
    // given: Process with multiple variables in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId",
        Map.of("testVar", "testValue", "anotherVar", "anotherValue"));
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // First migration: Migrate variables WITHOUT process instances (naturally skips)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateVariables(); // All variables skip due to missing process instance
    
    // Verify variables were skipped
    logs.assertContains("Migration of historic variable with C7 ID");
    logs.assertContains("skipped. Process instance not yet available");

    // Second migration: Migrate process instances and variables again
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateVariables(); // Already skipped variables should not be reprocessed

    // then: Process instance was migrated but variables remain skipped
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    
    // No variables were migrated - both remain skipped
    var variablesTestVar = searchHistoricVariables("testVar");
    var variablesAnotherVar = searchHistoricVariables("anotherVar");
    assertThat(variablesTestVar.size()).isEqualTo(0);
    assertThat(variablesAnotherVar.size()).isEqualTo(0);

    // Verify variables remain in skipped state
    logs.assertContains("Migration of historic variable with C7 ID");
    logs.assertContains("skipped. Process instance not yet available");
  }

  @Test
  public void shouldSkipTaskVariablesWhenTaskIsSkipped() {
    // given: Process with task-level variable in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Create a task-level variable
    var task = taskService.createTaskQuery().singleResult();
    taskService.setVariableLocal(task.getId(), "taskLocalVar", "taskValue");
    taskService.complete(task.getId());

    String taskId = historyService.createHistoricTaskInstanceQuery().singleResult().getId();

    // when: Migrate user tasks and variables WITHOUT process instances (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks(); // Skips due to missing process instance
    historyMigrator.migrateVariables(); // Also skips

    // then: Process instance was not migrated, task and its variables were skipped
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(0);

    // Verify task and variable were skipped via logs
    logs.assertContains("Migration of historic user task with C7 ID");
    logs.assertContains("skipped. Process instance yet not available");
    logs.assertContains("Migration of historic variable with C7 ID");

    // Find the variable that should have been skipped
    var taskVariable = historyService.createHistoricVariableInstanceQuery().taskIdIn(taskId).singleResult();

    logs.assertContains("Migration of historic variable with C7 ID [" + taskVariable.getId() + "] skipped");
  }

  @Test
  public void shouldSkipServiceTaskVariablesWhenServiceTaskIsSkipped() {
    // given: Process with service task using JUEL expression in C7
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

    // when: Migrate flow nodes and variables WITHOUT process instances (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateFlowNodes(); // Skips due to missing process instance
    historyMigrator.migrateVariables(); // Also skips

    // then: Process instance was not migrated, service task and variable were skipped
    var historicProcesses = searchHistoricProcessInstances("serviceTaskWithInputMappingProcessId");
    assertThat(historicProcesses.size()).isEqualTo(0);

    // Verify flow node and variable were skipped via logs
    logs.assertContains("Migration of historic flow nodes with C7 ID");
    logs.assertContains("skipped. Process instance yet not available");
    logs.assertContains("Migration of historic variable with C7 ID [" + serviceTaskVariable.getId() + "] skipped");
  }

  @Test
  public void shouldSkipDecisionDefinitionWhenDecisionRequirementsIsSkipped() {
    // given: DMN with requirements deployed in C7
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // when: Migrate decision definitions WITHOUT migrating requirements first
    // This creates a natural skip scenario - definitions can't migrate without their parent requirements
    historyMigrator.migrateDecisionDefinitions();

    // then: Decision definitions are naturally skipped due to missing requirements
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id")).isEmpty();
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id")).isEmpty();
    logs.assertContains("Migration of historic decision definition with C7 ID");
    logs.assertContains("skipped. Decision requirements definition not yet available");
    
    // Verify the skip reason confirms it was due to missing requirements
    // (This is implicit in the natural skip - no manual marking needed)
  }

  @Test
  public void shouldSkipDecisionInstanceWhenDecisionDefinitionIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String decisionDefinitionId = repositoryService.createDecisionDefinitionQuery()
        .decisionDefinitionKey("simpleDecisionId")
        .singleResult()
        .getId();
    
    // when: Migrate decision instances WITHOUT decision definitions (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing decision definition

    // then: Decision instances are naturally skipped due to missing decision definition
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains("Migration of historic decision instance with C7 ID");
    logs.assertContains("skipped. decision definition not yet available");
  }

  @Test
  public void shouldSkipDecisionInstanceWhenProcessDefinitionIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String processDefinitionId = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("businessRuleProcessId")
        .singleResult()
        .getId();
    
    // when: Migrate decision instances WITHOUT process definitions (creates natural skip)
    historyMigrator.migrateDecisionDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing process definition

    // then: Decision instances are naturally skipped due to missing process definition
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains("Migration of historic decision instance with C7 ID");
    logs.assertContains("skipped");
  }

  @Test
  public void shouldSkipDecisionInstanceWhenProcessInstanceIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    
    // when: Migrate decision instances WITHOUT process instances (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateDecisionDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing process instance

    // then: Decision instances are naturally skipped due to missing process instance
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains("Migration of historic decision instance with C7 ID");
    logs.assertContains("skipped. Process instance not yet available");
  }

  @Test
  public void shouldSkipDecisionInstanceWhenFlowNodeIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String c7FlowNodeId =
        historyService.createHistoricActivityInstanceQuery().activityId("businessRuleTaskId").singleResult().getId();
    
    // when: Migrate decision instances WITHOUT flow nodes (creates natural skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateDecisionDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing flow node

    // then: Decision instances are naturally skipped due to missing flow node
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains("Migration of historic decision instance with C7 ID");
    logs.assertContains("skipped. Flow node instance not yet available");
  }

  @Test
  public void shouldNotSkipTaskVariablesWhenEntityWithSameIdButDifferentTypeIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    var taskId = taskService.createTaskQuery().singleResult().getId();

    // Simulate ID collision by manually inserting a record with the same ID as the task
    // but with a different type (HISTORY_INCIDENT)
    dbClient.insert(taskId, null, IdKeyMapper.TYPE.HISTORY_INCIDENT);
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
