/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

  @Test
  void appliesDiscount() {
    BigDecimal price = new PriceCalculator().applyDiscount(new BigDecimal("100.00"), new BigDecimal("0.15"));

    assertEquals(new BigDecimal("85.0000"), price);
  }
}
