/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_REQUIREMENTS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FORM;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.qa.util.WhiteBox;
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
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;

@ExtendWith({ OutputCaptureExtension.class })
@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryMigrationSkippingTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

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
    var processInstanceIds = historyMigration.getHistoryService().createHistoricProcessInstanceQuery().list().stream()
        .map(pi -> pi.getId())
        .toList();
    var userTaskIds = historyMigration.getHistoryService().createHistoricTaskInstanceQuery().list().stream()
        .map(task -> task.getId())
        .toList();

    assertThat(processInstanceIds).hasSize(5);
    assertThat(userTaskIds).hasSize(5);

    // when
    historyMigration.getMigrator().migrateProcessInstances();
    historyMigration.getMigrator().migrateFlowNodes();
    historyMigration.getMigrator().migrateUserTasks();

    // then
    assertThat(historyMigration.searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // Verify all 5 specific process instances were skipped
    for (String instanceId : processInstanceIds) {
      logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(), instanceId, SKIP_REASON_MISSING_PROCESS_DEFINITION));
    }

    // Verify all 5 specific user tasks were skipped
    for (String taskId : userTaskIds) {
      logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_MISSING_PROCESS_INSTANCE));
    }
  }

  @Test
  public void shouldNotMigrateAlreadySkippedProcessInstance() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Migrate instances without definition (real-world skip scenario)
    historyMigration.getMigrator().migrateProcessInstances();
    
    // Verify the instance was skipped
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(), processInstance.getId(), SKIP_REASON_MISSING_PROCESS_DEFINITION));

    // Now migrate with definition - already skipped instance should not be reprocessed
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateProcessInstances();

    // then
    assertThat(historyMigration.searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // Verify logs don't contain any additional skip operations for this process instance
    assertThat(logs.getEvents()
        .stream()
        .filter(event -> event.getMessage()
            .contains(
                formatMessage(SKIPPING, TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(), processInstance.getId(), SKIP_REASON_MISSING_PROCESS_DEFINITION)))
        .toList()
        ).hasSize(1); // Only the first skip
  }

  @Test
  public void shouldSkipUserTasksWhenProcessInstanceIsSkipped() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateUserTasks();

    // then
<<<<<<< HEAD
    assertThat(historyMigration.searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING_USER_TASK_MISSING_PROCESS, task.getId()));
=======
    assertThat(searchHistoricProcessInstances("userTaskProcessId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_USER_TASK.getDisplayName(), task.getId(), SKIP_REASON_MISSING_PROCESS_INSTANCE));
>>>>>>> main
  }

  @Test
  public void shouldNotMigrateAlreadySkippedUserTask() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateUserTasks();
    historyMigration.getMigrator().migrateProcessInstances();
    historyMigration.getMigrator().migrateUserTasks();

    // then
    var historicProcesses = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses).hasSize(1);
    var processInstance = historicProcesses.getFirst();
    assertThat(historyMigration.searchHistoricUserTasks(processInstance.processInstanceKey())).isEmpty();

    // and verify logs don't contain any additional skip operations for this process instance
    assertThat(logs.getEvents()
        .stream()
        .filter(event -> event.getMessage().contains(formatMessage(SKIPPING, TYPE.HISTORY_USER_TASK.getDisplayName(), task.getId(), SKIP_REASON_MISSING_PROCESS_INSTANCE)))
        .toList()
        ).hasSize(1); // Only the first skip from phase 1
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
    assertThat(historyMigration.getHistoryService().createHistoricIncidentQuery().count()).as("Expected one incident to be created")
        .isEqualTo(1);
    String incidentId = historyMigration.getHistoryService().createHistoricIncidentQuery().singleResult().getId();

    // when: Migrate incidents WITHOUT process instances (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateIncidents(); // Skips due to missing process instance

    // then: Incidents are skipped with a real-world scenario due to missing process instance
<<<<<<< HEAD
    assertThat(historyMigration.searchHistoricProcessInstances("failingServiceTaskProcessId")).isEmpty();
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_INCIDENT, incidentId));
=======
    assertThat(searchHistoricProcessInstances("failingServiceTaskProcessId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_INCIDENT.getDisplayName(), incidentId, SKIP_REASON_MISSING_PROCESS_INSTANCE));
>>>>>>> main
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

    String incidentId = historyMigration.getHistoryService().createHistoricIncidentQuery().singleResult().getId();

    // First migration: Migrate incidents WITHOUT process instances (skipps with a real-world scenario)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateIncidents(); // Skips due to missing process instance

    // Second migration: Migrate process instances, but already-skipped incident should not be reprocessed
    historyMigration.getMigrator().migrateProcessInstances();
    historyMigration.getMigrator().migrateIncidents();

    // then: Process instance was migrated but incident remains skipped
    var historicProcesses = historyMigration.searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(historicProcesses).hasSize(1);
    ProcessInstanceEntity c8ProcessInstance = historicProcesses.getFirst();
    assertThat(historyMigration.searchHistoricIncidents(c8ProcessInstance.processDefinitionId())).isEmpty();

    // and verify logs don't contain any additional skip operations for this process instance
    assertThat(logs.getEvents()
        .stream()
        .filter(event -> event.getMessage().contains(formatMessage(SKIPPING, TYPE.HISTORY_INCIDENT.getDisplayName(), incidentId, SKIP_REASON_MISSING_PROCESS_INSTANCE)))
        .toList()
        ).hasSize(1); // Only the first skip from phase 1
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
<<<<<<< HEAD
    historyMigration.getDbClient().insert(job.getId(), null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
=======
    dbClient.insert(job.getId(), null, TYPE.HISTORY_FLOW_NODE);
>>>>>>> main

    // when history is migrated
    historyMigration.getMigrator().migrate();

    // then process instance was migrated but incident was skipped due to skipped job
    var historicProcesses = historyMigration.searchHistoricProcessInstances("failingServiceTaskProcessId");
    assertThat(historicProcesses).hasSize(1);
    ProcessInstanceEntity c8processInstance = historicProcesses.getFirst();
    assertThat(historyMigration.searchHistoricIncidents(c8processInstance.processDefinitionId())).isEmpty();

    // verify the incident was skipped
<<<<<<< HEAD
    assertThat(historyMigration.getDbClient().countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);
=======
    assertThat(dbClient.countSkippedByType(TYPE.HISTORY_INCIDENT)).isEqualTo(1);
>>>>>>> main
  }

  @Test
  public void shouldSkipVariablesWhenProcessInstanceIsSkipped() {
    // given: Process with variable in C7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId", Map.of("testVar", "testValue"));
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // Capture variable IDs before migration
    var variableIds = historyMigration.getHistoryService().createHistoricVariableInstanceQuery().list().stream()
        .map(v -> v.getId())
        .toList();
    assertThat(variableIds).isNotEmpty();

    // when: Migrate variables WITHOUT process instances (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateVariables(); // Skips due to missing process instance

    // then: Variables are skipped with a real-world scenario due to missing process instance
    assertThat(historyMigration.searchHistoricProcessInstances("userTaskProcessId")).isEmpty();

    // Verify all variables were skipped
    for (String variableId : variableIds) {
      logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_VARIABLE.getDisplayName(), variableId, SKIP_REASON_MISSING_PROCESS_INSTANCE));
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
    var variableIds = historyMigration.getHistoryService().createHistoricVariableInstanceQuery().list().stream()
        .map(v -> v.getId())
        .toList();
    assertThat(variableIds).hasSize(2);

    // First migration: Migrate variables WITHOUT process instances (skipps with a real-world scenario)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateVariables(); // All variables skip due to missing process instance

    // Verify all variables were skipped
    for (String variableId : variableIds) {
      logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_VARIABLE.getDisplayName(), variableId, SKIP_REASON_MISSING_PROCESS_INSTANCE));
    }

    // Second migration: Migrate process instances and variables again
    historyMigration.getMigrator().migrateProcessInstances();
    historyMigration.getMigrator().migrateVariables(); // Already skipped variables should not be reprocessed

    // then: Process instance was migrated but variables remain skipped
    var historicProcesses = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses).hasSize(1);
    
    // No variables were migrated - both remain skipped
    var variablesTestVar = historyMigration.searchHistoricVariables("testVar");
    var variablesAnotherVar = historyMigration.searchHistoricVariables("anotherVar");
    assertThat(variablesTestVar).hasSize(0);
    assertThat(variablesAnotherVar).hasSize(0);
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

    String taskId = historyMigration.getHistoryService().createHistoricTaskInstanceQuery().singleResult().getId();
    var taskVariable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery().taskIdIn(taskId).singleResult();

    // when: Migrate user tasks and variables WITHOUT process instances (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateUserTasks(); // Skips due to missing process instance
    historyMigration.getMigrator().migrateVariables(); // Also skips

    // then: Process instance was not migrated, task and its variables were skipped
    var historicProcesses = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses).hasSize(0);

    // Verify task and variable were skipped
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_USER_TASK.getDisplayName(), taskId, SKIP_REASON_MISSING_PROCESS_INSTANCE));
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_VARIABLE.getDisplayName(), taskVariable.getId(), SKIP_REASON_MISSING_PROCESS_INSTANCE));
  }

  @Test
  public void shouldSkipServiceTaskVariablesWhenServiceTaskIsSkipped() {
    // given: Process with service task using JUEL expression in C7
    deployer.deployCamunda7Process("serviceTaskWithInputMappingProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("serviceTaskWithInputMappingProcessId");

    // Find the service task in history
    var historicActivities = historyMigration.getHistoryService().createHistoricActivityInstanceQuery()
        .processInstanceId(processInstance.getId())
        .activityId("serviceTaskId")
        .list();

    assertThat(historicActivities).isNotEmpty();

    String serviceTaskActivityInstanceId = historicActivities.getFirst().getId();

    // Find the service task local variable in history
    var serviceTaskVariable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("serviceTaskVar")
        .activityInstanceIdIn(serviceTaskActivityInstanceId)
        .singleResult();

    assertThat(serviceTaskVariable).isNotNull();

    // when: Migrate flow nodes and variables WITHOUT process instances (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateFlowNodes(); // Skips due to missing process instance
    historyMigration.getMigrator().migrateVariables(); // Also skips

    // then: Process instance was not migrated, service task and variable were skipped
    var historicProcesses = historyMigration.searchHistoricProcessInstances("serviceTaskWithInputMappingProcessId");
    assertThat(historicProcesses).hasSize(0);

    // Verify variable was skipped
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_VARIABLE.getDisplayName(), serviceTaskVariable.getId(), SKIP_REASON_MISSING_PROCESS_INSTANCE));
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
    historyMigration.getMigrator().migrateDecisionDefinitions();

    // then: Decision definitions are skipped with a real-world scenario due to missing requirements
    assertThat(historyMigration.searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id")).isEmpty();
    assertThat(historyMigration.searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id")).isEmpty();

    // Verify all 2 specific decision definitions were skipped
    for (String definitionId : decisionDefinitionIds) {
      logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_DECISION_DEFINITION.getDisplayName(), definitionId, SKIP_REASON_MISSING_DECISION_REQUIREMENTS));
    }
  }

  @Test
  public void shouldSkipDecisionInstanceWhenDecisionDefinitionIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String decisionInstanceId = historyMigration.getHistoryService().createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT decision definitions (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateDecisionInstances(); // Skips due to missing decision definition

    // then: Decision instances are skipped with a real-world scenario due to missing decision definition
<<<<<<< HEAD
    assertThat(historyMigration.searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "decision", decisionInstanceId, "decision"));
=======
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_DECISION_INSTANCE.getDisplayName(), decisionInstanceId, SKIP_REASON_MISSING_DECISION_DEFINITION));
>>>>>>> main
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
    String decisionInstanceId = historyMigration.getHistoryService().createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT process definitions (creates real-world skip)
    historyMigration.getMigrator().migrateDecisionDefinitions();
    historyMigration.getMigrator().migrateDecisionInstances(); // Skips due to missing process definition

    // then: Decision instances are skipped with a real-world scenario due to missing process definition
<<<<<<< HEAD
    assertThat(historyMigration.searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "decision", decisionInstanceId, "process"));
=======
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_DECISION_INSTANCE.getDisplayName(), decisionInstanceId, SKIP_REASON_MISSING_PROCESS_DEFINITION));
>>>>>>> main
  }

  @Test
  public void shouldSkipDecisionInstanceWhenProcessInstanceIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    String decisionInstanceId = historyMigration.getHistoryService().createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT process instances (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateDecisionDefinitions();
    historyMigration.getMigrator().migrateDecisionInstances(); // Skips due to missing process instance

    // then: Decision instances are skipped with a real-world scenario due to missing process instance
<<<<<<< HEAD
    assertThat(historyMigration.searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_DECISION_INSTANCE_MISSING_PROCESS_INSTANCE, decisionInstanceId));
=======
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_DECISION_INSTANCE.getDisplayName(), decisionInstanceId, SKIP_REASON_MISSING_PROCESS_INSTANCE));
>>>>>>> main
  }

  @Test
  public void shouldSkipDecisionInstanceWhenFlowNodeIsSkipped() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    String decisionInstanceId = historyMigration.getHistoryService().createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT flow nodes (creates real-world skip)
    historyMigration.getMigrator().migrateProcessDefinitions();
    historyMigration.getMigrator().migrateProcessInstances();
    historyMigration.getMigrator().migrateDecisionDefinitions();
    historyMigration.getMigrator().migrateDecisionInstances(); // Skips due to missing flow node

    // then: Decision instances are skipped with a real-world scenario due to missing flow node
<<<<<<< HEAD
    assertThat(historyMigration.searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_DECISION_INSTANCE_MISSING_FLOW_NODE_INSTANCE, decisionInstanceId));
=======
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_DECISION_INSTANCE.getDisplayName(), decisionInstanceId, SKIP_REASON_MISSING_FLOW_NODE));
>>>>>>> main
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
<<<<<<< HEAD
    historyMigration.getDbClient().insert(taskId, null, IdKeyMapper.TYPE.HISTORY_INCIDENT);
    // Verify the collision record exists before completing the task
    assertThat(historyMigration.getDbClient().checkExistsByC7IdAndType(taskId, IdKeyMapper.TYPE.HISTORY_INCIDENT)).as(
=======
    dbClient.insert(taskId, null, TYPE.HISTORY_INCIDENT);
    // Verify the collision record exists before completing the task
    assertThat(dbClient.checkExistsByC7IdAndType(taskId, TYPE.HISTORY_INCIDENT)).as(
>>>>>>> main
        "Record with task ID should exist").isTrue();

    // when history is migrated
    historyMigration.getMigrator().migrate();

    // then
    // 1. Process instance should be migrated
    var historicProcesses = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(historicProcesses).hasSize(1);
    var processInstanceKey = historicProcesses.getFirst().processInstanceKey();

    // 2. User task should be migrated (not skipped due to ID collision with HISTORY_INCIDENT)
    var userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).as("User task should be migrated despite ID collision with HISTORY_INCIDENT").hasSize(1);
    assertThat(userTasks.getFirst().elementId()).as("User task should have correct id").isEqualTo("userTaskId");
  }

  @Test
  public void shouldSkipFlowNodeWhenProcessDefinitionNotMigrated() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    String id = historyService.createHistoricActivityInstanceQuery().activityId(USER_TASK_ID).singleResult().getId();
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();

    // then
    assertThat(searchFlowNodeInstancesByName(USER_TASK_ID)).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_FLOW_NODE.getDisplayName(), id, SKIP_REASON_MISSING_PROCESS_INSTANCE));
  }

  @Test
  public void shouldSkipFlowNodeWhenProcessInstanceNotMigrated() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    String id = historyService.createHistoricActivityInstanceQuery().activityId(USER_TASK_ID).singleResult().getId();
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateFlowNodes();

    // then
    assertThat(searchFlowNodeInstancesByName(USER_TASK_ID)).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_FLOW_NODE.getDisplayName(), id, SKIP_REASON_MISSING_PROCESS_INSTANCE));
  }

  @Test
  public void shouldSkipUserTaskWhenFormNotMigrated() {
    // given - deploy process with user task form
    deployer.deployCamunda7Process("processWithForm.bpmn");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instance
    runtimeService.startProcessInstanceByKey("processWithFormId");
    var task = taskService.createTaskQuery().singleResult();

    // and - migrate process definition and process instance, but NOT the form
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    // Note: historyMigrator.migrateForms() is NOT called here
    historyMigrator.migrateUserTasks();

    // then - user task should be skipped due to missing form
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(1);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey())).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_USER_TASK.getDisplayName(), task.getId(), SKIP_REASON_MISSING_FORM));
  }

  @Test
  public void shouldMigrateUserTaskAfterFormIsMigrated() {
    // given - deploy process with user task form
    deployer.deployCamunda7Process("processWithForm.bpmn");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instance
    runtimeService.startProcessInstanceByKey("processWithFormId");
    var task = taskService.createTaskQuery().singleResult();

    // and - first attempt: migrate without forms (should skip user task)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();
    historyMigrator.migrateUserTasks(); // Skips due to missing form

    // then - user task should be skipped
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(1);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey())).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, TYPE.HISTORY_USER_TASK.getDisplayName(), task.getId(), SKIP_REASON_MISSING_FORM));

    // when - second attempt: migrate forms and retry user tasks
    historyMigrator.migrateForms();

    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrateUserTasks(); // Should succeed now

    // then - user task should be migrated successfully
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNotNull();
  }
}
