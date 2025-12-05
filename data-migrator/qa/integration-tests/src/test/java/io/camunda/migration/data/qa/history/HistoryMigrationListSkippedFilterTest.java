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

import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.util.SkippedEntitiesLogParserUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationListSkippedFilterTest extends HistoryMigrationAbstractTest {

    @Autowired
    protected ManagementService managementService;

    @Autowired
    protected HistoryService historyService;

    @Test
    public void shouldListSkippedEntitiesWithSingleTypeFilter(CapturedOutput output) {
        // given
        deployer.deployCamunda7Process("comprehensiveSkippingTestProcess.bpmn");

        List<String> processInstanceIds = createTestProcessInstances();
        String processDefinitionId = getProcessDefinitionId();

        // Create real-world skip scenario by migrating instances without definition
        historyMigrator.migrateProcessInstances();

        // when
        historyMigrator.setMode(LIST_SKIPPED);
        historyMigrator.setRequestedEntityTypes(List.of(TYPE.HISTORY_PROCESS_INSTANCE));
        historyMigrator.start();

        // then
        Map<String, List<String>> skippedEntitiesByType = SkippedEntitiesLogParserUtils.parseSkippedEntitiesOutput(output.getOut());

        // Should only contain process instances
        assertThat(skippedEntitiesByType).hasSize(1);
        assertThat(skippedEntitiesByType).containsKey(TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName());
        assertThat(skippedEntitiesByType.get(TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(processInstanceIds);

        // Should not contain other entity types
        assertThat(skippedEntitiesByType).doesNotContainKey(TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName());
        assertThat(skippedEntitiesByType).doesNotContainKey(TYPE.HISTORY_USER_TASK.getDisplayName());
        assertThat(skippedEntitiesByType).doesNotContainKey(TYPE.HISTORY_VARIABLE.getDisplayName());
    }

    @Test
    public void shouldListSkippedEntitiesWithMultipleTypeFilters(CapturedOutput output) {
        // given
        deployer.deployCamunda7Process("comprehensiveSkippingTestProcess.bpmn");

        List<String> processInstanceIds = createTestProcessInstances();
        String processDefinitionId = getProcessDefinitionId();

        // Create real-world skip scenario by migrating instances and tasks without definition
        historyMigrator.migrateProcessInstances();
        historyMigrator.migrateUserTasks();

        // when
        historyMigrator.setMode(LIST_SKIPPED);
        historyMigrator.setRequestedEntityTypes(List.of(
            IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE,
            IdKeyMapper.TYPE.HISTORY_USER_TASK
        ));
        historyMigrator.start();

        // then
        Map<String, List<String>> skippedEntitiesByType = SkippedEntitiesLogParserUtils.parseSkippedEntitiesOutput(output.getOut());

        // Should only contain process instances and user tasks
        assertThat(skippedEntitiesByType).hasSize(2);
        assertThat(skippedEntitiesByType).containsKeys(
            IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(),
            IdKeyMapper.TYPE.HISTORY_USER_TASK.getDisplayName()
        );

        // Verify process instances
        assertThat(skippedEntitiesByType.get(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName()))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(processInstanceIds);

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

        // Should not contain other entity types
        assertThat(skippedEntitiesByType).doesNotContainKey(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName());
        assertThat(skippedEntitiesByType).doesNotContainKey(IdKeyMapper.TYPE.HISTORY_VARIABLE.getDisplayName());
        assertThat(skippedEntitiesByType).doesNotContainKey(IdKeyMapper.TYPE.HISTORY_INCIDENT.getDisplayName());
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
            var jobs = managementService.createJobQuery().processInstanceId(processInstance.getId()).list();
            for (var job : jobs) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        managementService.executeJob(job.getId());
                    } catch (Exception e) {
                        // Expected - job will fail and create incident
                    }
                }
            }
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
}
