/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = PaymentApplication.class, webEnvironment = WebEnvironment.NONE)
class PaymentWorkerIT {

  private static final int ENGINE_PORT = 8080;

  @Container
  static final GenericContainer<?> camunda =
      new GenericContainer<>(DockerImageName.parse("camunda/camunda-bpm-platform:run-7.24.0"))
          .withExposedPorts(ENGINE_PORT)
          .waitingFor(Wait.forHttp("/engine-rest/engine").forStatusCode(200));

  @DynamicPropertySource
  static void configureExternalTaskClient(DynamicPropertyRegistry registry) {
    registry.add("camunda.bpm.client.base-url", PaymentWorkerIT::engineRestUrl);
  }

  @Test
  @Timeout(60)
  void chargesPaymentThroughEngineRest() {
    RestTemplate restTemplate = new RestTemplate();
    String engineRest = engineRestUrl();
    deployModel(restTemplate, engineRest);

    Map<?, ?> instance =
        restTemplate.postForObject(
            engineRest + "/process-definition/key/payment/start",
            Map.of(
                "variables",
                Map.of("amount", Map.of("value", 42, "type", "Integer"))),
            Map.class);
    assertThat(instance).isNotNull();
    assertThat(instance.containsKey("id")).isTrue();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              Map<?, ?> history =
                  restTemplate.getForObject(
                      engineRest + "/history/process-instance/" + instance.get("id"),
                      Map.class);
              assertThat(history).isNotNull();
              assertThat(history.get("state")).isEqualTo("COMPLETED");
            });
  }

  private void deployModel(RestTemplate restTemplate, String engineRest) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("data", new ClassPathResource("payment.bpmn"));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    restTemplate.postForEntity(
        engineRest + "/deployment/create",
        new HttpEntity<>(body, headers),
        Map.class);
  }

  private static String engineRestUrl() {
    return "http://localhost:" + camunda.getMappedPort(ENGINE_PORT) + "/engine-rest";
  }
}
