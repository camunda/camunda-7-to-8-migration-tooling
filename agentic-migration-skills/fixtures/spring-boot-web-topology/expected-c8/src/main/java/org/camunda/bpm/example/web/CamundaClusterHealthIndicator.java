/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("camundaCluster")
public class CamundaClusterHealthIndicator implements HealthIndicator {

  private final RestClient restClient;

  public CamundaClusterHealthIndicator(
      RestClient.Builder builder, @Value("${camunda.client.rest-address}") String restAddress) {
    this.restClient = builder.baseUrl(restAddress).build();
  }

  @Override
  public Health health() {
    try {
      restClient.get().uri("/v2/topology").retrieve().toBodilessEntity();
      return Health.up().build();
    } catch (RestClientException exception) {
      return Health.down(exception).build();
    }
  }
}
