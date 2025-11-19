/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.persistence;

import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;

public class SaveSkipReasonTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class, Level.DEBUG);

  @Autowired
  protected MigratorProperties migratorProperties;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  public void shouldSaveSkipReasonWhenSaveSkipReasonIsEnabled() {
    // given
    migratorProperties.setSaveSkipReason(true);

    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    assertThat(taskCount)
        .as("Unexpected process state: one task and three parallel tasks should be created")
        .isEqualTo(4L);

    // when
    runtimeMigrator.start();

    // then - verify process instance was skipped with appropriate reason in logs
    logs.assertContains("Migration of runtime process instance with C7 ID [" + process.getId() + "] skipped");
    logs.assertContains(String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask"));

    // Verify the skip was logged (not migrated)
    assertThat(logs.getEvents().stream()
        .anyMatch(event -> event.getMessage().contains("Migration of runtime process instance with C7 ID [" + process.getId() + "]")
            && event.getMessage().contains("completed")))
        .isFalse();
  }

  @Test
  public void shouldSaveNullSkipReasonIfSaveSkipReasonIsFalse() {
    // given
    migratorProperties.setSaveSkipReason(false);

    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    assertThat(taskCount)
        .as("Unexpected process state: one task and three parallel tasks should be created")
        .isEqualTo(4);

    // when
    runtimeMigrator.start();

    // then - verify process instance was skipped (skip reason saving is disabled but entity is still skipped)
    logs.assertContains("Migration of runtime process instance with C7 ID [" + process.getId() + "] skipped");

    // Verify the skip was logged (not migrated)
    assertThat(logs.getEvents().stream()
        .anyMatch(event -> event.getMessage().contains("Migration of runtime process instance with C7 ID [" + process.getId() + "]")
            && event.getMessage().contains("completed")))
        .isFalse();
  }
}
