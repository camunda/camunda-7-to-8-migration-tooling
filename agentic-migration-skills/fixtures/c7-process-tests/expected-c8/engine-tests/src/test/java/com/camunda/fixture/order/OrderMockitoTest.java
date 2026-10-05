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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(
    resources = {
      "converted-c8-order.bpmn",
      "converted-c8-discount.dmn",
      "order-review.form"
    })
class OrderMockitoTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;
  private List<JobWorker> workers;

  @BeforeEach
  void openSupportingWorkers() {
    workers = new ArrayList<>(OrderJobHandlers.openWithoutCharge(client));
  }

  @AfterEach
  void closeWorkers() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void registersWholeDelegateAndExecutionListenerMocks() {
    processTestContext.mockJobWorker("order-audit").thenComplete();
    JobWorkerMock charge =
        processTestContext
            .mockJobWorker("charge-payment")
            .thenComplete(Map.of("paymentCharged", true, "paymentReference", "payment-mock"));
    processTestContext.mockChildProcess("shipping", Map.of("shipped", true));

    ProcessInstanceEvent instance = startOrderAtApproval();
    completeAndCorrelate(instance);

    assertThat(instance).isCompleted().hasVariable("paymentCharged", true);
    org.assertj.core.api.Assertions.assertThat(charge.getInvocations()).isEqualTo(1);
  }

  @Test
  void registersDelegateOutputAndVerifiesItsInvocation() {
    processTestContext.mockJobWorker("order-audit").thenComplete();
    JobWorkerMock charge =
        processTestContext
            .mockJobWorker("charge-payment")
            .thenComplete(Map.of("paymentCharged", true));
    processTestContext.mockChildProcess("shipping", Map.of("shipped", true));

    ProcessInstanceEvent instance = startOrderAtApproval();
    completeAndCorrelate(instance);

    assertThat(instance).isCompleted();
    org.assertj.core.api.Assertions.assertThat(charge.getInvocations()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(charge.getActivatedJobs()).hasSize(1);
  }

  @Test
  void routesDelegateBpmnError() {
    processTestContext.mockJobWorker("order-audit").thenComplete();
    JobWorkerMock charge =
        processTestContext.mockJobWorker("charge-payment").thenThrowBpmnError("PAYMENT_FAILED");

    ProcessInstanceEvent instance = startOrderAtApproval();
    processTestContext.completeUserTask("Task_Approve", Map.of("approved", true));

    assertThat(instance).isCompleted().hasCompletedElements("End_PaymentFailed");
    org.assertj.core.api.Assertions.assertThat(charge.getInvocations()).isEqualTo(1);
  }

  @Test
  void reportsIncidentWhenTheWorkerFails() {
    processTestContext.mockJobWorker("order-audit").thenComplete();
    JobWorkerMock charge =
        processTestContext
            .mockJobWorker("charge-payment")
            .withHandler(
                (jobClient, job) ->
                    jobClient
                        .newFailCommand(job)
                        .retries(0)
                        .errorMessage("Payment failed")
                        .send()
                        .join());

    ProcessInstanceEvent instance = startOrderAtApproval();
    processTestContext.completeUserTask("Task_Approve", Map.of("approved", true));

    assertThat(instance).hasActiveIncidents();
    org.assertj.core.api.Assertions.assertThat(charge.getInvocations()).isEqualTo(1);
  }

  private ProcessInstanceEvent startOrderAtApproval() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(Map.of("orderId", "mock-order", "sku", "available", "customerType", "gold"))
            .send()
            .join();
    assertThat(instance).hasActiveElements("Task_Approve");
    return instance;
  }

  private void completeAndCorrelate(ProcessInstanceEvent instance) {
    processTestContext.completeUserTask("Task_Approve", Map.of("approved", true));
    assertThat(instance).isWaitingForMessage("PaymentConfirmed");
    client.newCorrelateMessageCommand()
        .messageName("PaymentConfirmed")
        .correlationKey("mock-order")
        .variables(Map.of("paymentConfirmed", true))
        .send()
        .join();
  }
}
