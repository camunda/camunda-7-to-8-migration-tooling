/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static io.camunda.migration.data.MigratorMode.LIST_MIGRATED;
import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.util.EntitiesLogParserUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith(OutputCaptureExtension.class)
public class RuntimeMigrationListMappingsTest extends RuntimeMigrationAbstractTest {

    @Test
    public void shouldListMigratedProcessInstances(CapturedOutput output) {
        // given
        deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

        ProcessInstance pi1 = runtimeService.startProcessInstanceByKey("simpleProcess");
        ProcessInstance pi2 = runtimeService.startProcessInstanceByKey("simpleProcess");

        runtimeMigrator.start();

        assertThatProcessInstanceCountIsEqualTo(2);

        // when
        runtimeMigrator.setMode(LIST_MIGRATED);
        runtimeMigrator.start();

        // then
        String outputStr = output.getOut();
        Map<String, List<String>> migratedEntitiesByType =
            EntitiesLogParserUtils.parseMigratedEntitiesOutput(outputStr);

        assertThat(migratedEntitiesByType).containsKey(TYPE.RUNTIME_PROCESS_INSTANCE.getDisplayName());

        List<String> mappings = migratedEntitiesByType.get(TYPE.RUNTIME_PROCESS_INSTANCE.getDisplayName());
        assertThat(mappings).hasSize(2);

        // Fetch C8 process instances and verify full C7 ID -> C8 Key mapping lines
        List<io.camunda.client.api.search.response.ProcessInstance> c8Instances =
            camundaClient.newProcessInstanceSearchRequest().execute().items();
        assertThat(c8Instances).hasSize(2);

        for (io.camunda.client.api.search.response.ProcessInstance c8Instance : c8Instances) {
            Long c8Key = c8Instance.getProcessInstanceKey();
            // Look up the C7 ID via the legacy variable stored during migration
            Optional<io.camunda.client.api.search.response.Variable> legacyVar =
                getVariableByScope(c8Key, c8Key, LEGACY_ID_VAR_NAME);
            assertThat(legacyVar).isPresent();
            // Variable value is JSON-encoded (e.g. "\"4\""), strip surrounding quotes
            String c7Id = legacyVar.get().getValue().replaceAll("^\"|\"$", "");
            // Verify the full mapping line appears in the output
            assertThat(mappings).contains(c7Id + " " + c8Key);
        }
    }

    @Test
    public void shouldShowNoMappingsWhenNoInstancesMigrated(CapturedOutput output) {
        // given: no migration has been run

        // when
        runtimeMigrator.setMode(LIST_MIGRATED);
        runtimeMigrator.start();

        // then
        String outputStr = output.getOut();
        assertThat(outputStr).contains("No entities of type [" + TYPE.RUNTIME_PROCESS_INSTANCE.getDisplayName() + "] were migrated");
    }
}
