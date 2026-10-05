/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed to Camunda Services GmbH under the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.RepeatedTest;

@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-fulfillment.bpmn", "fulfillment-task.form"})
class FulfillmentScenarioTest {

  private CamundaProcessTestContext processTestContext;

  @RepeatedTest(2)
  void shouldCompleteWorkAfterTwoDailyReminders() {
    String orderId = "order-42";
    Map<String, Object> variables = Map.of("orderId", orderId);
    CamundaClient client = processTestContext.createClient();

    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byProcessId("fulfillment"))
                    .hasActiveElements("RemindColleague"))
        .as("RemindColleague")
        .then(() -> processTestContext.completeUserTask(byElementId("RemindColleague")));
    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byProcessId("fulfillment"))
                    .isWaitingForMessage("CarrierConfirmed", orderId))
        .as("CarrierConfirmed")
        .then(
            () ->
                client
                    .newCorrelateMessageCommand()
                    .messageName("CarrierConfirmed")
                    .correlationKey(orderId)
                    .variables(Map.of("carrierConfirmed", true))
                    .send()
                    .join());
    processTestContext.mockJobWorker("book-carrier").thenComplete();
    processTestContext.mockChildProcess("shipping", Map.of("shipped", true));

    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fulfillment")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    assertThat(processInstance).hasActiveElements("CompleteWork");
    assertThatUserTask(byElementId("CompleteWork")).isCreated();
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasNotActivatedElements("ColleagueReminded");
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 1);
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 1);
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 2);
    processTestContext.increaseTime(Duration.ofHours(12));
    processTestContext.completeUserTask(byElementId("CompleteWork"));

    assertThat(processInstance).isCompleted().hasCompletedElements("WorkFinished");
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 2);
  }
}
