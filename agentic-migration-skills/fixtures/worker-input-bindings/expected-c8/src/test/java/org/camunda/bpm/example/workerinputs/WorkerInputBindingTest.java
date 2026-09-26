/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = WorkerInputBindingApplication.class)
@CamundaSpringProcessTest
@TestDeployment(resources = "processes/worker-input-bindings.bpmn")
class WorkerInputBindingTest {

  @Autowired private CamundaClient camundaClient;
  @Autowired private WorkerInputRecorder recorder;

  @Test
  void bindsSingleVariablesAndTheCompleteVariableMapWithoutParameterMetadata() {
    recorder.clear();

    Map<String, Object> variables =
        Map.of(
            "defaultScore", 720,
            "shouldFail", false,
            "content", "Loan approved",
            "customerId", "customer-42",
            "projectName", "migration");
    assertThat(variables).doesNotContainKey("variables");

    ProcessInstanceEvent processInstance = start("worker-input-bindings", variables);

    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasNoActiveIncidents();
    assertThat(recorder.snapshot())
        .containsEntry("defaultScore", 720)
        .containsEntry("shouldFail", false)
        .containsEntry("content", "Loan approved")
        .containsEntry("customerId", "customer-42")
        .containsEntry("projectName", "migration");
  }

  @Test
  void missingRequiredInputCannotCompleteWithANullValue() {
    recorder.clear();

    ProcessInstanceEvent processInstance = start("missing-required-score", Map.of());

    CamundaAssert.assertThat(processInstance).hasActiveIncidents();
    assertThat(recorder.snapshot()).doesNotContainKey("defaultScore");
  }

  @Test
  void preservesOptionalTwitterContentAsNull() {
    recorder.clear();

    ProcessInstanceEvent processInstance =
        start(
            "worker-input-bindings",
            Map.of(
                "defaultScore", 720,
                "shouldFail", false,
                "customerId", "customer-42",
                "projectName", "migration"));

    CamundaAssert.assertThat(processInstance).isCompleted().hasNoActiveIncidents();
    assertThat(recorder.snapshot()).containsEntry("content", null);
  }

  @Test
  void preservesSynchronousFailureBehavior() {
    recorder.clear();

    ProcessInstanceEvent processInstance =
        start(
            "worker-input-bindings",
            Map.of(
                "defaultScore", 720,
                "shouldFail", true,
                "content", "Loan approved",
                "customerId", "customer-42",
                "projectName", "migration"));

    CamundaAssert.assertThat(processInstance).hasActiveIncidents();
    assertThat(recorder.snapshot()).containsEntry("shouldFail", true).doesNotContainKey("content");
  }

  private ProcessInstanceEvent start(String processId, Map<String, Object> variables) {
    return camundaClient.newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join();
  }
}
