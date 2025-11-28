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

import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.qa.util.SkippedEntitiesLogParserUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationListSkippedTest extends HistoryMigrationAbstractTest {

    @Autowired
    protected DbClient dbClient;

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

        // Mark the process definition as skipped and run migration
        dbClient.insert(processDefinitionId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
        historyMigrator.migrate();

        // Verify all entities were marked as skipped
        verifyEntitiesMarkedAsSkipped();

        // when running history migration with list skipped mode
        historyMigrator.setMode(LIST_SKIPPED);
        historyMigrator.start();

        // then verify the output contains all expected skipped entities
        Map<String, List<String>> skippedEntitiesByType = SkippedEntitiesLogParserUtils.parseSkippedEntitiesOutput(output.getOut());
        verifySkippedEntitiesOutput(skippedEntitiesByType, processDefinitionId, processInstanceIds);
    }

    private List<String> createTestProcessInstances() {
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

    private String getProcessDefinitionId() {
        return repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("comprehensiveSkippingTestProcessId")
            .latestVersion()
            .singleResult()
            .getId();
    }

    private void verifyC7EntitiesExist() {
        assertThat(historyService.createHistoricProcessInstanceQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricTaskInstanceQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricVariableInstanceQuery().count()).isGreaterThan(6);
        assertThat(historyService.createHistoricIncidentQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricActivityInstanceQuery().count()).isEqualTo(12);
    }

    private void verifyEntitiesMarkedAsSkipped() {
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION)).isEqualTo(1);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(3);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(3);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(3);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isGreaterThan(6);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE)).isEqualTo(12);
    }

    private void verifySkippedEntitiesOutput(Map<String, List<String>> skippedEntitiesByType,
                                           String processDefinitionId, List<String> processInstanceIds) {
        // Verify all expected entity types are present
        String[] expectedEntityTypes = IdKeyMapper.getHistoryTypes()
            .stream()
            .map(IdKeyMapper.TYPE::getDisplayName)
            .toArray(String[]::new);

        assertThat(skippedEntitiesByType).containsKeys(expectedEntityTypes);

        // Verify specific entities with expected counts and IDs
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName()))
            .hasSize(1)
            .containsExactly(processDefinitionId);

        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(processInstanceIds);

        verifyHistoricEntitiesById(skippedEntitiesByType, processDefinitionId);

        // Verify empty entity types
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION.getDisplayName())).isEmpty();
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE.getDisplayName())).isEmpty();
    }

    private void verifyHistoricEntitiesById(Map<String, List<String>> skippedEntitiesByType, String processDefinitionId) {
        // Verify user tasks
        List<String> expectedUserTaskIds = historyService.createHistoricTaskInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(task -> task.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_USER_TASK.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedUserTaskIds);

        // Verify incidents
        List<String> expectedIncidentIds = historyService.createHistoricIncidentQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(incident -> incident.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_INCIDENT.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedIncidentIds);

        // Verify variables
        List<String> expectedVariableIds = historyService.createHistoricVariableInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(variable -> variable.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_VARIABLE.getDisplayName()))
            .hasSize(9)
            .containsAll(expectedVariableIds);

        // Verify flow nodes
        List<String> expectedFlowNodeIds = historyService.createHistoricActivityInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(activity -> activity.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_FLOW_NODE.getDisplayName()))
            .hasSize(12)
            .containsExactlyInAnyOrderElementsOf(expectedFlowNodeIds);
    }
}
