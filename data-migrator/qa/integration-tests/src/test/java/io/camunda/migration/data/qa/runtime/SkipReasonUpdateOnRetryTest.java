/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import io.camunda.migration.data.qa.AbstractMigratorTest;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.NO_C8_DEPLOYMENT_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.NO_EXECUTION_LISTENER_OF_TYPE_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.NO_NONE_START_EVENT_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.BYTE_ARRAY_UNSUPPORTED_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public class SkipReasonUpdateOnRetryTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Autowired
  protected MigratorProperties migratorProperties;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  public void shouldUpdateSkipReasonWhenFailingActivateJob() {
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployProcessInC7AndC8("noMigratorListener.bpmn");
    Map<String, Object> variables = new HashMap<>();
    variables.put("unsupportedVar", "hello".getBytes());
    var process = runtimeService.startProcessInstanceByKey("noMigratorListener", variables);

    // when
    runtimeMigration.getMigrator().start();

    // then
    String originalSkipReason = String.format(NO_EXECUTION_LISTENER_OF_TYPE_ERROR, "migrator", "Event_1px2j50", "noMigratorListener", 1, "migrator");
    verifySkippedViaLogs(process, originalSkipReason);

    // given
    deployer.deployCamunda8Process("addedMigratorListenerProcess.bpmn");

    // when
    runtimeMigration.getMigrator().setMode(RETRY_SKIPPED);
    runtimeMigration.getMigrator().start();

    // then
    verifySkippedViaLogs(process, BYTE_ARRAY_UNSUPPORTED_ERROR);
  }

  @Test
  public void shouldUpdateSkipReasonWhenFailingBPMNValidation() {
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployCamunda7Process("messageStartEventProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("MessageStartEventProcessId");

    // when
    runtimeMigration.getMigrator().start();

    // then
    String expectedSkipReason = String.format(NO_C8_DEPLOYMENT_ERROR, "MessageStartEventProcessId", process.getId());
    verifySkippedViaLogs(process, expectedSkipReason);

    // given
    deployer.deployCamunda8Process("messageStartEventProcess.bpmn");

    // when
    runtimeMigration.getMigrator().setMode(RETRY_SKIPPED);
    runtimeMigration.getMigrator().start();

    // then
    String updatedSkipReason = String.format(NO_NONE_START_EVENT_ERROR, "MessageStartEventProcessId", 1);
    verifySkippedViaLogs(process, updatedSkipReason);
  }

  @Test
  public void shouldNotSaveSkipReasonDuringRetry() {
    // given
    migratorProperties.setSaveSkipReason(false);
    deployer.deployCamunda7Process("messageStartEventProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("MessageStartEventProcessId");

    // when
    runtimeMigration.getMigrator().start();

    // then
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);
    verifySkippedViaLogs(process, String.format(NO_C8_DEPLOYMENT_ERROR, "MessageStartEventProcessId", process.getId()));

    // given
    deployer.deployCamunda8Process("messageStartEventProcess.bpmn");

    // when
    runtimeMigration.getMigrator().setMode(RETRY_SKIPPED);
    runtimeMigration.getMigrator().start();

    // then
    verifySkippedViaLogs(process, String.format(NO_C8_DEPLOYMENT_ERROR, "MessageStartEventProcessId", process.getId()));
  }

  protected void verifySkippedViaLogs(ProcessInstance process, String expectedSkipReason) {
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);

    // Verify skip occurred via logs using constant from RuntimeMigratorLogs
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, process.getId(), expectedSkipReason));
  }
}
