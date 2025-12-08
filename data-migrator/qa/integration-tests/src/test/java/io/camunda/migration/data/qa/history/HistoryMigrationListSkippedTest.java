/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.SkippedEntitiesLogParserUtils;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationListSkippedTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

    @RegisterExtension
    protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

    @Autowired
    protected HistoryService historyService;

    @Test
    public void shouldListAllSkippedHistoricEntities(CapturedOutput output) {
        // given
        deployer.deployCamunda7Process("comprehensiveSkippingTestProcess.bpmn");

        List<String> processInstanceIds = createTestProcessInstances();
        String processDefinitionId = getProcessDefinitionId();

        // Verify expected entities exist in C7
        verifyC7EntitiesExist();

        // Capture entity IDs before migration for strict assertions
        List<String> userTaskIds = historyService.createHistoricTaskInstanceQuery().list().stream()
            .map(HistoricTaskInstance::getId)
            .collect(Collectors.toList());
        List<String> incidentIds = historyService.createHistoricIncidentQuery().list().stream()
            .map(HistoricIncident::getId)
            .collect(Collectors.toList());
        List<String> variableIds = historyService.createHistoricVariableInstanceQuery().list().stream()
            .map(HistoricVariableInstance::getId)
            .collect(Collectors.toList());
        List<String> flowNodeIds = historyService.createHistoricActivityInstanceQuery().list().stream()
            .map(HistoricActivityInstance::getId)
            .collect(Collectors.toList());

        // Create real-world skip scenario by migrating instances without definition
        historyMigration.getMigrator().migrateProcessInstances();
        historyMigration.getMigrator().migrateFlowNodes();
        historyMigration.getMigrator().migrateUserTasks();
        historyMigration.getMigrator().migrateVariables();
        historyMigration.getMigrator().migrateIncidents();

        // Verify all entities were marked as skipped with specific IDs
        verifyEntitiesMarkedAsSkipped(processInstanceIds, userTaskIds, incidentIds, variableIds, flowNodeIds);

        // when
        historyMigration.getMigrator().setMode(LIST_SKIPPED);
        historyMigration.getMigrator().start();

        // then
        Map<String, List<String>> skippedEntitiesByType = SkippedEntitiesLogParserUtils.parseSkippedEntitiesOutput(output.getOut());
        verifySkippedEntitiesOutput(skippedEntitiesByType, processDefinitionId, processInstanceIds);
    }

    protected List<String> createTestProcessInstances() {
      List<String> processInstanceIds = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        var processInstance = runtimeService.startProcessInstanceByKey("comprehensiveSkippingTestProcessId",
            Map.of("testVar", "testValue" + i, "anotherVar", "anotherValue" + i));
        processInstanceIds.add(processInstance.getId());

        // Complete user tasks to generate user task history
        var tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        for (Task task : tasks) {
          taskService.setVariableLocal(task.getId(), "taskLocalVar", "taskValue" + i);
          taskService.complete(task.getId());
        }

        // Execute failing service task jobs to generate incidents
        triggerIncident(processInstance.getId());
      }
      return processInstanceIds;
    }

    protected String getProcessDefinitionId() {
        return repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("comprehensiveSkippingTestProcessId")
            .latestVersion()
            .singleResult()
            .getId();
    }

    protected void verifyC7EntitiesExist() {
        assertThat(historyService.createHistoricProcessInstanceQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricTaskInstanceQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricVariableInstanceQuery().count()).isEqualTo(9);
        assertThat(historyService.createHistoricIncidentQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricActivityInstanceQuery().count()).isEqualTo(12);
    }

    protected void verifyEntitiesMarkedAsSkipped(List<String> processInstanceIds,
                                                  List<String> userTaskIds,
                                                  List<String> incidentIds,
                                                  List<String> variableIds,
                                                  List<String> flowNodeIds) {
        // Verify exact counts match expected
        assertThat(processInstanceIds).hasSize(3);
        assertThat(userTaskIds).hasSize(3);
        assertThat(incidentIds).hasSize(3);
        assertThat(variableIds).hasSize(9);
        assertThat(flowNodeIds).hasSize(12);

        // Build regex patterns from constants by converting SLF4J placeholders to regex
        // Process instances: SKIPPING_INSTANCE_MISSING_DEFINITION expects "process", instanceId, "process"
        for (String id : processInstanceIds) {
            String pattern = convertToRegex(HistoryMigratorLogs.SKIPPING_INSTANCE_MISSING_DEFINITION, "process", id, "process");
            assertThat(logs.getEvents().stream()
                .anyMatch(event -> event.getMessage().matches(pattern)))
                .as("Process instance %s should be logged as skipped", id)
                .isTrue();
        }

        // User tasks: SKIPPING_USER_TASK_MISSING_PROCESS expects taskId
        for (String id : userTaskIds) {
            String pattern = convertToRegex(HistoryMigratorLogs.SKIPPING_USER_TASK_MISSING_PROCESS, id);
            assertThat(logs.getEvents().stream()
                .anyMatch(event -> event.getMessage().matches(pattern)))
                .as("User task %s should be logged as skipped", id)
                .isTrue();
        }

        // Incidents: SKIPPING_INCIDENT expects incidentId
        for (String id : incidentIds) {
            String pattern = convertToRegex(HistoryMigratorLogs.SKIPPING_INCIDENT, id);
            assertThat(logs.getEvents().stream()
                .anyMatch(event -> event.getMessage().matches(pattern)))
                .as("Incident %s should be logged as skipped", id)
                .isTrue();
        }

        // Variables can be skipped for multiple reasons (missing process, missing task, missing scope, etc.)
        // So we check if ANY skip message exists for each variable ID using regex pattern matching
        for (String id : variableIds) {
            // Pattern matches any variable skip message with the specific ID
            String pattern = convertToRegex(HistoryMigratorLogs.SKIPPING_VARIABLE, id);
            boolean foundSkipMessage = logs.getEvents().stream()
                .anyMatch(event -> event.getMessage().matches(pattern));
            assertThat(foundSkipMessage)
                .as("Variable %s should be logged as skipped", id)
                .isTrue();
        }

        // Flow nodes: SKIPPING_FLOW_NODE expects flowNodeId
        for (String id : flowNodeIds) {
            String pattern = convertToRegex(HistoryMigratorLogs.SKIPPING_FLOW_NODE, id);
            assertThat(logs.getEvents().stream()
                .anyMatch(event -> event.getMessage().matches(pattern)))
                .as("Flow node %s should be logged as skipped", id)
                .isTrue();
        }
    }

    /**
     * Converts an SLF4J log template with {} placeholders to a regex pattern for matching.
     * Escapes regex special characters and replaces {} with the provided arguments.
     */
    protected String convertToRegex(String template, Object... args) {
        String result = template;
        // Escape regex special characters except for []
        result = result.replace(".", "\\.");
        result = result.replace("[", "\\[");
        result = result.replace("]", "\\]");

        // Replace {} placeholders with actual values
        for (Object arg : args) {
            result = result.replaceFirst("\\{}", String.valueOf(arg));
        }

        // Wrap with .* for flexible matching
        return ".*" + result + ".*";
    }

    protected void verifySkippedEntitiesOutput(Map<String, List<String>> skippedEntitiesByType,
                                           String processDefinitionId, List<String> processInstanceIds) {
        // Verify all expected entity types are present
        // Expected entity types from the LIST_SKIPPED output (using IdKeyMapper.TYPE display names)
        String[] expectedEntityTypes = {
            IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_FLOW_NODE.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_USER_TASK.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_VARIABLE.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_INCIDENT.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE.getDisplayName(),
            TYPE.HISTORY_DECISION_INSTANCE_INPUT.getDisplayName(),
            TYPE.HISTORY_DECISION_INSTANCE_OUTPUT.getDisplayName()
        };

        assertThat(skippedEntitiesByType.keySet().toArray()).containsExactlyInAnyOrder(expectedEntityTypes);

        // Verify specific entities with expected counts and IDs
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(processInstanceIds);

        verifyHistoricEntitiesById(skippedEntitiesByType, processDefinitionId);

        // Verify empty entity types
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION.getDisplayName())).isEmpty();
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE.getDisplayName())).isEmpty();
    }

    protected void verifyHistoricEntitiesById(Map<String, List<String>> skippedEntitiesByType, String processDefinitionId) {
        // Verify user tasks
        List<String> expectedUserTaskIds = historyService.createHistoricTaskInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricTaskInstance::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_USER_TASK.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedUserTaskIds);

        // Verify incidents
        List<String> expectedIncidentIds = historyService.createHistoricIncidentQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricIncident::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_INCIDENT.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedIncidentIds);

        // Verify variables
        List<String> expectedVariableIds = historyService.createHistoricVariableInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricVariableInstance::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_VARIABLE.getDisplayName()))
            .hasSize(9)
            .containsAll(expectedVariableIds);

        // Verify flow nodes
        List<String> expectedFlowNodeIds = historyService.createHistoricActivityInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricActivityInstance::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_FLOW_NODE.getDisplayName()))
            .hasSize(12)
            .containsExactlyInAnyOrderElementsOf(expectedFlowNodeIds);
    }
}
