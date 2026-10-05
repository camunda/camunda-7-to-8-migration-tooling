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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@CamundaSpringProcessTest
@SpringBootTest(
    classes = TestSubscriptionApplication.class,
    properties = "fixture.housekeeping.auto-start=false")
@TestDeployment(resources = {"converted-c8-housekeeping.bpmn", "housekeeping-task.form"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HousekeepingStartupTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;
  @Autowired private HousekeepingStarter housekeepingStarter;

  @Test
  void startsHousekeepingOnDeployment() {
    housekeepingStarter.startHousekeeping(client);
    assertThatProcessInstance(byProcessId("housekeeping")).hasActiveElements("Task_Review");
  }

  @Test
  void completesHousekeepingAfterReview() {
    housekeepingStarter.startHousekeeping(client);
    processTestContext.completeUserTask("Task_Review");
    assertThatProcessInstance(byProcessId("housekeeping")).isCompleted();
  }
}
