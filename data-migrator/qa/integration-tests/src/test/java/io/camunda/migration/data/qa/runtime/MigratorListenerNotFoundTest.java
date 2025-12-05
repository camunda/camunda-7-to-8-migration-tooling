/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.date.runtime;

import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.NO_EXECUTION_LISTENER_OF_TYPE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MigratorListenerNotFoundTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldSkipOnMissingListener() {
    // given
    deployer.deployProcessInC7AndC8("noMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("noMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream()
        .filter(event -> event.getMessage()
            .matches(String.format(".*" + String.format(
                SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("[{}]", "\\[%s\\]").replace("{}", "%s"), id,
                String.format(
                    NO_EXECUTION_LISTENER_OF_TYPE_ERROR.replace(".", "\\.").replace("[", "\\[").replace("]", "\\]"),
                    "migrator", "Event_1px2j50", "noMigratorListener", 1, "migrator")))))).hasSize(1);
  }

  @Test
  public void shouldSkipOnListenerWithWrongType() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerCustomType.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerCustomType").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream()
        .filter(event -> event.getMessage()
            .matches(String.format(".*" + String.format(
                SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("[{}]", "\\[%s\\]").replace("{}", "%s"), id,
                String.format(
                    NO_EXECUTION_LISTENER_OF_TYPE_ERROR.replace(".", "\\.").replace("[", "\\[").replace("]", "\\]"),
                    "migrator", "Event_1px2j50", "migratorListenerCustomType", 1, "migrator")))))).hasSize(1);
  }

  @Test
  public void shouldNotSkipOnMissingListenerWithEmbeddedSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("embeddedSubprocessWithoutMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("embeddedSubprocessWithoutMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

}
