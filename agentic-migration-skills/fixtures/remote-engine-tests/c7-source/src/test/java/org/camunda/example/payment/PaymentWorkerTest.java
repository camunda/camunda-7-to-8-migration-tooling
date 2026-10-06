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
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = PaymentWorkerApplication.class, webEnvironment = WebEnvironment.NONE)
@Testcontainers
class PaymentWorkerTest {

  @Container
  private static final GenericContainer<?> CAMUNDA =
      new GenericContainer<>(DockerImageName.parse("camunda/camunda-bpm-platform:run-7.24.0"))
          .withExposedPorts(8080)
          .waitingFor(Wait.forHttp("/engine-rest/engine").forStatusCode(200));

  @DynamicPropertySource
  static void configureExternalTaskClient(DynamicPropertyRegistry registry) {
    registry.add("camunda.bpm.client.base-url", PaymentWorkerTest::engineRest);
  }

  @Test
  void startsTheProcessThroughEngineRestAndObservesTheWorkerResult() throws IOException {
    RestTemplate restTemplate = new RestTemplate();
    deploy(restTemplate);

    JsonNode started =
        restTemplate.postForObject(
            engineRest() + "/process-definition/key/payment/start",
            Map.of(
                "variables",
                Map.of("amount", Map.of("value", 42, "type", "Integer"))),
            JsonNode.class);
    assertThat(started).isNotNull();
    String processInstanceId = started.path("id").asText();
    assertThat(processInstanceId).isNotBlank();

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      JsonNode history =
          restTemplate.getForObject(
              engineRest() + "/history/process-instance/" + processInstanceId, JsonNode.class);
      assertThat(history).isNotNull();
      assertThat(history.path("state").asText()).isEqualTo("COMPLETED");

      JsonNode variables =
          restTemplate.getForObject(
              engineRest()
                  + "/history/variable-instance?processInstanceId="
                  + processInstanceId
                  + "&variableName=charged",
              JsonNode.class);
      assertThat(variables).isNotNull();
      assertThat(variables.size()).isEqualTo(1);
      assertThat(variables.path(0).path("value").asBoolean()).isTrue();
    });
  }

  private static void deploy(RestTemplate restTemplate) throws IOException {
    byte[] model;
    try (var input = new ClassPathResource("payment.bpmn").getInputStream()) {
      model = input.readAllBytes();
    }

    ByteArrayResource bpmn =
        new ByteArrayResource(model) {
          @Override
          public String getFilename() {
            return "payment.bpmn";
          }
        };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("deployment-name", "payment");
    body.add("data", bpmn);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    ResponseEntity<JsonNode> response =
        restTemplate.postForEntity(
            engineRest() + "/deployment/create",
            new HttpEntity<>(body, headers),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private static String engineRest() {
    return "http://%s:%d/engine-rest"
        .formatted(CAMUNDA.getHost(), CAMUNDA.getMappedPort(8080));
  }
}
