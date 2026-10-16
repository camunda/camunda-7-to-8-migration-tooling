/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.camunda.bpm.example.springprocess.service.OrderEffects;
import org.springframework.stereotype.Component;

@Component
public class RollbackWorkers {

  private final OrderEffects orderEffects;

  public RollbackWorkers(OrderEffects orderEffects) {
    this.orderEffects = orderEffects;
  }

  @JobWorker(type = "reserve-inventory")
  public void reserveInventory() {
    orderEffects.record("inventory");
  }

  @JobWorker(type = "charge-payment-rollback")
  public void chargePayment() {
    orderEffects.record("payment");
  }

  @JobWorker(type = "fail-after-payment", autoComplete = false)
  public void failAfterPayment(ActivatedJob job, JobClient jobClient) {
    jobClient
        .newFailCommand(job)
        .retries(0)
        .errorMessage("payment rejected after inventory reservation")
        .send()
        .join();
  }
}
