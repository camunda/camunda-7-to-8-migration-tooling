/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ForwardedHeadersCorsConfigurationTest {
  private static final String PUBLIC_ORIGIN = "https://diagram-converter.camunda.io";

  @LocalServerPort int port;

  @BeforeEach
  void setup() {
    RestAssured.port = port;
  }

  @Test
  void allowsSameOriginRequestWhenTlsTerminatesAtProxy() {
    final var response =
        RestAssured.given()
            .header("Origin", PUBLIC_ORIGIN)
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "diagram-converter.camunda.io")
            .header("X-Forwarded-Port", "443")
            .get("/version");

    assertThat(response.statusCode()).isEqualTo(200);
  }
}
