/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class CheckStockDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    if ("missing".equals(execution.getVariable("sku"))) {
      throw new IllegalStateException("Stock is unavailable");
    }
    execution.setVariable("stockChecked", true);
  }
}
