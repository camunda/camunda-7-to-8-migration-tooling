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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CamundaSpringProcessTest
@SpringBootTest(
    classes = TestSubscriptionApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "fixture.housekeeping.auto-start=false")
@TestDeployment(resources = {"converted-c8-subscription.bpmn", "subscription-task.form"})
class SubscriptionEndpointTest {

  @Autowired private TestRestTemplate restTemplate;
  @MockitoBean private BillingClient billingClient;

  @Test
  void startsSubscriptionFromHttp() {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/subscriptions", java.util.Map.of("subscriptionId", "sub-http"), Void.class);

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertThatProcessInstance(byProcessId("subscription")).hasActiveElements("Task_WelcomeCall");
    verify(billingClient).activate("sub-http");
  }
}
