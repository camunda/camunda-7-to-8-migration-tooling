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
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TimerElementMigrationTest extends AbstractElementMigrationTest {

  protected static final OffsetDateTime DATE_IN_TIMER_DATE_CATCH_PROCESS = OffsetDateTime.parse("2050-11-23T00:00:00Z");

  @Autowired
  protected CamundaProcessTestContext processTestContext;

  @Test
  public void shouldMigrateTimerInterruptingBoundaryWithDuration() {
    // given a process with a timer boundary event that has a duration of 10 days and leftover duration var of 1 day
    // the timer duration definition uses an expression as described in our workaround suggestions
    deployer.deployProcessInC7AndC8("timerDurationBoundaryEventProcess.bpmn");
    ProcessInstance c7instance = runtimeService.startProcessInstanceByKey("timerDurationBoundaryEventProcessId");
    runtimeService.setVariable(c7instance.getId(), "leftoverDuration", "P1D");

    // when
    runtimeMigration.getMigrator().start();

    // then timer has not yet fired
    assertThat(byProcessId("timerDurationBoundaryEventProcessId")).isActive()
        .hasActiveElements("userTaskId")
        .hasVariables(Map.of(
            LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId(),
            "leftoverDuration", "P1D")
        );

    // when
    processTestContext.increaseTime(Duration.ofDays(2));

    // then timer fires
    assertThat(byProcessId("timerDurationBoundaryEventProcessId")).isCompleted()
        .hasTerminatedElements("userTaskId")
        .hasCompletedElements( "timerEndEventId")
        .hasVariables(Map.of(
            LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId(),
            "leftoverDuration", "P1D")
        );
  }

  @Test
  public void shouldMigrateTimerBoundaryNonInterruptingWithCycle() {
    // given a process with a timer boundary event that has a cycle of 10 days
    deployer.deployProcessInC7AndC8("timerCycleBoundaryEventProcess.bpmn");
    ProcessInstance c7instance = runtimeService.startProcessInstanceByKey("timerCycleBoundaryEventProcessId");

    // when
    runtimeMigration.getMigrator().start();
    processTestContext.increaseTime(Duration.ofDays(11));

    // then
    assertThat(byProcessId("timerCycleBoundaryEventProcessId")).isActive()
        .hasActiveElement("userTaskId", 1)
        .hasCompletedElement( "timerEndEventId", 1)
        .hasVariable(LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId());

    // when
    processTestContext.increaseTime(Duration.ofDays(11));

    // then
    assertThat(byProcessId("timerCycleBoundaryEventProcessId")).isActive()
        .hasActiveElement("userTaskId", 1)
        .hasCompletedElement( "timerEndEventId", 2)
        .hasVariable(LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId());
  }

  @Test
  public void shouldMigrateTimerCatchWithDate() {
    // given
    deployer.deployProcessInC7AndC8("timerDateCatchProcess.bpmn");
    ProcessInstance c7instance = runtimeService.startProcessInstanceByKey("timerDateCatchProcessId");

    // when
    runtimeMigration.getMigrator().start();

    // then instance is still waiting in the timer catch event
    assertThat(byProcessId("timerDateCatchProcessId")).isActive()
        .hasActiveElements("timerCatchId")
        .hasVariable(LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId());

    // when advancing time to the timer due date
    processTestContext.increaseTime(Duration.between(processTestContext.getCurrentTime(), DATE_IN_TIMER_DATE_CATCH_PROCESS));

    // then
    assertThat(byProcessId("timerDateCatchProcessId")).isCompleted()
        .hasCompletedElements("timerCatchId", "timerCatchEndId")
        .hasVariable(LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId());
  }
}
