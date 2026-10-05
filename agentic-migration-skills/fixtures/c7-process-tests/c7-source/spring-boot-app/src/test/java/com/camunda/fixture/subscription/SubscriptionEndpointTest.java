/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:subscription-endpoint;DB_CLOSE_DELAY=-1")
public class SubscriptionEndpointTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private RuntimeService runtimeService;
  @MockBean private BillingClient billingClient;

  @Test
  public void startsSubscriptionFromHttp() {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/subscriptions", Map.of("subscriptionId", "sub-http"), Void.class);

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    verify(billingClient).activate("sub-http");
    assertEquals(
        1,
        runtimeService
            .createProcessInstanceQuery()
            .processDefinitionKey("subscription")
            .count());
  }
}
