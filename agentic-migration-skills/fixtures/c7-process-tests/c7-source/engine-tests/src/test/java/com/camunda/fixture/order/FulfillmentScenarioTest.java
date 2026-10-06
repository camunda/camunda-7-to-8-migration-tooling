/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

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

@Deployment(resources = "fulfillment.bpmn")
public class FulfillmentScenarioTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Mock private ProcessScenario process;

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
  public void shouldCompleteWorkAfterTwoDailyReminders() {
    when(process.waitsAtUserTask("CompleteWork"))
        .thenReturn(task -> task.defer("P2DT12H", task::complete));
    when(process.waitsAtUserTask("RemindColleague")).thenReturn(task -> task.complete());
    when(process.waitsAtServiceTask("BookCarrier")).thenReturn(task -> task.complete());
    when(process.waitsAtMessageIntermediateCatchEvent("WaitForCarrierConfirmation"))
        .thenReturn(message -> message.receive(Map.of("carrierConfirmed", true)));
    when(process.waitsAtMockedCallActivity("ShipOrder"))
        .thenReturn(call -> call.complete(Map.of("shipped", true)));

    Scenario.run(process)
        .withMockedProcess("shipping")
        .startByKey("fulfillment", Map.of("orderId", "order-42"))
        .execute();

    verify(process).hasFinished("WorkFinished");
    verify(process, times(2)).hasFinished("ColleagueReminded");
  }
}
