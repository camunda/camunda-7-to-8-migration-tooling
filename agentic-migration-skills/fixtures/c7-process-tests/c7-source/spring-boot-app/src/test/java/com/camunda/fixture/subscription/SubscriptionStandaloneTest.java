/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;

import java.util.Map;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.spring.boot.starter.test.helper.AbstractProcessEngineRuleTest;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

public class SubscriptionStandaloneTest extends AbstractProcessEngineRuleTest {

  @After
  public void resetMocks() {
    Mocks.reset();
  }

  @Test
  @Deployment(resources = "subscription.bpmn")
  public void startsSubscriptionWithoutSpring() {
    Mocks.register("activateSubscriptionDelegate", Mockito.mock(JavaDelegate.class));

    ProcessInstance instance =
        processEngine.getRuntimeService().startProcessInstanceByKey(
            "subscription", Map.of("subscriptionId", "sub-standalone"));

    assertThat(instance).isWaitingAt("Task_WelcomeCall");
  }
}
