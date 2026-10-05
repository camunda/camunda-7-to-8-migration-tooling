/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.camunda.community.mockito.DelegateExpressions.registerExecutionListenerMock;
import static org.camunda.community.mockito.DelegateExpressions.registerJavaDelegateMock;
import static org.camunda.community.mockito.DelegateExpressions.verifyExecutionListenerMock;
import static org.camunda.community.mockito.DelegateExpressions.verifyJavaDelegateMock;
import static org.camunda.community.mockito.ProcessExpressions.registerCallActivityMock;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class OrderMockitoTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Before
  public void registerNotificationService() {
    Mocks.register("notificationService", mock(NotificationService.class));
  }

  @After
  public void resetMocks() {
    for (org.camunda.bpm.engine.repository.Deployment deployment :
        processEngineRule.getRepositoryService().createDeploymentQuery().list()) {
      boolean containsShipping =
          processEngineRule
                  .getRepositoryService()
                  .createProcessDefinitionQuery()
                  .deploymentId(deployment.getId())
                  .processDefinitionKey("shipping")
                  .count()
              > 0;
      if (containsShipping) {
        processEngineRule
            .getRepositoryService()
            .deleteDeployment(deployment.getId(), true);
      }
    }
    Mocks.reset();
  }

  @Test
  @Deployment(resources = {"order.bpmn", "discount.dmn"})
  public void registersWholeDelegateAndExecutionListenerMocks() throws Exception {
    JavaDelegate chargePaymentDelegate = mock(JavaDelegate.class);
    doAnswer(
            invocation -> {
              DelegateExecution execution = invocation.getArgument(0, DelegateExecution.class);
              execution.setVariable("paymentCharged", true);
              return null;
            })
        .when(chargePaymentDelegate)
        .execute(any(DelegateExecution.class));
    Mocks.register("chargePaymentDelegate", chargePaymentDelegate);
    registerExecutionListenerMock("orderAuditListener");
    registerCallActivityMock("shipping")
        .onExecutionSetVariables(Variables.putValue("shipped", true))
        .deploy(processEngineRule);

    ProcessInstance instance = startAndReachPayment();
    processEngineRule.getRuntimeService().correlateMessage("PaymentConfirmed");

    verify(chargePaymentDelegate).execute(any(DelegateExecution.class));
    verifyExecutionListenerMock("orderAuditListener").executed();
    assertNotNull(
        processEngineRule
            .getHistoryService()
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .finished()
            .singleResult());
    assertNotNull(instance);
  }

  @Test
  @Deployment(resources = {"order.bpmn", "discount.dmn"})
  public void registersDelegateOutputAndVerifiesItsInvocation() throws Exception {
    registerExecutionListenerMock("orderAuditListener");
    registerJavaDelegateMock("chargePaymentDelegate")
        .onExecutionSetVariables(Variables.putValue("paymentCharged", true));
    registerCallActivityMock("shipping")
        .onExecutionSetVariables(Variables.putValue("shipped", true))
        .deploy(processEngineRule);

    ProcessInstance instance = startAndReachPayment();
    processEngineRule.getRuntimeService().correlateMessage("PaymentConfirmed");

    verifyJavaDelegateMock("chargePaymentDelegate").executed(times(1));
    assertNotNull(instance);
  }

  @Test
  @Deployment(resources = {"order.bpmn", "discount.dmn"})
  public void routesDelegateBpmnError() {
    registerExecutionListenerMock("orderAuditListener");
    registerJavaDelegateMock("chargePaymentDelegate")
        .onExecutionThrowBpmnError("PAYMENT_FAILED", "Payment failed");
    ProcessInstance instance =
        processEngineRule.getRuntimeService().startProcessInstanceByKey("order");
    processEngineRule
        .getTaskService()
        .complete(
            processEngineRule
                .getTaskService()
                .createTaskQuery()
                .taskDefinitionKey("Task_Approve")
                .singleResult()
                .getId(),
            Map.of("approved", true));
    processEngineRule
        .getManagementService()
        .executeJob(
            processEngineRule
                .getManagementService()
                .createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult()
                .getId());

    assertNotNull(
        processEngineRule
            .getHistoryService()
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instance.getId())
            .activityId("End_PaymentFailed")
            .singleResult());
  }

  @Test
  @Deployment(resources = {"order.bpmn", "discount.dmn"})
  public void throwsWhenTheSynchronousDelegateFails() {
    registerExecutionListenerMock("orderAuditListener");
    registerJavaDelegateMock("chargePaymentDelegate")
        .onExecutionThrowException(new IllegalStateException("Payment failed"));

    ProcessInstance instance =
        processEngineRule.getRuntimeService().startProcessInstanceByKey("order");
    processEngineRule
        .getTaskService()
        .complete(
            processEngineRule
                .getTaskService()
                .createTaskQuery()
                .taskDefinitionKey("Task_Approve")
                .singleResult()
                .getId(),
            Map.of("approved", true));

    assertThrows(
        RuntimeException.class,
        () ->
            processEngineRule
                .getManagementService()
                .executeJob(
                    processEngineRule
                        .getManagementService()
                        .createJobQuery()
                        .processInstanceId(instance.getId())
                        .singleResult()
                        .getId()));
  }

  private ProcessInstance startAndReachPayment() {
    ProcessInstance instance =
        processEngineRule
            .getRuntimeService()
            .startProcessInstanceByKey("order", Map.of("sku", "available"));
    processEngineRule
        .getTaskService()
        .complete(
            processEngineRule
                .getTaskService()
                .createTaskQuery()
                .taskDefinitionKey("Task_Approve")
                .singleResult()
                .getId(),
            Map.of("approved", true));
    processEngineRule
        .getManagementService()
        .executeJob(
            processEngineRule
                .getManagementService()
                .createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult()
                .getId());
    return instance;
  }
}
