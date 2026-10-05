/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.test;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import org.camunda.bpm.example.springprocess.service.PaymentService;
import org.camunda.bpm.example.springprocess.service.StartupProcessStarter;
import org.camunda.bpm.example.springprocess.testapp.TestProcessApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestProcessApplication.class)
@AutoConfigureMockMvc
@CamundaSpringProcessTest
class SpringProcessTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CamundaClient camundaClient;
  @Autowired private CamundaProcessTestContext processTestContext;
  @Autowired private StartupProcessStarter startupProcessStarter;
  @MockitoBean private PaymentService paymentService;

  @BeforeEach
  void repeatStartupHookForEachTest() {
    processTestContext.mockJobWorker("ship-order").thenComplete();
    startupProcessStarter.startStartupProcess(camundaClient);
  }

  @Test
  void createsAnOrderThroughTheApplicationEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100}"))
        .andExpect(status().isAccepted());

    CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byProcessId("order"))
        .hasCompletedElements("Task_Charge", "Task_Ship")
        .hasActiveElements("Task_Review");
    verify(paymentService, timeout(10_000)).charge(100);
  }

  @Test
  void startsAnOrderFromTheStartupHook() {
    CamundaAssert.assertThatProcessInstance(
            ProcessInstanceSelectors.byProcessId("startup-order"))
        .hasActiveElements("Task_StartupWait");
  }
}
