/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CamundaClusterHealthIndicatorTest {

  @Test
  void reportsUpWhenTheClusterStatusEndpointReturnsNoContent() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://localhost:8080/v2/status"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withNoContent());

    CamundaClusterHealthIndicator indicator =
        new CamundaClusterHealthIndicator(builder, "http://localhost:8080");

    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    server.verify();
  }

  @Test
  void reportsDownWhenTheClusterStatusEndpointReportsServiceUnavailable() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://localhost:8080/v2/status"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    CamundaClusterHealthIndicator indicator =
        new CamundaClusterHealthIndicator(builder, "http://localhost:8080");

    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    server.verify();
  }

  @Test
  void reportsDownWhenTheClusterRestApiIsUnavailable() {
    CamundaClusterHealthIndicator indicator =
        new CamundaClusterHealthIndicator(RestClient.builder(), "http://localhost:1");

    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }
}
