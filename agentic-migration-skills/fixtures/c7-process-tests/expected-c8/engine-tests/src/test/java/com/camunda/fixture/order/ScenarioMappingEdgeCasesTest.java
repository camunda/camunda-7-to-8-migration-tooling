/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed to Camunda Services GmbH under the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-review-edge-cases.bpmn")
class ScenarioMappingEdgeCasesTest {

  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldStartMessageProcess() {
    CorrelateMessageResponse correlationResponse =
        processTestContext
            .createClient()
            .newCorrelateMessageCommand()
            .messageName("FulfillmentRequested")
            .withoutCorrelationKey()
            .send()
            .join();

    assertThatProcessInstance(byKey(correlationResponse.getProcessInstanceKey()))
        .isCompleted()
        .hasCompletedElements("MessageStarted");
  }

  @Test
  void shouldCountCompletedVisitsSeparately() {
    ProcessInstanceEvent processInstance = createMixedFinishInstance();
    processTestContext.completeJob("mixed-work", Map.of("visitCount", 1));
    processTestContext.completeJob("mixed-work", Map.of("visitCount", 2));

    assertThat(processInstance).isCompleted().hasCompletedElement("MixedWork", 2);
  }

  @Test
  void shouldCountMixedFinishedVisitsByOutcome() {
    ProcessInstanceEvent processInstance = createMixedFinishInstance();
    processTestContext.completeJob("mixed-work", Map.of("visitCount", 1));
    assertThat(processInstance).hasActiveElements("MixedWork");
    processTestContext.throwBpmnErrorFromJob("mixed-work", "MIXED_WORK_ERROR");

    assertThat(processInstance)
        .isCompleted()
        .hasCompletedElement("MixedWork", 1)
        .hasTerminatedElement("MixedWork", 1);
  }

  private ProcessInstanceEvent createMixedFinishInstance() {
    CamundaClient client = processTestContext.createClient();
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId("MixedFinishReview")
        .latestVersion()
        .send()
        .join();
  }
}
