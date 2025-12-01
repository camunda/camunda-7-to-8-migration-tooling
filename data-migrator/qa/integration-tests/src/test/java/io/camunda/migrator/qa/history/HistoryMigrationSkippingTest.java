/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIPPING_INCIDENT;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIPPING_INSTANCE_MISSING_DEFINITION;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIPPING_USER_TASK_MISSING_PROCESS;
import static io.camunda.migrator.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.impl.logging.HistoryMigratorLogs;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.qa.util.WhiteBox;
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
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }

    // Capture IDs before migration
    var processInstanceIds = historyService.createHistoricProcessInstanceQuery().list().stream()
        .map(pi -> pi.getId())
        .toList();
    var userTaskIds = historyService.createHistoricTaskInstanceQuery().list().stream()
        .map(task -> task.getId())
        .toList();

    assertThat(processInstanceIds).hasSize(5);
    assertThat(userTaskIds).hasSize(5);

    // when
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks();

    // then
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // Verify all 5 specific process instances were skipped
    for (String instanceId : processInstanceIds) {
      logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", instanceId, "process"));
    }

    // Verify all 5 specific user tasks were skipped
    for (String taskId : userTaskIds) {
      logs.assertContains(formatMessage(SKIPPING_USER_TASK_MISSING_PROCESS, taskId));
    }
  }

  @Test
  public void shouldNotMigrateAlreadySkippedProcessInstance() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Migrate instances without definition (real-world skip scenario)
    historyMigrator.migrateProcessInstances();
    
    // Verify the instance was skipped
    logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", processInstance.getId(), "process"));

    // Now migrate with definition - already skipped instance should not be reprocessed
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();

    // then
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // Verify logs don't contain any additional skip operations for this process instance
    assertThat(logs.getEvents()
        .stream()
        .filter(event -> event.getMessage()
            .contains(
                formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", processInstance.getId(), "process")))
        .toList()
        .size()).isEqualTo(1); // Only the first skip
  }

  @Test
  public void shouldSkipUserTasksWhenProcessInstanceIsSkipped() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks();

    // then
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING_USER_TASK_MISSING_PROCESS, task.getId()));
  }

  @Test
  public void shouldNotMigrateAlreadySkippedUserTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateUserTasks();

    // then
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    var processInstance = historicProcesses.getFirst();
    assertThat(searchHistoricUserTasks(processInstance.processInstanceKey())).isEmpty();

    // and verify logs don't contain any additional skip operations for this process instance
    assertThat(logs.getEvents()
        .stream()
        .filter(event -> event.getMessage().contains(formatMessage(SKIPPING_USER_TASK_MISSING_PROCESS, task.getId())))
        .toList()
        .size()).isEqualTo(1); // Only the first skip from phase 1
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

    // when: Migrate incidents WITHOUT process instances (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateIncidents(); // Skips due to missing process instance

    // then: Incidents are skipped with a real-world scenario due to missing process instance
    assertThat(searchHistoricProcessInstances("failingServiceTaskProcessId")).isEmpty();
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_INCIDENT, incidentId));
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

    // First migration: Migrate incidents WITHOUT process instances (skipps with a real-world scenario)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateIncidents(); // Skips due to missing process instance

    // Second migration: Migrate process instances, but already-skipped incident should not be reprocessed
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateIncidents();

    // then: Process instance was migrated but incident remains skipped
    var historicProcesses = searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(1);
    ProcessInstanceEntity c8ProcessInstance = historicProcesses.getFirst();
    assertThat(searchHistoricIncidents(c8ProcessInstance.processDefinitionId())).isEmpty();

    // and verify logs don't contain any additional skip operations for this process instance
    assertThat(logs.getEvents()
        .stream()
        .filter(event -> event.getMessage().contains(formatMessage(SKIPPING_INCIDENT, incidentId)))
        .toList()
        .size()).isEqualTo(1); // Only the first skip from phase 1
  }

  @Disabled("TODO: https://github.com/camunda/camunda-bpm-platform/issues/5331")
  @WhiteBox
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

    // Capture variable IDs before migration
    var variableIds = historyService.createHistoricVariableInstanceQuery().list().stream()
        .map(v -> v.getId())
        .toList();
    assertThat(variableIds).isNotEmpty();

    // when: Migrate variables WITHOUT process instances (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateVariables(); // Skips due to missing process instance

    // then: Variables are skipped with a real-world scenario due to missing process instance
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // Verify all variables were skipped
    for (String variableId : variableIds) {
      logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_VARIABLE_MISSING_PROCESS, variableId));
    }
  }

  @Test
  public void shouldNotMigrateAlreadySkippedVariable() {
    // given: Process with multiple variables in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId",
        Map.of("testVar", "testValue", "anotherVar", "anotherValue"));
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // Capture variable IDs before migration
    var variableIds = historyService.createHistoricVariableInstanceQuery().list().stream()
        .map(v -> v.getId())
        .toList();
    assertThat(variableIds).hasSize(2);

    // First migration: Migrate variables WITHOUT process instances (skipps with a real-world scenario)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateVariables(); // All variables skip due to missing process instance

    // Verify all variables were skipped
    for (String variableId : variableIds) {
      logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_VARIABLE_MISSING_PROCESS, variableId));
    }

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
    var taskVariable = historyService.createHistoricVariableInstanceQuery().taskIdIn(taskId).singleResult();

    // when: Migrate user tasks and variables WITHOUT process instances (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks(); // Skips due to missing process instance
    historyMigrator.migrateVariables(); // Also skips

    // then: Process instance was not migrated, task and its variables were skipped
    var historicProcesses = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses.size()).isEqualTo(0);

    // Verify task and variable were skipped
    logs.assertContains(formatMessage(SKIPPING_USER_TASK_MISSING_PROCESS, taskId));
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_VARIABLE_MISSING_TASK, taskVariable.getId(), taskId));
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

    assertThat(historicActivities).isNotEmpty();

    String serviceTaskActivityInstanceId = historicActivities.getFirst().getId();

    // Find the service task local variable in history
    var serviceTaskVariable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("serviceTaskVar")
        .activityInstanceIdIn(serviceTaskActivityInstanceId)
        .singleResult();

    assertThat(serviceTaskVariable).isNotNull();

    // when: Migrate flow nodes and variables WITHOUT process instances (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateFlowNodes(); // Skips due to missing process instance
    historyMigrator.migrateVariables(); // Also skips

    // then: Process instance was not migrated, service task and variable were skipped
    var historicProcesses = searchHistoricProcessInstances("serviceTaskWithInputMappingProcessId");
    assertThat(historicProcesses.size()).isEqualTo(0);

    // Verify variable was skipped
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_VARIABLE_MISSING_PROCESS, serviceTaskVariable.getId()));
  }

  @Test
  public void shouldSkipDecisionDefinitionWhenDecisionRequirementsIsSkipped() {
    // given: DMN with requirements deployed in C7
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // Capture decision definition IDs
    var decisionDefinitionIds = repositoryService.createDecisionDefinitionQuery().list().stream()
        .map(dd -> dd.getId())
        .toList();
    assertThat(decisionDefinitionIds).hasSize(2);

    // when: Migrate decision definitions WITHOUT migrating requirements first
    // This creates a real-world skip scenario - definitions can't migrate without their parent requirements
    historyMigrator.migrateDecisionDefinitions();

    // then: Decision definitions are skipped with a real-world scenario due to missing requirements
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id")).isEmpty();
    assertThat(searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id")).isEmpty();

    // Verify all 2 specific decision definitions were skipped
    for (String definitionId : decisionDefinitionIds) {
      logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_DECISION_DEFINITION, definitionId));
    }
  }

  @Test
  public void shouldSkipDecisionInstanceWhenDecisionDefinitionIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT decision definitions (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing decision definition

    // then: Decision instances are skipped with a real-world scenario due to missing decision definition
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "decision", decisionInstanceId, "decision"));
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
    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT process definitions (creates real-world skip)
    historyMigrator.migrateDecisionDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing process definition

    // then: Decision instances are skipped with a real-world scenario due to missing process definition
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "decision", decisionInstanceId, "process"));
  }

  @Test
  public void shouldSkipDecisionInstanceWhenProcessInstanceIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT process instances (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateDecisionDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing process instance

    // then: Decision instances are skipped with a real-world scenario due to missing process instance
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_DECISION_INSTANCE_MISSING_PROCESS_INSTANCE, decisionInstanceId));
  }

  @Test
  public void shouldSkipDecisionInstanceWhenFlowNodeIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT flow nodes (creates real-world skip)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateDecisionDefinitions();
    historyMigrator.migrateDecisionInstances(); // Skips due to missing flow node

    // then: Decision instances are skipped with a real-world scenario due to missing flow node
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_DECISION_INSTANCE_MISSING_FLOW_NODE_INSTANCE, decisionInstanceId));
  }

  @Test
  @WhiteBox
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
