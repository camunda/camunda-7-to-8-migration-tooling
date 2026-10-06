/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@CamundaProcessTest
class HousekeepingStartupTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void startsHousekeepingOnDeployment() throws Exception {
    new SubscriptionApplication()
        .deployProcessModels(client)
        .run(new DefaultApplicationArguments());
    publishApplicationReadyEvent();

    assertThatProcessInstance(byProcessId("housekeeping")).hasActiveElements("Task_Review");
    processTestContext.completeUserTask("Task_Review");
    assertThatProcessInstance(byProcessId("housekeeping")).isCompleted();
  }

  private void publishApplicationReadyEvent() {
    try (var applicationContext = new AnnotationConfigApplicationContext()) {
      applicationContext.registerBean(CamundaClient.class, () -> client);
      applicationContext.registerBean(HousekeepingStarter.class);
      applicationContext.refresh();
      applicationContext.publishEvent(
          new ApplicationReadyEvent(
              new SpringApplication(SubscriptionApplication.class),
              new String[0],
              applicationContext,
              Duration.ZERO));
    }
  }
}
