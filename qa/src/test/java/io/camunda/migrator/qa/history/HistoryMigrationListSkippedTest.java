/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.qa.util.SkippedEntitiesLogParserUtils;
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
public class HistoryMigrationListSkippedTest extends HistoryMigrationAbstractTest {

    @RegisterExtension
    protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

    @Autowired
    protected HistoryService historyService;

    @Test
    public void shouldListAllSkippedHistoricEntities(CapturedOutput output) {
        // given multiple process instances with comprehensive entity generation
        deployer.deployCamunda7Process("comprehensiveSkippingTestProcess.bpmn");

        List<String> processInstanceIds = createTestProcessInstances();
        String processDefinitionId = getProcessDefinitionId();

        // Verify expected entities exist in C7
        verifyC7EntitiesExist();

        // Create natural skip scenario: Migrate instances without definition
        // This causes all child entities to naturally skip due to missing process definition
        historyMigrator.migrateProcessInstances();
        historyMigrator.migrateFlowNodes();
        historyMigrator.migrateUserTasks();
        historyMigrator.migrateVariables();
        historyMigrator.migrateIncidents();

        // Verify all entities were marked as skipped
        verifyEntitiesMarkedAsSkipped();

        // when running history migration with list skipped mode
        historyMigrator.setMode(LIST_SKIPPED);
        historyMigrator.start();

        // then verify the output contains all expected skipped entities
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
        assertThat(historyService.createHistoricVariableInstanceQuery().count()).isGreaterThan(6);
        assertThat(historyService.createHistoricIncidentQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricActivityInstanceQuery().count()).isEqualTo(12);
    }

    protected void verifyEntitiesMarkedAsSkipped() {
        // Verify entities were skipped via logs with counts
        logs.assertContains("Migration of historic process instance with C7 ID");
        assertThat(logs.getEvents().stream()
            .filter(event -> event.getMessage().contains("Migration of historic process instance with C7 ID"))
            .filter(event -> event.getMessage().contains("skipped"))
            .count()).isEqualTo(3);
        
        logs.assertContains("Migration of historic user task with C7 ID");
        assertThat(logs.getEvents().stream()
            .filter(event -> event.getMessage().contains("Migration of historic user task with C7 ID"))
            .filter(event -> event.getMessage().contains("skipped"))
            .count()).isEqualTo(3);
        
        logs.assertContains("Migration of historic incident with C7 ID");
        assertThat(logs.getEvents().stream()
            .filter(event -> event.getMessage().contains("Migration of historic incident with C7 ID"))
            .filter(event -> event.getMessage().contains("skipped"))
            .count()).isEqualTo(3);
        
        logs.assertContains("Migration of historic variable with C7 ID");
        assertThat(logs.getEvents().stream()
            .filter(event -> event.getMessage().contains("Migration of historic variable with C7 ID"))
            .filter(event -> event.getMessage().contains("skipped"))
            .count()).isGreaterThan(6);
        
        logs.assertContains("Migration of historic flow nodes with C7 ID");
        assertThat(logs.getEvents().stream()
            .filter(event -> event.getMessage().contains("Migration of historic flow nodes with C7 ID"))
            .filter(event -> event.getMessage().contains("skipped"))
            .count()).isEqualTo(12);
    }

    protected void verifySkippedEntitiesOutput(Map<String, List<String>> skippedEntitiesByType,
                                           String processDefinitionId, List<String> processInstanceIds) {
        // Verify all expected entity types are present
        // Expected entity types from the LIST_SKIPPED output
        String[] expectedEntityTypes = {
            "process definition",
            "process instance",
            "flow node instance",
            "user task",
            "variable",
            "incident",
            "decision definition",
            "decision requirements",
            "decision instance"
        };

        assertThat(skippedEntitiesByType).containsKeys(expectedEntityTypes);

        // Verify specific entities with expected counts and IDs
        assertThat(skippedEntitiesByType.get("process instance"))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(processInstanceIds);

        verifyHistoricEntitiesById(skippedEntitiesByType, processDefinitionId);

        // Verify empty entity types
        assertThat(skippedEntitiesByType.get("decision definition")).isEmpty();
        assertThat(skippedEntitiesByType.get("decision instance")).isEmpty();
    }

    protected void verifyHistoricEntitiesById(Map<String, List<String>> skippedEntitiesByType, String processDefinitionId) {
        // Verify user tasks
        List<String> expectedUserTaskIds = historyService.createHistoricTaskInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricTaskInstance::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("user task"))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedUserTaskIds);

        // Verify incidents
        List<String> expectedIncidentIds = historyService.createHistoricIncidentQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricIncident::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("incident"))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedIncidentIds);

        // Verify variables
        List<String> expectedVariableIds = historyService.createHistoricVariableInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricVariableInstance::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("variable"))
            .hasSize(9)
            .containsAll(expectedVariableIds);

        // Verify flow nodes
        List<String> expectedFlowNodeIds = historyService.createHistoricActivityInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(HistoricActivityInstance::getId)
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("flow node instance"))
            .hasSize(12)
            .containsExactlyInAnyOrderElementsOf(expectedFlowNodeIds);
    }
}
