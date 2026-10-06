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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-order.bpmn", "order-review.form"})
class OrderAutoMockTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;
  private JobWorker stockWorker;

  @BeforeEach
  void openStockWorker() {
    stockWorker = OrderJobHandlers.openStockWorker(client);
  }

  @AfterEach
  void closeStockWorker() {
    stockWorker.close();
  }

  @Test
  void autoMocksDelegatesAndTracksCoverage() {
    JobWorkerMock audit = processTestContext.mockJobWorker("order-audit").thenComplete();
    JobWorkerMock charge = processTestContext.mockJobWorker("charge-payment").thenComplete();
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(Map.of("sku", "available"))
            .send()
            .join();

    assertThat(instance).hasActiveElements("Task_Approve").hasVariable("stockChecked", true);
    org.assertj.core.api.Assertions.assertThat(audit.getInvocations()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(charge.getInvocations()).isZero();
    org.assertj.core.api.Assertions.assertThat(instance.getBpmnProcessId()).isEqualTo("order");
  }
}
