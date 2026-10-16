/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.camunda.bpm.example.springprocess.service.OrderEffects;
import org.camunda.bpm.example.springprocesstest.TestProcessApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestProcessApplication.class)
@CamundaSpringProcessTest
class RollbackProcessTest {

  @Autowired private CamundaClient camundaClient;
  @Autowired private OrderEffects orderEffects;

  @BeforeEach
  void clearOrderEffects() {
    orderEffects.clear();
  }

  @Test
  void observesCommittedWritesAfterWorkerFailure() {
    ProcessInstanceEvent instance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("rollback-order")
            .latestVersion()
            .send()
            .join();

    CamundaAssert.assertThat(instance)
        .hasCompletedElements("Task_Reserve", "Task_ChargeRollback")
        .hasActiveIncidents();
    assertThat(orderEffects.count("inventory")).isEqualTo(1);
    assertThat(orderEffects.count("payment")).isEqualTo(1);
  }
}
