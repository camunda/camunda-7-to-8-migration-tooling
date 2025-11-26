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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SkipReasonUpdateOnRetryTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private MigratorProperties migratorProperties;

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
    String originalSKipReason = String.format(NO_EXECUTION_LISTENER_OF_TYPE_ERROR, "migrator", "Event_1px2j50", "noMigratorListener", 1, "migrator");
    verifySkipped(process, originalSKipReason);

    // given: fix the migrator listener issue by deploying correct C8 process
    deployer.deployCamunda8Process("addedMigratorListenerProcess.bpmn");

    // when: retry skipped process instances
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then: process instance skipp reason is updated
    verifySkipped(process, BYTE_ARRAY_UNSUPPORTED_ERROR);
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
    verifySkipped(process, expectedSkipReason);

    // given: deploy missing C8 process but without a none start event
    deployer.deployCamunda8Process("messageStartEventProcess.bpmn");

    // when: retry skipped process instances
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then: process instance should still be skipped but with updated reason (missing none start event)
    String updatedSkipReason = String.format(NO_NONE_START_EVENT_ERROR, "MessageStartEventProcessId", 1);
    verifySkipped(process, updatedSkipReason);
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

    // then: process instance should be skipped with null skip reason
    verifySkipped(process, null);

    // given: deploy missing C8 process but without a none start event
    deployer.deployCamunda8Process("messageStartEventProcess.bpmn");

    // when: retry skipped process instances
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then: process instance should still be skipped with null skip reason
    verifySkipped(process, null);
  }

  private void verifySkipped(ProcessInstance process, String expectedSkipReason) {
    assertThatProcessInstanceCountIsEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = findSkippedRuntimeProcessInstances().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().getC7Id()).isEqualTo(process.getId());

    // verify initial skip reason contains no none start event error
    String initialSkipReason = skippedProcessInstanceIds.getFirst().getSkipReason();
    assertThat(initialSkipReason).isEqualTo(expectedSkipReason);
  }
}
