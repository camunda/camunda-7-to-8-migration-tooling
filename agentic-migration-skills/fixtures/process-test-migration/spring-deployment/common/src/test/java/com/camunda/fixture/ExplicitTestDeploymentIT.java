/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = Application.class,
    properties = "camunda.client.deployment.enabled=false")
@CamundaSpringProcessTest
@TestDeployment(
    resources = {
      "converted-c8-process.bpmn",
      "converted-c8-decision.dmn",
      "converted-c8-request.form"
    })
class ExplicitTestDeploymentIT {

  @Autowired private CamundaClient client;

  @Test
  void deploysAndExecutesTheActualProcessAndDecision() {
    ProcessInstanceEvent process =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fixture-process")
            .latestVersion()
            .send()
            .join();
    CamundaAssert.assertThat(process).isCompleted();

    EvaluateDecisionResponse decision =
        client.newEvaluateDecisionCommand().decisionId("fixture-decision").send().join();
    CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(decision))
        .isEvaluated()
        .hasOutput(true);
  }
}
