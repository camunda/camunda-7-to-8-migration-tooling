/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.camunda.bpm.client.ExternalTaskClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = SharedEnginePaymentTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "shared-engine.test.enabled", matches = "true")
@ActiveProfiles("test")
class SharedEnginePaymentTest {

  @Value("${test.engine-rest-url}")
  private String engineRestUrl;

  private final TestRestTemplate restTemplate = new TestRestTemplate();

  @Test
  void startsAndCompletesThePaymentProcessOnTheConfiguredSharedEngine() {
    String businessKey = UUID.randomUUID().toString();
    ExternalTaskClient externalTaskClient =
        ExternalTaskClient.create().baseUrl(engineRestUrl).build();
    try {
      externalTaskClient
          .subscribe("charge-payment")
          .businessKey(businessKey)
          .handler(
              (externalTask, externalTaskService) -> {
                int amount = externalTask.getVariable("amount");
                externalTaskService.complete(
                    externalTask.getId(), Map.of("charged", amount > 0), null);
              })
          .open();

      ResponseEntity<JsonNode> started =
          restTemplate.postForEntity(
              engineRestUrl + "/process-definition/key/payment/start",
              Map.of(
                  "businessKey",
                  businessKey,
                  "variables",
                  Map.of("amount", Map.of("value", 42, "type", "Integer"))),
              JsonNode.class);

      assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode startedBody = started.getBody();
      assertThat(startedBody).isNotNull();
      String processInstanceId = startedBody.path("id").asText();
      assertThat(processInstanceId).isNotBlank();

      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                JsonNode history =
                    restTemplate.getForObject(
                        engineRestUrl + "/history/process-instance/{id}",
                        JsonNode.class,
                        processInstanceId);
                assertThat(history).isNotNull();
                assertThat(history.path("state").asText()).isEqualTo("COMPLETED");

                JsonNode variables =
                    restTemplate.getForObject(
                        engineRestUrl
                            + "/history/variable-instance?processInstanceId={id}&variableName=charged",
                        JsonNode.class,
                        processInstanceId);
                assertThat(variables).isNotNull();
                assertThat(variables.size()).isEqualTo(1);
                assertThat(variables.path(0).path("value").asBoolean()).isTrue();
              });
    } finally {
      externalTaskClient.stop();
    }
  }
}
