/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example.processmock;

import static org.camunda.community.mockito.DelegateExpressions.autoMock;
import static org.camunda.community.mockito.DelegateExpressions.registerExecutionListenerMock;
import static org.camunda.community.mockito.DelegateExpressions.registerJavaDelegateMock;
import static org.camunda.community.mockito.DelegateExpressions.verifyExecutionListenerMock;
import static org.camunda.community.mockito.DelegateExpressions.verifyJavaDelegateMock;
import static org.camunda.community.mockito.ProcessExpressions.registerCallActivityMock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

public class InvoiceProcessTest {

  @Rule public ProcessEngineRule rule = new ProcessEngineRule();

  @After
  public void resetMocks() {
    Mocks.reset();
  }

  @Test
  @Deployment(resources = "invoice.bpmn")
  public void registersCollaboratorAndWholeDelegateMocks() {
    InvoiceService invoiceService = registerInvoiceService();
    NotifyDelegate notifyDelegate = mock(NotifyDelegate.class);
    AtomicReference<String> invoiceIdSeenByDelegate = new AtomicReference<>();
    doAnswer(
            invocation -> {
              DelegateExecution execution = invocation.getArgument(0, DelegateExecution.class);
              invoiceIdSeenByDelegate.set((String) execution.getVariable("invoiceId"));
              execution.setVariable("notified", true);
              return null;
            })
        .when(notifyDelegate)
        .execute(any(DelegateExecution.class));
    Mocks.register("notifyDelegate", notifyDelegate);
    registerExecutionListenerMock("notifyStart");
    registerArchiveMock();

    ProcessInstance instance = startInvoice();

    assertInvoiceFinished(instance);
    assertHistoricVariable(instance, "notified", true);
    assertHistoricVariable(instance, "archived", true);
    verify(invoiceService).isValid("I-1");
    verify(notifyDelegate).execute(any(DelegateExecution.class));
    assertEquals("I-1", invoiceIdSeenByDelegate.get());
    verifyExecutionListenerMock("notifyStart").executed();
  }

  @Test
  @Deployment(resources = "invoice.bpmn")
  public void registersDelegateOutputAndVerifiesItsInvocation() {
    registerInvoiceService();
    registerExecutionListenerMock("notifyStart");
    registerJavaDelegateMock("notifyDelegate")
        .onExecutionSetVariables(Variables.putValue("notified", true));
    registerArchiveMock();

    ProcessInstance instance = startInvoice();

    assertInvoiceFinished(instance);
    assertHistoricVariable(instance, "notified", true);
    assertHistoricVariable(instance, "archived", true);
    verifyJavaDelegateMock("notifyDelegate").executed(times(1));
  }

  @Test
  @Deployment(resources = "invoice.bpmn")
  public void routesADelegateBpmnError() {
    registerInvoiceService();
    registerExecutionListenerMock("notifyStart");
    registerJavaDelegateMock("notifyDelegate")
        .onExecutionThrowBpmnError("INVOICE_REJECTED", "Invoice rejected");

    ProcessInstance instance = startInvoice();

    assertNotNull(instance);
    assertHistoricActivityReached(instance, "End_InvoiceRejected");
  }

  @Test
  @Deployment(resources = "invoice.bpmn")
  public void throwsWhenTheSynchronousDelegateFails() {
    registerInvoiceService();
    registerExecutionListenerMock("notifyStart");
    registerJavaDelegateMock("notifyDelegate")
        .onExecutionThrowException(new IllegalStateException("Notification failed"));

    assertThrows(RuntimeException.class, () -> startInvoice());
  }

  @Test
  @Deployment(resources = "auto-mock-invoice.bpmn")
  public void autoMocksDelegatesAndExecutionListeners() {
    autoMock("auto-mock-invoice.bpmn");

    ProcessInstance instance =
        rule.getRuntimeService().startProcessInstanceByKey("auto-mock-invoice");

    assertInvoiceFinished(instance);
    verifyJavaDelegateMock("autoValidateDelegate").executed();
    verifyJavaDelegateMock("autoNotifyDelegate").executed();
    verifyExecutionListenerMock("autoStartListener").executed();
  }

  private InvoiceService registerInvoiceService() {
    InvoiceService invoiceService = mock(InvoiceService.class);
    when(invoiceService.isValid("I-1")).thenReturn(true);
    Mocks.register("invoiceService", invoiceService);
    return invoiceService;
  }

  private void registerArchiveMock() {
    registerCallActivityMock("archive-invoice")
        .onExecutionSetVariables(Variables.putValue("archived", true))
        .deploy(rule);
  }

  private ProcessInstance startInvoice() {
    RuntimeService runtimeService = rule.getRuntimeService();
    return runtimeService.startProcessInstanceByKey(
        "invoice", Variables.putValue("invoiceId", "I-1"));
  }

  private void assertInvoiceFinished(ProcessInstance instance) {
    assertNotNull(instance);
    assertNotNull(
        rule.getHistoryService()
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .finished()
            .singleResult());
  }

  private void assertHistoricVariable(ProcessInstance instance, String name, Object expectedValue) {
    HistoricVariableInstance variable =
        rule.getHistoryService()
            .createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName(name)
            .singleResult();
    assertNotNull(variable);
    assertEquals(expectedValue, variable.getValue());
  }

  private void assertHistoricActivityReached(ProcessInstance instance, String activityId) {
    assertEquals(
        1L,
        rule.getHistoryService()
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instance.getId())
            .activityId(activityId)
            .count());
  }
}
