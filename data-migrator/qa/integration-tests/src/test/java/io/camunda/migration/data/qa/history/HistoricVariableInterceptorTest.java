/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_VARIABLE_INTERCEPTOR_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for selective skipping of historic variables when variable interception fails.
 */
@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].class-name=io.camunda.migrator.qa.history.interceptor.FailingHistoricVariableInterceptor"
})
public class HistoricVariableInterceptorTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.WARN);

  @Test
  public void shouldSkipOnlyFailingHistoricVariablesWhileSucceedingOthers() {
    // given: Process with variables - one that will fail, one that will succeed
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(processInstance.getId(), "failingVar", "FAIL");
    runtimeService.setVariable(processInstance.getId(), "successVar", "SUCCESS");

    String failingVariableId = historyMigration.getHistoryService()
        .createHistoricVariableInstanceQuery()
        .variableName("failingVar")
        .singleResult()
        .getId();

    String successVariableId = historyMigration.getHistoryService()
        .createHistoricVariableInstanceQuery()
        .variableName("successVar")
        .singleResult()
        .getId();

    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when: Migrate with conditional failing interceptor
    historyMigration.getMigrator().migrate();

    // then: Only the failing variable is skipped
    var successVars = historyMigration.searchHistoricVariables("successVar");
    var failingVars = historyMigration.searchHistoricVariables("failingVar");

    // successVar should be migrated
    assertThat(successVars).hasSize(1);
    assertThat(successVars.getFirst().value()).isEqualTo("\"SUCCESS\"");

    // failingVar should be skipped
    assertThat(failingVars).isEmpty();

    // Verify the failing one was logged as skipped
    logs.assertContains(formatMessage(SKIPPING_VARIABLE_INTERCEPTOR_ERROR,
        failingVariableId, "Test exception: Unsupported variable value FAIL"));

    // Verify the successful one was NOT logged as skipped
    logs.assertDoesNotContain(formatMessage(SKIPPING_VARIABLE_INTERCEPTOR_ERROR,
        successVariableId, "Test exception: Unsupported variable value FAIL"));
  }

  @Test
  public void shouldContinueMigrationAfterSkippingFailedHistoricVariable() {
    // given: Process with variables in order: success, fail, success
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Set variables in a specific order
    runtimeService.setVariable(processInstance.getId(), "var1", "value1");
    runtimeService.setVariable(processInstance.getId(), "failVar", "FAIL");
    runtimeService.setVariable(processInstance.getId(), "var2", "value2");

    String failingId = historyMigration.getHistoryService()
        .createHistoricVariableInstanceQuery()
        .variableName("failVar")
        .singleResult()
        .getId();

    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when: Migrate variables
    historyMigration.getMigrator().migrate();

    // then: Two variables are migrated, one is skipped
    assertThat(historyMigration.searchHistoricVariables("var1")).hasSize(1);
    assertThat(historyMigration.searchHistoricVariables("var2")).hasSize(1);
    assertThat(historyMigration.searchHistoricVariables("failVar")).isEmpty();

    // Verify the failing one was skipped
    logs.assertContains(formatMessage(SKIPPING_VARIABLE_INTERCEPTOR_ERROR,
        failingId, "Test exception: Unsupported variable value FAIL"));
  }

  @Test
  public void shouldSkipFailingVariableInOneProcessWhileSucceedingInAnother() {
    // given: Two processes with different variable values
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    ProcessInstance processInstance1 = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(processInstance1.getId(), "testVar", "FAIL");
    Task task1 = taskService.createTaskQuery().processInstanceId(processInstance1.getId()).singleResult();
    taskService.complete(task1.getId());

    ProcessInstance processInstance2 = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(processInstance2.getId(), "testVar", "SUCCESS");
    Task task2 = taskService.createTaskQuery().processInstanceId(processInstance2.getId()).singleResult();
    taskService.complete(task2.getId());

    // when: Migrate variables
    historyMigration.getMigrator().migrate();

    // then: First process has testVar skipped, second has it migrated
    var variables1 = historyMigration.searchHistoricVariables("testVar");

    // Should have 1 testVar from the second process instance only
    assertThat(variables1).hasSize(1);
    assertThat(variables1.getFirst().value()).isEqualTo("\"SUCCESS\"");
  }
}
