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
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-fulfillment.bpmn", "fulfillment-task.form"})
class FulfillmentScenarioTest {

  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldCompleteWorkAfterTwoDailyReminders() {
    String orderId = "order-42";
    Map<String, Object> variables = Map.of("orderId", orderId);
    CamundaClient client = processTestContext.createClient();
    processTestContext.mockJobWorker("book-carrier").thenComplete();
    processTestContext.mockChildProcess("shipping", Map.of("shipped", true));

    // Keep another instance active to verify task actions stay scoped to the intended instance.
    ProcessInstanceEvent decoyProcessInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fulfillment")
            .latestVersion()
            .variables(Map.of("orderId", "order-43"))
            .send()
            .join();
    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fulfillment")
            .latestVersion()
            .variables(variables)
            .send()
            .join();
    long processInstanceKey = processInstance.getProcessInstanceKey();
    UserTaskSelector completeWork = byElementId("CompleteWork", processInstanceKey);
    UserTaskSelector decoyCompleteWork =
        byElementId("CompleteWork", decoyProcessInstance.getProcessInstanceKey());

    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byKey(processInstanceKey))
                    .hasActiveElements("RemindColleague"))
        .as("RemindColleague")
        .then(
            () ->
                processTestContext.completeUserTask(
                    byElementId("RemindColleague", processInstanceKey)));
    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byKey(processInstanceKey))
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

    assertThat(processInstance).hasActiveElements("CompleteWork");
    assertThatUserTask(completeWork).isCreated();
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasNotActivatedElements("ColleagueReminded");
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 1);
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 1);
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 2);
    processTestContext.increaseTime(Duration.ofHours(12));
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 2);
    processTestContext.completeUserTask(completeWork);
    assertThatUserTask(decoyCompleteWork).isCreated();
    client.newCancelInstanceCommand(decoyProcessInstance.getProcessInstanceKey()).send().join();

    assertThat(processInstance).isCompleted().hasCompletedElements("WorkFinished");
    assertThat(processInstance).hasCompletedElement("ColleagueReminded", 2);
  }
}
