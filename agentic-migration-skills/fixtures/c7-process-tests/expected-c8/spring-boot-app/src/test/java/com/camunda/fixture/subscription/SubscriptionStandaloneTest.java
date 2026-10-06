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
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-subscription.bpmn", "subscription-task.form"})
class SubscriptionStandaloneTest {

  private CamundaClient client;

  @Test
  void startsSubscriptionWithoutSpring() {
    try (JobWorker worker =
        client.newWorker()
            .jobType("activate-subscription")
            .handler(
                (jobClient, job) ->
                    jobClient
                        .newCompleteCommand(job)
                        .variables(Map.of("activated", true))
                        .send()
                        .join())
            .open()) {
      ProcessInstanceEvent instance =
          client.newCreateInstanceCommand()
              .bpmnProcessId("subscription")
              .latestVersion()
              .variables(Map.of("subscriptionId", "sub-standalone"))
              .send()
              .join();

      assertThat(instance).hasActiveElements("Task_WelcomeCall");
    }
  }
}
