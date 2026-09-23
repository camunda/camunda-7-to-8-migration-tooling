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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class CorsConfigurationTest {
  private static final String ALLOWED_ORIGIN = "http://localhost:5173";
  private static final String DISALLOWED_ORIGIN = "https://untrusted.example";

  @LocalServerPort int port;

  @BeforeEach
  void setup() {
    RestAssured.port = port;
  }

  @Test
  void allowsConfiguredOrigin() {
    final var response = RestAssured.given().header("Origin", ALLOWED_ORIGIN).get("/version");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
  }

  @Test
  void exposesContentDispositionToConfiguredOrigin() {
    final var response = RestAssured.given().header("Origin", ALLOWED_ORIGIN).get("/version");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.getHeader("Access-Control-Expose-Headers"))
        .isEqualTo("Content-Disposition");
  }

  @Test
  void rejectsOriginNotInConfiguration() {
    final var response = RestAssured.given().header("Origin", DISALLOWED_ORIGIN).get("/version");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
  }

  @Test
  void allowsNonBrowserClientsWithoutAnOriginHeader() {
    final var response = RestAssured.get("/version");

    assertThat(response.statusCode()).isEqualTo(200);
  }
}
