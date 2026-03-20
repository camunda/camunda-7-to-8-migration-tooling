/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.util.EntitiesLogParserUtils;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationListMappingsTest extends HistoryMigrationAbstractTest {

    @Test
    public void shouldListMigratedHistoryEntities(CapturedOutput output) {
        // given
        deployer.deployCamunda7Process("simpleProcess.bpmn");

        String c7ProcessInstanceId1 = runtimeService.startProcessInstanceByKey("simpleProcess", "businessKey1").getId();
        String c7ProcessInstanceId2 = runtimeService.startProcessInstanceByKey("simpleProcess", "businessKey2").getId();

        historyMigrator.migrate();

        // when
        historyMigrator.printMigratedHistoryEntities(null);

        // then
        String outputStr = output.getOut();
        Map<String, List<String>> migratedEntitiesByType =
            EntitiesLogParserUtils.parseMigratedEntitiesOutput(outputStr);

        // Verify process definition mappings exist
        assertThat(migratedEntitiesByType).containsKey(TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName());
        assertThat(migratedEntitiesByType.get(TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName()))
            .isNotEmpty();

        // Verify process instance mappings exist
        assertThat(migratedEntitiesByType).containsKey(TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName());
        List<String> processInstanceMappings = migratedEntitiesByType.get(TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName());
        assertThat(processInstanceMappings).hasSize(2);

        // Verify the mapping contains the correct C7 IDs and C8 Keys
        List<ProcessInstanceEntity> c8Instances = searchHistoricProcessInstances("simpleProcess");
        assertThat(c8Instances).hasSize(2);
        Map<String, Long> businessKeyToC8Key = c8Instances.stream()
            .collect(Collectors.toMap(
                ProcessInstanceEntity::businessId,
                ProcessInstanceEntity::processInstanceKey));

        assertThat(processInstanceMappings).contains(
            c7ProcessInstanceId1 + " " + businessKeyToC8Key.get("businessKey1"),
            c7ProcessInstanceId2 + " " + businessKeyToC8Key.get("businessKey2"));
    }

    @Test
    public void shouldPrintOutputForAllHistoryEntityTypes(CapturedOutput output) {
        // given no migrated entities of any history type
        // when
        historyMigrator.printMigratedHistoryEntities(null);

        // then every history type should appear in the output
        String outputStr = output.getOut();
        List<String> expectedTypes = List.of(
            TYPE.HISTORY_PROCESS_DEFINITION.getDisplayName(),
            TYPE.HISTORY_PROCESS_INSTANCE.getDisplayName(),
            TYPE.HISTORY_INCIDENT.getDisplayName(),
            TYPE.HISTORY_VARIABLE.getDisplayName(),
            TYPE.HISTORY_USER_TASK.getDisplayName(),
            TYPE.HISTORY_FLOW_NODE.getDisplayName(),
            TYPE.HISTORY_DECISION_INSTANCE.getDisplayName(),
            TYPE.HISTORY_DECISION_DEFINITION.getDisplayName(),
            TYPE.HISTORY_DECISION_REQUIREMENT.getDisplayName(),
            TYPE.HISTORY_AUDIT_LOG.getDisplayName(),
            TYPE.HISTORY_FORM_DEFINITION.getDisplayName(),
            TYPE.HISTORY_JOB.getDisplayName()
        );
        for (String displayName : expectedTypes) {
            assertThat(
                outputStr.contains("No entities of type [" + displayName + "] were migrated"))
                .as("Expected output for type [%s]", displayName)
                .isTrue();
        }
    }
}
