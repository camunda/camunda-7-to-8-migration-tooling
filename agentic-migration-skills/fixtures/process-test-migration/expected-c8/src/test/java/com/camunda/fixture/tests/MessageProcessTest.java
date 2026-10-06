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
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@CamundaProcessTest
@TestDeployment(
    resources = "com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn")
class MessageProcessTest {

  private CamundaClient client;

  @Test
  void correlatesMessage() {
    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("message-process")
            .latestVersion()
            .businessId("legacy-business-key")
            .variables(Map.of("orderId", "subscription-key"))
            .send()
            .join();
    assertEquals("legacy-business-key", processInstance.getBusinessId());
    assertThat(processInstance).isWaitingForMessage("ContinueMessage");

    client
        .newCorrelateMessageCommand()
        .messageName("ContinueMessage")
        .correlationKey("subscription-key")
        .variables(Map.of("messageReceived", true))
        .send()
        .join();

    assertThat(processInstance)
        .hasCompletedElements("Message_Catch")
        .isCompleted()
        .hasVariable("messageReceived", true);
  }
}
