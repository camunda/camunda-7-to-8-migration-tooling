/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessDefinitionEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;

@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryProcessDefinitionTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldMigrateHistoricProcessDefinitions() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn", null);
    deployer.deployCamunda7Process("userTaskProcess.bpmn", "my-tenant1");

<<<<<<< HEAD
    // when history is migrated
    historyMigration.getMigrator().migrate();
=======
    // when
    historyMigrator.migrate();
>>>>>>> main

    // then
    List<ProcessDefinitionEntity> processDefinitions = historyMigration.searchHistoricProcessDefinitions("userTaskProcessId");
    assertThat(processDefinitions).hasSize(2);
    processDefinitions.forEach(definition -> {
      assertThat(definition.processDefinitionKey()).isNotNull();
      assertThat(definition.processDefinitionId()).isEqualTo(prefixDefinitionId("userTaskProcessId"));
      assertThat(definition.name()).isEqualTo("UserTaskProcess");
      assertThat(definition.version()).isEqualTo(1);
      if (!definition.tenantId().equals("my-tenant1")) {
        assertThat(definition.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      }
      assertThat(definition.versionTag()).isEqualTo("custom-version-tag");
      assertThat(definition.bpmnXml()).isNotEmpty();
      assertThat(definition.formId()).isNull();
    });
  }
}
