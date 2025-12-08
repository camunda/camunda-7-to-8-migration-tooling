/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.function.Supplier;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.page-size=4"
})
@CamundaSpringProcessTest
class SkippedProcessInstancesTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  @Test
  public void shouldMigrateSkippedProcessInstances() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    deployer.deployProcessInC7AndC8("miProcess.bpmn");

    for (int i = 0; i < 22; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
      runtimeService.startProcessInstanceByKey("miProcess");
    }

    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(22);

    runtimeService.createProcessInstanceQuery()
        .processDefinitionKey("miProcess")
        .list()
        .forEach(processInstance -> {
          taskService.createTaskQuery()
              .processInstanceId(processInstance.getId())
              .list()
              .stream()
              .map(Task::getId).forEach(taskService::complete);
        });

    runtimeMigration.getMigrator().setMode(RETRY_SKIPPED);

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(response.get().totalItems()).isEqualTo(22*2);
  }

}