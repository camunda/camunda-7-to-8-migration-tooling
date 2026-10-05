/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed to Camunda Services GmbH under the Camunda License 1.0.
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Deployment(resources = "review-edge-cases.bpmn")
public class ScenarioMappingEdgeCasesTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Mock private ProcessScenario process;
  @Mock private Collaborator collaborator;

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
    when(process.waitsAtServiceTask("MixedWork"))
        .thenReturn(
            task -> task.complete(Map.of("visitCount", 1)),
            task -> task.complete(Map.of("visitCount", 2)));

    Scenario.run(process).startByKey("MixedFinishReview", Map.of()).execute();

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

  @Test
  public void shouldKeepUnrelatedMockitoMockInitialized() {
    when(collaborator.lookup("order-42")).thenReturn("ready");

    assertEquals("ready", collaborator.lookup("order-42"));
  }

  interface Collaborator {
    String lookup(String orderId);
  }
}
