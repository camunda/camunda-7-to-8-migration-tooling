/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example.processmock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import java.util.Map;
import org.camunda.example.processmock.service.InvoiceService;
import org.camunda.example.processmock.testapp.TestProcessApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = TestProcessApplication.class)
@CamundaSpringProcessTest
class InvoiceProcessTest {

  @Autowired private CamundaClient camundaClient;
  @Autowired private CamundaProcessTestContext processTestContext;
  @MockitoBean private InvoiceService invoiceService;

  @Test
  void keepsCollaboratorAndWholeDelegateAtTheirOriginalBoundaries() {
    when(invoiceService.isValid("I-1")).thenReturn(true);
    JobWorkerMock notify =
        processTestContext.mockJobWorker("notify-invoice").thenComplete(Map.of("notified", true));
    JobWorkerMock notifyStart = processTestContext.mockJobWorker("notify-start").thenComplete();
    processTestContext.mockChildProcess("archive-invoice", Map.of("archived", true));

    ProcessInstanceEvent instance = start("invoice", Map.of("invoiceId", "I-1"));

    CamundaAssert.assertThat(instance)
        .isCompleted()
        .hasVariable("notified", true)
        .hasVariable("archived", true);
    verify(invoiceService).isValid("I-1");
    assertThat(notify.getInvocations()).isEqualTo(1);
    assertThat(notifyStart.getInvocations()).isEqualTo(1);
    assertThat(notify.getActivatedJobs()).hasSize(1);
    assertThat(notify.getActivatedJobs().get(0).getVariablesAsMap())
        .containsEntry("invoiceId", "I-1");
  }

  @Test
  void mapsDelegateVariablesAndInvocationVerification() {
    JobWorkerMock notify =
        processTestContext.mockJobWorker("notify-invoice").thenComplete(Map.of("notified", true));
    JobWorkerMock notifyStart = processTestContext.mockJobWorker("notify-start").thenComplete();
    processTestContext.mockChildProcess("archive-invoice", Map.of("archived", true));

    ProcessInstanceEvent instance = start("invoice", Map.of("invoiceId", "I-1"));

    CamundaAssert.assertThat(instance)
        .isCompleted()
        .hasVariable("notified", true)
        .hasVariable("archived", true);
    assertThat(notify.getInvocations()).isEqualTo(1);
    assertThat(notifyStart.getInvocations()).isEqualTo(1);
    assertThat(notify.getActivatedJobs()).hasSize(1);
    assertThat(notify.getActivatedJobs().get(0).getVariablesAsMap())
        .containsEntry("invoiceId", "I-1");
  }

  @Test
  void routesTheMockedBpmnError() {
    processTestContext.mockJobWorker("notify-start").thenComplete();
    JobWorkerMock notify =
        processTestContext
            .mockJobWorker("notify-invoice")
            .thenThrowBpmnError("INVOICE_REJECTED", "Invoice rejected", Map.of());

    ProcessInstanceEvent instance = start("invoice", Map.of("invoiceId", "I-1"));

    CamundaAssert.assertThat(instance)
        .isCompleted()
        .hasCompletedElements("End_InvoiceRejected");
    assertThat(notify.getInvocations()).isEqualTo(1);
  }

  @Test
  void reportsAnIncidentForAWorkerException() {
    processTestContext.mockJobWorker("notify-start").thenComplete();
    JobWorkerMock notify =
        processTestContext
            .mockJobWorker("notify-invoice")
            .withHandler(
                (jobClient, job) ->
                    jobClient
                        .newFailCommand(job)
                        .retries(0)
                        .errorMessage("Notification failed")
                        .send()
                        .join());

    ProcessInstanceEvent instance = start("invoice", Map.of("invoiceId", "I-1"));

    CamundaAssert.assertThat(instance).hasActiveIncidents();
    assertThat(notify.getInvocations()).isEqualTo(1);
  }

  @Test
  void mocksEveryAutoMockedJobTypeIncludingTheExecutionListener() {
    JobWorkerMock autoStart =
        processTestContext.mockJobWorker("auto-start-listener").thenComplete();
    JobWorkerMock autoValidate = processTestContext.mockJobWorker("auto-validate").thenComplete();
    JobWorkerMock autoNotify = processTestContext.mockJobWorker("auto-notify").thenComplete();

    ProcessInstanceEvent instance = start("auto-mock-invoice", Map.of());

    CamundaAssert.assertThat(instance).isCompleted();
    assertThat(autoStart.getInvocations()).isEqualTo(1);
    assertThat(autoValidate.getInvocations()).isEqualTo(1);
    assertThat(autoNotify.getInvocations()).isEqualTo(1);
  }

  private ProcessInstanceEvent start(String processId, Map<String, Object> variables) {
    return camundaClient.newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join();
  }
}
