/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "fixture.activate-worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ActivateSubscriptionWorker {

  private final BillingClient billingClient;

  public ActivateSubscriptionWorker(BillingClient billingClient) {
    this.billingClient = billingClient;
  }

  @JobWorker(type = "activate-subscription")
  public Map<String, Object> activate(@Variable(name = "subscriptionId") String subscriptionId) {
    billingClient.activate(subscriptionId);
    return Map.of("activated", true);
  }
}
