/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ExampleWebApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class WebTopologyIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @LocalServerPort private int applicationPort;

  @Value("${camunda.client.rest-address}")
  private String clusterRestAddress;

  @Test
  void startsTheAppAndUsesC8WhileTheClusterUsesASeparatePort() {
    int clusterPort = URI.create(clusterRestAddress).getPort();
    assertThat(applicationPort).isEqualTo(8081);
    assertThat(clusterPort).isEqualTo(8080);
    assertThat(applicationPort).isNotEqualTo(clusterPort);

    ResponseEntity<JsonNode> health =
        restTemplate.getForEntity("/actuator/health", JsonNode.class);
    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(health.getBody().path("status").asText()).isEqualTo("UP");
    assertThat(health.getBody().has("components")).isFalse();

    ResponseEntity<ProcessInstanceController.StartedProcess> started =
        restTemplate.postForEntity(
            "/api/process-instances",
            Map.of("source", "fixture"),
            ProcessInstanceController.StartedProcess.class);
    assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(started.getBody()).isNotNull();
    assertThat(started.getBody().processInstanceKey()).isPositive();

    ResponseEntity<String> oldEngineApi =
        restTemplate.getForEntity(
            "/engine-rest/engine/default/process-definition", String.class);
    assertThat(oldEngineApi.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
