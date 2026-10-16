/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.camunda.bpm.example.springprocess.service.PaymentService;
import org.springframework.stereotype.Component;

@Component
public class OrderWorkers {

  private final PaymentService paymentService;

  public OrderWorkers(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @JobWorker(type = "charge-payment")
  public void chargePayment(@Variable(name = "amount") int amount) {
    paymentService.charge(amount);
  }

  @JobWorker(type = "ship-order")
  public void shipOrder() {}

  @JobWorker(type = "calculate-total")
  public Map<String, Object> calculateTotal(@Variable(name = "amount") int amount) {
    return Map.of("total", amount + 20);
  }

  @JobWorker(type = "post-deployment-work")
  public Map<String, Object> postDeploymentWork() {
    return Map.of("postDeploymentExecuted", true);
  }
}
