/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import java.util.Map;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineTestCase;

public class LegacyOrderTest extends ProcessEngineTestCase {

  @Deployment
  public void testStockMissing() {
    try {
      runtimeService.startProcessInstanceByKey("legacyOrder", Map.of("sku", "missing"));
      fail("Starting an order without stock must fail");
    } catch (IllegalStateException exception) {
      assertTrue(exception.getMessage().contains("Stock is unavailable"));
    }
  }
}
