/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.PresetProcessInstanceInterceptor",
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer",
    "camunda.migrator.interceptors[1].enabled=false"
})
public class HistoryPresetParentPropertiesTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldExecutePresetInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.migrateProcessInstances();

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances = searchHistoricProcessInstances("simpleProcess");
    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity processInstanceEntity = migratedProcessInstances.getFirst();
    assertThat(processInstanceEntity.processInstanceKey()).isEqualTo(88888L);
    assertThat(processInstanceEntity.processDefinitionKey()).isEqualTo(12345L);
  }
}
