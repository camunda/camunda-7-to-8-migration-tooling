/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.webapp;

import static org.assertj.core.api.Assertions.*;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.File;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {"server.tomcat.max-part-count=2", "spring.servlet.multipart.resolve-lazily=true"})
public class ConverterExceptionHandlerTest {

  @LocalServerPort int port;

  @BeforeEach
  void setup() {
    RestAssured.port = port;
  }

  @Test
  void convertBatchExceedingFileCountLimit() throws URISyntaxException {
    Response response =
        RestAssured.given()
            .contentType(ContentType.MULTIPART)
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example.bpmn").toURI()))
            .multiPart(
                "file", new File(getClass().getClassLoader().getResource("example2.bpmn").toURI()))
            .formParam("appendDocumentation", true)
            .accept("application/zip")
            .post("/convertBatch");

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.jsonPath().getString("errorCode")).isEqualTo("FILE_COUNT_LIMIT_EXCEEDED");
    assertThat(response.jsonPath().getInt("limit")).isEqualTo(2);
  }
}
