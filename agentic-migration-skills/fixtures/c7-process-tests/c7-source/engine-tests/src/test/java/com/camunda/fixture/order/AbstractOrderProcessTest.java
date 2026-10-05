/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mockito;

public abstract class AbstractOrderProcessTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Before
  public void registerOrderBeans() {
    Mocks.register("orderAuditListener", new OrderAuditListener());
    Mocks.register("chargePaymentDelegate", new ChargePaymentDelegate());
    Mocks.register("notificationService", Mockito.mock(NotificationService.class));
  }
}
