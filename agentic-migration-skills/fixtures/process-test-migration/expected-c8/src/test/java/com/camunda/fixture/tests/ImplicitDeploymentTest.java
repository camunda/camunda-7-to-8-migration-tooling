/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.tests;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import org.junit.jupiter.api.Test;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-implicit-process.bpmn")
class ImplicitDeploymentTest {

  private CamundaClient client;

  @Test
  void testStartsImplicitlyDeployedProcess() {
    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("implicit-process")
            .latestVersion()
            .send()
            .join();

    assertThat(processInstance).isCompleted();
  }
}
