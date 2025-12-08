/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.runtime.element;

import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

public class EventSubprocessMigrationTest extends AbstractElementMigrationTest {

  @Test
  public void shouldMigrateNonInterruptingEventSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("eventSubprocess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("eventSubprocessId");
    // Send signal event, to trigger the eventSubprocess in C7
    runtimeService.signalEventReceived("SignalEventName");

    // when
    runtimeMigrator.start();

    // then both user tasks exist in C8
    assertThat(byProcessId("eventSubprocessId")).isActive()
        .hasActiveElements(byId("userTaskId"))
        .hasActiveElements(byId("subprocessUserTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, processInstance.getProcessInstanceId());
    assertThat(byTaskName("userTaskName")).isCreated().hasElementId("userTaskId");
    assertThat(byTaskName("subprocessUserTaskName")).isCreated().hasElementId("subprocessUserTaskId");
  }

  @Test
  public void shouldMigrateInterruptingEventSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("interruptingEventSubprocess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("eventSubprocessId");
    // Send signal event, to trigger the eventSubprocess in C7
    runtimeService.signalEventReceived("SignalEventName");

    // when
    runtimeMigrator.start();

    // then canceled user task does not exist in C8
    assertThat(byProcessId("eventSubprocessId")).isActive()
        .hasNoActiveElements(byId("userTaskId"))
        .hasActiveElements(byId("subprocessUserTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, processInstance.getProcessInstanceId());
    assertThat(byTaskName("subprocessUserTaskName")).isCreated().hasElementId("subprocessUserTaskId");
  }
}
