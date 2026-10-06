/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessEngineExtension.class)
class OrderTimerTest {

  @Test
  @Deployment(resources = "order.bpmn")
  void escalatesAfterOneDay(ProcessEngine processEngine) {
    Mocks.register("orderAuditListener", new OrderAuditListener());
    ProcessInstance instance = processEngine.getRuntimeService().startProcessInstanceByKey("order");
    assertThat(instance).isWaitingAt("Task_Approve");

    ClockUtil.setCurrentTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
    execute(job());

    assertThat(instance).isWaitingAt("Task_Escalate").isNotWaitingAt("Task_Approve");
  }

  @AfterEach
  void resetClock() {
    ClockUtil.reset();
    Mocks.reset();
  }
}
