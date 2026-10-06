/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

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
    CamundaClient client = processTestContext.createClient();

    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byProcessId("fulfillment"))
                    .hasActiveElements("RemindColleague"))
        .as("RemindColleague")
        .then(() -> processTestContext.completeUserTask("RemindColleague"));
    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byProcessId("fulfillment"))
                    .isWaitingForMessage("CarrierConfirmed", "order-42"))
        .as("CarrierConfirmed")
        .then(
            () ->
                client
                    .newCorrelateMessageCommand()
                    .messageName("CarrierConfirmed")
                    .correlationKey("order-42")
                    .variables(Map.of("carrierConfirmed", true))
                    .send()
                    .join());
    processTestContext.mockJobWorker("book-carrier").thenComplete();
    processTestContext.mockChildProcess("shipping", Map.of("shipped", true));

    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("fulfillment")
            .latestVersion()
            .variables(Map.of("orderId", "order-42"))
            .send()
            .join();

    assertThat(instance).hasActiveElements("CompleteWork");
    processTestContext.increaseTime(Duration.ofDays(1));
    assertThat(instance).hasCompletedElement("ColleagueReminded", 1);
    processTestContext.increaseTime(Duration.ofDays(1));
    assertThat(instance).hasCompletedElement("ColleagueReminded", 2);
    processTestContext.increaseTime(Duration.ofHours(12));
    processTestContext.completeUserTask("CompleteWork");

    assertThat(instance).isCompleted().hasCompletedElements("WorkFinished");
    assertThat(instance).hasCompletedElement("ColleagueReminded", 2);
  }
}
