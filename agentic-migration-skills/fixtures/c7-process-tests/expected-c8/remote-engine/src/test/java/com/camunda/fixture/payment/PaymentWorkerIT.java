/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.payment;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@CamundaSpringProcessTest
@SpringBootTest(classes = PaymentApplication.class)
@TestDeployment(resources = "converted-c8-payment.bpmn")
class PaymentWorkerIT {

  @Autowired private CamundaClient client;

  @Test
  void chargesPayment() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("payment")
            .latestVersion()
            .variables(Map.of("amount", 42))
            .send()
            .join();

    assertThat(instance).isCompleted().hasVariable("charged", true).hasVariable("chargedAmount", 42);
  }
}
