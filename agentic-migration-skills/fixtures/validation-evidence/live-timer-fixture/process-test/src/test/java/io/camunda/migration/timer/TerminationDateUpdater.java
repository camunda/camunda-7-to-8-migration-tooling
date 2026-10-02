/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.timer;

import io.camunda.client.CamundaClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

final class TerminationDateUpdater {

  private static final String MESSAGE_NAME = "TerminationDateChanged";
  private static final Duration MESSAGE_TTL = Duration.ofSeconds(30);
  private final CamundaClient camundaClient;

  TerminationDateUpdater(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  void update(String projectId, String terminationDate) {
    camundaClient
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(projectId)
        .messageId(UUID.randomUUID().toString())
        .timeToLive(MESSAGE_TTL)
        .variables(Map.of("updatedTerminationDate", terminationDate))
        .send()
        .join();
  }
}
