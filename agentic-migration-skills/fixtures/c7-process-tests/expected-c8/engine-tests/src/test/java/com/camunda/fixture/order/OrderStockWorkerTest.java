/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.List;
import java.util.Map;
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
class OrderStockWorkerTest {

  private CamundaClient client;
  private List<JobWorker> workers;

  @BeforeEach
  void openWorkers() {
    workers = OrderJobHandlers.open(client);
  }

  @AfterEach
  void closeWorkers() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void completesAvailableStockJobWithStockFlag() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(
                Map.of(
                    "orderId", "stock-check",
                    "sku", "available",
                    "customerType", "gold"))
            .send()
            .join();

    assertThat(instance).hasActiveElements("Task_Approve").hasVariable("stockChecked", true);
  }
}
