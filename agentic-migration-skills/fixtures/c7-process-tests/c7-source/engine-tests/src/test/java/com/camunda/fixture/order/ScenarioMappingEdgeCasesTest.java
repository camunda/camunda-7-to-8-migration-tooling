/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.scenario.ProcessScenario;
import org.camunda.bpm.scenario.Scenario;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@Deployment(resources = "review-edge-cases.bpmn")
public class ScenarioMappingEdgeCasesTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Mock private ProcessScenario process;

  @Mock private Collaborator collaborator;

  @Spy private AuditLog auditLog = new AuditLog();

  @Captor private ArgumentCaptor<String> orderIdCaptor;

  @InjectMocks private CollaboratorService collaboratorService;

  private AutoCloseable mocks;

  @Before
  public void openMocks() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void closeMocks() throws Exception {
    mocks.close();
  }

  @Test
  public void shouldStartMessageProcess() {
    Scenario.run(process).startByMessage("FulfillmentRequested", Map.of()).execute();

    verify(process).hasFinished("MessageStarted");
  }

  @Test
  public void shouldCountCompletedVisitsSeparately() {
    when(collaborator.lookup("order-42")).thenReturn("ready");
    when(process.waitsAtServiceTask("MixedWork"))
        .thenReturn(
            task -> task.complete(Map.of("visitCount", 1)),
            task -> task.complete(Map.of("visitCount", 2)));

    Scenario.run(process).startByKey("MixedFinishReview", Map.of()).execute();

    assertEquals("ready", collaboratorService.lookup("order-42"));
    verify(collaborator).lookup(orderIdCaptor.capture());
    assertEquals("order-42", orderIdCaptor.getValue());
    verify(auditLog).record("order-42");
    verify(process, times(2)).hasCompleted("MixedWork");
    verify(process, times(2)).hasFinished("MixedWork");
  }

  @Test
  public void shouldCountMixedFinishedVisitsByOutcome() {
    when(process.waitsAtServiceTask("MixedWork"))
        .thenReturn(
            task -> task.complete(Map.of("visitCount", 1)),
            task -> task.handleBpmnError("MIXED_WORK_ERROR", Map.of()));

    Scenario.run(process).startByKey("MixedFinishReview", Map.of()).execute();

    verify(process).hasCompleted("MixedWork");
    verify(process).hasCanceled("MixedWork");
    verify(process, times(2)).hasFinished("MixedWork");
  }

  private static class Collaborator {
    String lookup(String orderId) {
      return "not-stubbed";
    }
  }

  private static class AuditLog {
    void record(String orderId) {}
  }

  private static class CollaboratorService {
    private Collaborator collaborator;
    private AuditLog auditLog;

    String lookup(String orderId) {
      auditLog.record(orderId);
      return collaborator.lookup(orderId);
    }
  }
}
