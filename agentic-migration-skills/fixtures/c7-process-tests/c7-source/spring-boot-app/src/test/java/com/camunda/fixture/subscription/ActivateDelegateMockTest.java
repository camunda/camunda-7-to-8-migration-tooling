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
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:subscription-mock;DB_CLOSE_DELAY=-1")
public class ActivateDelegateMockTest {

  @Autowired private RuntimeService runtimeService;
  @MockBean(name = "activateSubscriptionDelegate")
  private JavaDelegate activateSubscriptionDelegate;

  @Test
  public void mocksDelegateBean() {
    ProcessInstance instance =
        runtimeService.startProcessInstanceByKey(
            "subscription", Map.of("subscriptionId", "sub-mock"));

    assertThat(instance).isWaitingAt("Task_WelcomeCall");
  }
}
