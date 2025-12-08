/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_DECISION_INSTANCE_INTERCEPTOR_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for selective skipping of decision instances when variable interception fails.
 */
@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].class-name=io.camunda.migrator.qa.history.interceptor.FailingDecisionInterceptor"
})
public class HistoricDecisionInstanceInterceptorTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.WARN);

  @Test
  public void shouldSkipOnlyFailingDecisionInstancesWhileSucceedingOthers() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");

    // Start process instances - one that will fail, one that will succeed
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("FAIL")));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("SUCCESS")));

    var decisionInstances = historyMigration.getHistoryService()
        .createHistoricDecisionInstanceQuery().list();
    assertThat(decisionInstances).hasSize(2);

    String failingDecisionInstanceId = decisionInstances.get(0).getId();
    String successDecisionInstanceId = decisionInstances.get(1).getId();

    // when: Migrate decision instances with conditional failing interceptor
    historyMigration.getMigrator().migrate();

    // then: Only the failing decision instance is skipped
    var migratedDecisionInstances = historyMigration.searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(migratedDecisionInstances).hasSize(1);

    // Verify the failing one was logged as skipped
    logs.assertContains(formatMessage(SKIPPING_DECISION_INSTANCE_INTERCEPTOR_ERROR,
        failingDecisionInstanceId, "Test exception: Unsupported input value FAIL"));

    // Verify the successful one was NOT logged as skipped
    logs.assertDoesNotContain(formatMessage(SKIPPING_DECISION_INSTANCE_INTERCEPTOR_ERROR,
        successDecisionInstanceId, "Test exception: Unsupported input value FAIL"));
  }

  @Test
  public void shouldContinueMigrationAfterSkippingFailedDecisionInstance() {
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");

    // Start process instances in order: success, fail, success
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("FAIL")));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("B")));

    var decisionInstances = historyMigration.getHistoryService()
        .createHistoricDecisionInstanceQuery()
        .orderByEvaluationTime().asc().list();
    assertThat(decisionInstances).hasSize(3);

    String failingId = decisionInstances.get(1).getId();

    // when: Migrate decision instances
    historyMigration.getMigrator().migrate();

    // then: Two decision instances are migrated, one is skipped
    var migratedDecisionInstances = historyMigration.searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(migratedDecisionInstances).hasSize(2);

    // Verify the failing one was logged as skipped
    logs.assertContains(formatMessage(SKIPPING_DECISION_INSTANCE_INTERCEPTOR_ERROR,
        failingId, "Test exception: Unsupported input value FAIL"));
  }
}
