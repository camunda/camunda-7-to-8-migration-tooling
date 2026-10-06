/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-subscription.bpmn", "subscription-task.form"})
class SubscriptionStandaloneTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void startsSubscriptionWithoutSpring() {
    JobWorkerMock activationMock =
        processTestContext.mockJobWorker("activate-subscription").thenComplete();
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("subscription")
            .latestVersion()
            .variables(Map.of("subscriptionId", "sub-standalone"))
            .send()
            .join();

    assertThat(instance).hasActiveElements("Task_WelcomeCall");
    org.assertj.core.api.Assertions.assertThat(activationMock.getInvocations()).isEqualTo(1);
  }
}
