/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(
    resources = {
      "converted-c8-order.bpmn",
      "converted-c8-shipping.bpmn",
      "converted-c8-discount.dmn",
      "order-review.form"
    })
class OrderProcessTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;
  private List<JobWorker> workers;

  @BeforeEach
  void openWorkers() {
    OrderJobHandlers.NotificationService notificationService =
        mock(OrderJobHandlers.NotificationService.class);
    workers = OrderJobHandlers.open(client, notificationService);
  }

  @AfterEach
  void closeWorkers() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void approvesAndShipsOrder() {
    ProcessInstanceEvent instance = startOrder("order-1");

    assertThat(instance).hasActiveElements("Task_Approve");
    assertThatUserTask(byElementId("Task_Approve")).hasAssignee("demo");
    processTestContext.completeUserTask("Task_Approve", Map.of("approved", true));
    assertThat(instance).isWaitingForMessage("PaymentConfirmed");

    client.newCorrelateMessageCommand()
        .messageName("PaymentConfirmed")
        .correlationKey("order-1")
        .variables(Map.of("paymentConfirmed", true))
        .send()
        .join();

    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("CallActivity_Ship", "Task_Discount")
        .hasVariable("paymentCharged", true)
        .hasVariable("shipped", true)
        .hasVariable("auditStarted", true);
  }

  @Test
  void failsWhenStockIsMissing() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(
                Map.of(
                    "orderId", "order-missing",
                    "sku", "missing",
                    "customerType", "gold"))
            .send()
            .join();

    assertThat(instance).hasActiveIncidents();
    var incidents =
        client.newIncidentSearchRequest()
            .filter(filter -> filter.processInstanceKey(instance.getProcessInstanceKey()))
            .send()
            .join()
            .items();
    Assertions.assertThat(incidents)
        .singleElement()
        .satisfies(
            incident -> {
              Assertions.assertThat(incident.getElementId()).isEqualTo("Task_CheckStock");
              Assertions.assertThat(incident.getErrorMessage()).contains("Stock is unavailable");
            });
  }

  private ProcessInstanceEvent startOrder(String orderId) {
    return client.newCreateInstanceCommand()
        .bpmnProcessId("order")
        .latestVersion()
        .variables(Map.of("orderId", orderId, "sku", "available", "customerType", "gold"))
        .send()
        .join();
  }
}
