/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-order.bpmn", "order-review.form"})
class OrderTimerTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;
  private List<JobWorker> workers;

  @BeforeEach
  void openWorkers() {
    workers = OrderJobHandlers.open(client);
  }

  @AfterEach
  void closeWorkers() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void escalatesAfterOneDay() {
    ProcessInstanceEvent instance = startOrderAndAdvanceTimer();
    assertThat(instance)
        .hasActiveElements("Task_Escalate")
        .hasNoActiveElements("Task_Approve")
        .hasVariable("auditStarted", true);
  }

  @Test
  void completesEscalatedOrderAtExpectedEndEvent() {
    ProcessInstanceEvent instance = startOrderAndAdvanceTimer();
    assertThat(instance).hasActiveElements("Task_Escalate").hasNoActiveElements("Task_Approve");

    processTestContext.completeUserTask("Task_Escalate");
    assertThat(instance).isCompleted().hasCompletedElements("End_Escalated");
  }

  private ProcessInstanceEvent startOrderAndAdvanceTimer() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(Map.of("sku", "available"))
            .send()
            .join();

    assertThat(instance).hasActiveElements("Task_Approve");
    processTestContext.increaseTime(Duration.ofDays(1));
    return instance;
  }
}
