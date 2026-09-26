/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class EmbeddedEngineHttpTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void servesApplicationAndEngineRestOnTheDefaultPort() {
    ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
    ResponseEntity<String> definitions =
        restTemplate.getForEntity("/engine-rest/engine/default/process-definition", String.class);

    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(definitions.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void startsAProcessThroughTheApplicationApi() {
    ResponseEntity<ProcessInstanceController.StartedProcess> response =
        restTemplate.postForEntity(
            "/api/process-instances",
            Map.of("source", "fixture"),
            ProcessInstanceController.StartedProcess.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().processInstanceId()).isNotBlank();
  }
}
