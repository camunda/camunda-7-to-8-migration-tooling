/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.timer;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import java.util.Map;

final class TerminationDateUpdater {

  private static final String MESSAGE_NAME = "TerminationDateChanged";
  private final CamundaClient camundaClient;

  TerminationDateUpdater(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  CorrelateMessageResponse update(String projectId, String terminationDate) {
    return camundaClient
        .newCorrelateMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(projectId)
        .variables(Map.of("updatedTerminationDate", terminationDate))
        .send()
        .join();
  }
}
