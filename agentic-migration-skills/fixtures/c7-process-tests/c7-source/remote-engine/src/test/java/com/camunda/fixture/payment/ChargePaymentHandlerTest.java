/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.payment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;

class ChargePaymentHandlerTest {

  @Test
  void completesExternalTask() {
    ExternalTask task = mock(ExternalTask.class);
    ExternalTaskService service = mock(ExternalTaskService.class);
    when(task.getVariable("amount")).thenReturn(42);

    new ChargePaymentHandler().execute(task, service);

    verify(service).complete(task, Map.of("charged", true, "chargedAmount", 42));
  }
}
