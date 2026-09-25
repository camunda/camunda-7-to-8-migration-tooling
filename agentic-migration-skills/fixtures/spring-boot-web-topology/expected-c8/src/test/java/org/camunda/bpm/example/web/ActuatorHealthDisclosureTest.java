/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ActuatorHealthDisclosureTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthDisclosureTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void hidesComponentDetailsFromThePublicHealthEndpoint() {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity("/actuator/health", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().path("status").asText()).isEqualTo("DOWN");
    assertThat(response.getBody().has("components")).isFalse();
    assertThat(response.getBody().toString()).doesNotContain("internal-camunda-cluster");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = "io.camunda.client.spring.configuration.CamundaAutoConfiguration")
  static class TestApplication {

    @Bean("camundaCluster")
    HealthIndicator camundaClusterHealthIndicator() {
      return () ->
          Health.down().withDetail("endpoint", "http://internal-camunda-cluster").build();
    }
  }
}
