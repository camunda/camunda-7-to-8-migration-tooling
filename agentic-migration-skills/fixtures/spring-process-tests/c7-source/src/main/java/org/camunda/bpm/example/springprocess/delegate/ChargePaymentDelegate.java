/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.example.springprocess.service.PaymentService;
import org.springframework.stereotype.Component;

@Component("chargePaymentDelegate")
public class ChargePaymentDelegate implements JavaDelegate {

  private final PaymentService paymentService;

  public ChargePaymentDelegate(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @Override
  public void execute(DelegateExecution execution) {
    paymentService.charge((Integer) execution.getVariable("amount"));
  }
}
