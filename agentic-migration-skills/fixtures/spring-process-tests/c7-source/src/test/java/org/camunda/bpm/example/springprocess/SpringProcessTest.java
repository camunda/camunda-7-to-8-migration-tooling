/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests;
import org.camunda.bpm.example.springprocess.delegate.ShipOrderDelegate;
import org.camunda.bpm.example.springprocess.service.PaymentService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringProcessApplication.class)
@AutoConfigureMockMvc
public class SpringProcessTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RuntimeService runtimeService;
  @Autowired private ProcessEngine processEngine;
  @MockBean private PaymentService paymentService;
  @MockBean private ShipOrderDelegate shipOrderDelegate;

  @Before
  public void initializeBpmnAssertions() {
    BpmnAwareTests.init(processEngine);
  }

  @Test
  public void createsAnOrderThroughTheApplicationEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100}"))
        .andExpect(status().isAccepted());

    ProcessInstance processInstance =
        runtimeService
            .createProcessInstanceQuery()
            .processDefinitionKey("order")
            .singleResult();

    assertThat(processInstance)
        .hasPassed("Task_Charge")
        .hasPassed("Task_Ship")
        .isWaitingAt("Task_Review");
    verify(paymentService).charge(100);
    verify(shipOrderDelegate).execute(any(DelegateExecution.class));
  }

  @Test
  public void startsAnOrderFromTheStartupHook() {
    ProcessInstance processInstance =
        runtimeService
            .createProcessInstanceQuery()
            .processDefinitionKey("startup-order")
            .singleResult();

    assertThat(processInstance).isWaitingAt("Task_StartupWait");
  }
}
