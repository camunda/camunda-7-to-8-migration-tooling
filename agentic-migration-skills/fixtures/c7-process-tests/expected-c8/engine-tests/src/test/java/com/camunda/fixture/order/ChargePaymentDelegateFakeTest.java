/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.Assert.assertEquals;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.community.mockito.delegate.DelegateExecutionFake;
import org.junit.Test;

public class ChargePaymentDelegateFakeTest {

  @Test
  public void writesPaymentReference() {
    DelegateExecutionFake execution = DelegateExecutionFake.of();
    new LegacyChargePaymentDelegate().execute(execution);

    assertEquals("payment-42", execution.getVariable("paymentReference"));
  }
}

class LegacyChargePaymentDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable(
        "paymentReference", new PaymentReferenceFactory().create("42"));
  }
}
