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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@CamundaSpringProcessTest
@SpringBootTest(
    classes = TestSubscriptionApplication.class,
    properties = {
      "fixture.activate-worker.enabled=false",
      "fixture.housekeeping.auto-start=false"
    })
@TestDeployment(resources = {"converted-c8-subscription.bpmn", "subscription-task.form"})
class ActivateDelegateMockTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void mocksDelegateBean() {
    processTestContext
        .mockJobWorker("activate-subscription")
        .thenComplete(Map.of("activated", true));

    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("subscription")
            .latestVersion()
            .variables(Map.of("subscriptionId", "sub-mock"))
            .send()
            .join();

    assertThat(instance).hasActiveElements("Task_WelcomeCall");
  }
}
