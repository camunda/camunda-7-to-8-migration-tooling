/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history.entity;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryProcessDefinitionTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigrateHistoricProcessDefinitions() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn", "my-tenant1");
    deployer.deployCamunda7Process("userTaskProcess.bpmn", "my-tenant2");

    // when history is migrated
    historyMigrator.migrate();

    // then
    List<ProcessDefinitionEntity> processDefinitions = searchHistoricProcessDefinitions("userTaskProcessId");
    assertThat(processDefinitions.size()).isEqualTo(2);
    processDefinitions.forEach(definition -> {
      assertThat(definition.processDefinitionKey()).isNotNull();
      assertThat(definition.processDefinitionId()).isEqualTo("userTaskProcessId");
      assertThat(definition.name()).isEqualTo("UserTaskProcess");
      assertThat(definition.version()).isEqualTo(1);
      if (!definition.tenantId().equals("my-tenant1")) {
        assertThat(definition.tenantId()).isEqualTo("my-tenant2");
      }
      assertThat(definition.versionTag()).isEqualTo("custom-version-tag");
      assertThat(definition.bpmnXml()).isNotEmpty();
      assertThat(definition.formId()).isNull();
    });
  }
}
