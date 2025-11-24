/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.runtime.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;

import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class NoneStartEventNotFoundTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldNotSkipOnNoneStartEventPresent() {
    // given
    deployer.deployProcessInC7AndC8("noneStartProcess.bpmn");

    String id = runtimeService.startProcessInstanceByKey("noneStartProcess").getId();

    // assume
    ensureNotNull("Unexpected process state: process instance should exist",
        runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult());

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

  @Test
  public void shouldSkipOnMissingNoneStartEvent() {
    // given
    deployer.deployProcessInC7AndC8("messageStartEventProcess.bpmn");
     runtimeService.correlateMessage("msgRef");

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(".*C8 process definition \\[id: .+, version: .+\\] should have a None Start Event.*")))
        .hasSize(1);
  }

  @Test
  public void shouldNotSkipOnMultipleStartEventsAndNoneStartEventPresent() {
    // given
    deployer.deployProcessInC7AndC8("multipleStartEvent.bpmn");

    String id = runtimeService.startProcessInstanceByKey("multipleStartEvent").getId();

    // assume
    ensureNotNull("Unexpected process state: process instance should exist",
        runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult());

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

  @Test
  public void shouldSkipOnNoneStartEventOnlyExistInSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("messageStartEventWithSubprocess.bpmn");

    String id = runtimeService.startProcessInstanceByKey("messageStartEventWithSubprocess").getId();

    // assume
    ensureNotNull("Unexpected process state: process instance should exist",
        runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult());

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(".*C8 process definition \\[id: .+, version: .+\\] should have a None Start Event.*")))
        .hasSize(1);
  }
}
