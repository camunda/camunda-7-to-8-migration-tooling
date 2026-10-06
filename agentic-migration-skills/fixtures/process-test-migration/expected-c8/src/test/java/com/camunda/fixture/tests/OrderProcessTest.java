/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.tests;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;

@CamundaProcessTest
@TestDeployment(
    resources = "com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn")
class OrderProcessTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void approvesOrder() {
    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .variables(Map.of("amount", 100))
            .send()
            .join();

    assertThat(processInstance)
        .hasActiveElements("Task_Approve")
        .hasNoActiveElements("Task_Escalate");
    assertThatUserTask(UserTaskSelectors.byElementId("Task_Approve")).hasAssignee("demo");
    processTestContext.completeUserTask("Task_Approve", Map.of("approved", true));
    assertThat(processInstance)
        .hasCompletedElements("Task_Approve")
        .isCompleted()
        .hasVariable("approved", true);
  }

  @Test
  void escalatesAfterOneDay() {
    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .send()
            .join();

    assertThat(processInstance).hasActiveElements("Task_Approve");
    processTestContext.increaseTime(Duration.ofDays(1));
    assertThat(processInstance)
        .hasActiveElements("Task_Escalate")
        .hasNoActiveElements("Task_Approve");
  }

  @Test
  void continuesAfterAsync() {
    processTestContext.mockJobWorker("noopDelegate").thenComplete();

    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("async-process")
            .latestVersion()
            .send()
            .join();

    assertThat(processInstance).hasActiveElements("Task_AfterAsync");
  }

  @Test
  void failsWhenDelegateThrows() {
    processTestContext
        .mockJobWorker("failingDelegate")
        .withHandler(
            (jobClient, job) ->
                jobClient
                    .newFailCommand(job)
                    .retries(0)
                    .errorMessage("delegate failed")
                    .send()
                    .join());

    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("failing-process")
            .latestVersion()
            .send()
            .join();

    assertThat(processInstance).hasActiveIncidents();
  }
}
