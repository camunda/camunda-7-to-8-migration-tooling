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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.TestDeployment;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-review-edge-cases.bpmn")
class ScenarioMappingEdgeCasesTest {

  private CamundaProcessTestContext processTestContext;

  @Spy private Collaborator collaborator = new Collaborator();
  @Captor private ArgumentCaptor<String> orderIdCaptor;
  @InjectMocks private CollaboratorService collaboratorService;

  private AutoCloseable mocks;

  @BeforeEach
  void openMocks() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void closeMocks() throws Exception {
    mocks.close();
  }

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

  @Test
  void shouldKeepUnrelatedMockitoAnnotationsInitialized() {
    when(collaborator.lookup("order-42")).thenReturn("ready");

    assertEquals("ready", collaboratorService.lookup("order-42"));
    verify(collaborator).lookup(orderIdCaptor.capture());
    assertEquals("order-42", orderIdCaptor.getValue());
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

  static class Collaborator {
    String lookup(String orderId) {
      return "not-stubbed";
    }
  }

  static class CollaboratorService {
    private Collaborator collaborator;

    String lookup(String orderId) {
      return collaborator.lookup(orderId);
    }
  }
}
