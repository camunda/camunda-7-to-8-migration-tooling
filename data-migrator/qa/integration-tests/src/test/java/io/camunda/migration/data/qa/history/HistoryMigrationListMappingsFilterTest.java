/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.util.EntitiesLogParserUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationListMappingsFilterTest extends HistoryMigrationAbstractTest {

    @Test
    public void shouldListMigratedEntitiesWithSingleTypeFilter(CapturedOutput output) {
        // given
        deployer.deployCamunda7Process("simpleProcess.bpmn");

        runtimeService.startProcessInstanceByKey("simpleProcess");

        historyMigrator.migrate();

        // when
        historyMigrator.printMigratedHistoryEntities(List.of(HISTORY_PROCESS_INSTANCE));

        // then
        Map<String, List<String>> migratedEntitiesByType =
            EntitiesLogParserUtils.parseMigratedEntitiesOutput(output.getOut());

        // Should only contain process instances
        assertThat(migratedEntitiesByType).hasSize(1);
        assertThat(migratedEntitiesByType).containsKey(HISTORY_PROCESS_INSTANCE.getDisplayName());
        assertThat(migratedEntitiesByType.get(HISTORY_PROCESS_INSTANCE.getDisplayName()))
            .isNotEmpty();

        // Should not contain other entity types
        assertThat(migratedEntitiesByType).doesNotContainKey(HISTORY_PROCESS_DEFINITION.getDisplayName());
    }

    @Test
    public void shouldListMigratedEntitiesWithMultipleTypeFilters(CapturedOutput output) {
        // given
        deployer.deployCamunda7Process("simpleProcess.bpmn");

        runtimeService.startProcessInstanceByKey("simpleProcess");

        historyMigrator.migrate();

        // when
        historyMigrator.printMigratedHistoryEntities(List.of(
            HISTORY_PROCESS_DEFINITION,
            HISTORY_PROCESS_INSTANCE
        ));

        // then
        Map<String, List<String>> migratedEntitiesByType =
            EntitiesLogParserUtils.parseMigratedEntitiesOutput(output.getOut());

        // Should contain both requested types
        assertThat(migratedEntitiesByType).hasSize(2);
        assertThat(migratedEntitiesByType).containsKeys(
            HISTORY_PROCESS_DEFINITION.getDisplayName(),
            HISTORY_PROCESS_INSTANCE.getDisplayName()
        );

        // Should not contain other entity types
        assertThat(migratedEntitiesByType).doesNotContainKey(TYPE.HISTORY_VARIABLE.getDisplayName());
        assertThat(migratedEntitiesByType).doesNotContainKey(TYPE.HISTORY_FLOW_NODE.getDisplayName());
    }
}
