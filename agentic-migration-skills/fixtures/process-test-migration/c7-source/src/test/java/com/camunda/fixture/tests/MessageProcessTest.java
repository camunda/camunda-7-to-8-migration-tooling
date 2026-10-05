/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.tests;

import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;

@ExtendWith(ProcessEngineExtension.class)
@Deployment(resources = "com/camunda/fixture/tests/process-test-cases.bpmn")
class MessageProcessTest {

  @Test
  void correlatesMessage() {
    ProcessInstance processInstance =
        runtimeService().startProcessInstanceByKey("message-process", "legacy-business-key");
    assertThat(processInstance).isWaitingFor("ContinueMessage");

    runtimeService()
        .correlateMessage(
            "ContinueMessage",
            "legacy-business-key",
            Variables.createVariables().putValue("messageReceived", true));

    assertThat(processInstance)
        .hasPassed("Message_Catch")
        .isEnded()
        .variables()
        .containsEntry("messageReceived", true);
  }
}
