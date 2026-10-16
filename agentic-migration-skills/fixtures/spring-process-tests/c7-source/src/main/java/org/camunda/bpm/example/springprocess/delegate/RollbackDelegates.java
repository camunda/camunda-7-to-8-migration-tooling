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
import org.camunda.bpm.example.springprocess.service.OrderEffects;
import org.springframework.stereotype.Component;

public final class RollbackDelegates {

  private RollbackDelegates() {}

  @Component("reserveInventory")
  public static class ReserveInventory implements JavaDelegate {

    private final OrderEffects orderEffects;

    public ReserveInventory(OrderEffects orderEffects) {
      this.orderEffects = orderEffects;
    }

    @Override
    public void execute(DelegateExecution execution) {
      orderEffects.record("inventory");
    }
  }

  @Component("chargePayment")
  public static class ChargePayment implements JavaDelegate {

    private final OrderEffects orderEffects;

    public ChargePayment(OrderEffects orderEffects) {
      this.orderEffects = orderEffects;
    }

    @Override
    public void execute(DelegateExecution execution) {
      orderEffects.record("payment");
    }
  }

  @Component("failAfterPayment")
  public static class FailAfterPayment implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
      throw new IllegalStateException("payment rejected after inventory reservation");
    }
  }
}
