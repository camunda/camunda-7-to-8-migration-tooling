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
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.servlet.multipart.resolve-lazily=true",
      "spring.servlet.multipart.max-file-size=3KB",
      "spring.servlet.multipart.max-request-size=8KB"
    })
class ConverterUploadSizeTest {

  @LocalServerPort int port;

  @BeforeEach
  void setup() {
    RestAssured.port = port;
  }

  @Test
  void rejectsFileExceedingFileSizeLimit() {
    Response response =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart("file", "large.bpmn", new byte[4096], "application/xml")
            .accept(ContentType.JSON)
            .post("/check");

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.jsonPath().getString("errorCode")).isEqualTo("FILE_SIZE_LIMIT_EXCEEDED");
    assertThat(response.jsonPath().getLong("maxUploadSize")).isEqualTo(3 * 1024);
  }

  @Test
  void rejectsRequestExceedingRequestSizeLimit() {
    Response response =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart("file", "first.bpmn", new byte[2300], "application/xml")
            .multiPart("file", "second.bpmn", new byte[2300], "application/xml")
            .formParam("defaultJobType", "x".repeat(4096))
            .accept("application/zip")
            .post("/convertBatch");

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.jsonPath().getString("errorCode")).isEqualTo("FILE_SIZE_LIMIT_EXCEEDED");
    assertThat(response.jsonPath().getLong("maxUploadSize")).isEqualTo(8 * 1024);
  }
}
