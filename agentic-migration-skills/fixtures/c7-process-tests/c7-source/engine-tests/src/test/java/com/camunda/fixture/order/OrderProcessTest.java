/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.Test;

public class OrderProcessTest extends AbstractOrderProcessTest {

  @Test
  @Deployment(resources = {"order.bpmn", "shipping.bpmn", "discount.dmn"})
  public void approvesAndShipsOrder() {
    ProcessInstance instance =
        runtimeService()
            .startProcessInstanceByKey(
                "order",
                "order-1",
                Map.of("sku", "available", "customerType", "gold"));

    assertThat(instance).isWaitingAt("Task_Approve");
    assertThat(task()).isAssignedTo("demo");
    complete(task(), withVariables("approved", true));
    assertThat(instance).isWaitingAt("Task_ChargePayment");

    execute(job());
    assertThat(instance).isWaitingFor("PaymentConfirmed");
    runtimeService()
        .correlateMessage(
            "PaymentConfirmed",
            "order-1",
            Map.of("paymentConfirmed", true));

    assertThat(instance)
        .hasPassed("CallActivity_Ship")
        .hasPassed("Task_Discount")
        .isEnded()
        .variables()
        .containsEntry("paymentCharged", true)
        .containsEntry("auditStarted", true);
  }
}
