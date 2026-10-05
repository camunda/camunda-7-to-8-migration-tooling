/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChargePaymentHandlerTest {

  @Test
  void completesPaymentTask() {
    Map<String, Object> output = new ChargePaymentWorker().charge(42);

    assertEquals(Map.of("charged", true, "chargedAmount", 42), output);
  }

  @Test
  void preservesFractionalPaymentAmount() {
    Object amount = 42.75d;

    Map<String, Object> output = new ChargePaymentWorker().charge(amount);

    assertEquals(Map.of("charged", true, "chargedAmount", amount), output);
  }

  @Test
  void preservesPaymentAmountBeyondIntegerRange() {
    Object amount = 2_147_483_648L;

    Map<String, Object> output = new ChargePaymentWorker().charge(amount);

    assertEquals(Map.of("charged", true, "chargedAmount", amount), output);
  }
}
