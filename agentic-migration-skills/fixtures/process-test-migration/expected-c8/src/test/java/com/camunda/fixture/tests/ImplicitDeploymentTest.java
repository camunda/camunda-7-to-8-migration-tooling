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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

@CamundaProcessTest
@TestDeployment(
    resources = "com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn")
class ImplicitDeploymentTest {

  private CamundaClient client;

  private String processDefinitionKey;

  @BeforeEach
  void setUp() {
    processDefinitionKey = "implicit-process";
  }

  @AfterEach
  void tearDown() {
    processDefinitionKey = null;
  }

  @Test
  void testStartsImplicitlyDeployedProcess() {
    ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processDefinitionKey)
            .latestVersion()
            .send()
            .join();

    assertThat(processInstance).isCompleted();
  }
}
