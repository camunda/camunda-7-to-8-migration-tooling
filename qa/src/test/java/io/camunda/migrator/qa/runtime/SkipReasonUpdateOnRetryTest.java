/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime;

import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_C8_DEPLOYMENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_EXECUTION_LISTENER_OF_TYPE_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_NONE_START_EVENT_ERROR;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.BYTE_ARRAY_UNSUPPORTED_ERROR;

import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.config.property.MigratorProperties;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

public class SkipReasonUpdateOnRetryTest extends RuntimeMigrationAbstractTest {

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
    // given: enable skip reason saving
    migratorProperties.setSaveSkipReason(true);

    // given: deploy process without migrator listener
    deployer.deployProcessInC7AndC8("noMigratorListener.bpmn");
    // and create a new process instance with an unsupported variable type
    Map<String, Object> variables = new HashMap<>();
    variables.put("unsupportedVar", "hello".getBytes());
    var process = runtimeService.startProcessInstanceByKey("noMigratorListener", variables);

    // when: run initial migration
    runtimeMigrator.start();

    // then: process instance should be skipped with missing listener error
    String originalSkipReason = String.format(NO_EXECUTION_LISTENER_OF_TYPE_ERROR, "migrator", "Event_1px2j50", "noMigratorListener", 1, "migrator");
    verifySkippedViaLogs(process, originalSkipReason);

    // given: fix the migrator listener issue by deploying correct C8 process
    deployer.deployCamunda8Process("addedMigratorListenerProcess.bpmn");

    // when: retry skipped process instances
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then: process instance skip reason is updated (verified via new log entry)
    verifySkippedViaLogs(process, BYTE_ARRAY_UNSUPPORTED_ERROR);
  }

  @Test
  public void shouldUpdateSkipReasonWhenFailingBPMNValidation() {
    // given: enable skip reason saving
    migratorProperties.setSaveSkipReason(true);

    // given: deploy process with message start event only in C7
    deployer.deployCamunda7Process("messageStartEventProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("MessageStartEventProcessId");

    // when: run initial migration
    runtimeMigrator.start();

    // then: process instance should be skipped with no C8 deployment error
    String expectedSkipReason = String.format(NO_C8_DEPLOYMENT_ERROR, "MessageStartEventProcessId", process.getId());
    verifySkippedViaLogs(process, expectedSkipReason);

    // given: deploy missing C8 process but without a none start event
    deployer.deployCamunda8Process("messageStartEventProcess.bpmn");

    // when: retry skipped process instances
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then: process instance should still be skipped but with updated reason (missing none start event)
    String updatedSkipReason = String.format(NO_NONE_START_EVENT_ERROR, "MessageStartEventProcessId", 1);
    verifySkippedViaLogs(process, updatedSkipReason);
  }

  @Test
  public void shouldNotSaveSkipReasonDuringRetry() {
    // given: disabled skip reason saving
    migratorProperties.setSaveSkipReason(false);

    // given: deploy process with message start event only in C7
    deployer.deployCamunda7Process("messageStartEventProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("MessageStartEventProcessId");

    // when: run initial migration
    runtimeMigrator.start();

    // then: process instance should be skipped (verified via logs)
    assertThatProcessInstanceCountIsEqualTo(0);
    logs.assertContains("Skipping process instance with C7 ID [" + process.getId() + "]");

    // given: deploy missing C8 process but without a none start event
    deployer.deployCamunda8Process("messageStartEventProcess.bpmn");

    // when: retry skipped process instances
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then: process instance should still be skipped (verified via logs)
    assertThatProcessInstanceCountIsEqualTo(0);
    logs.assertContains("Skipping process instance with C7 ID [" + process.getId() + "]");
  }

  protected void verifySkippedViaLogs(ProcessInstance process, String expectedSkipReason) {
    assertThatProcessInstanceCountIsEqualTo(0);

    // Verify skip occurred via logs containing the specific skip reason
    logs.assertContains("Skipping process instance with C7 ID [" + process.getId() + "]: " + expectedSkipReason);
  }
}
