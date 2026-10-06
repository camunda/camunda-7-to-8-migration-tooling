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
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;

@Deployment(resources = "com/camunda/fixture/tests/process-test-cases.bpmn")
public class OrderProcessTest {

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Test
  public void approvesOrder() {
    ProcessInstance processInstance =
        runtimeService().startProcessInstanceByKey("order-process", withVariables("amount", 100));

    assertThat(processInstance).isWaitingAt("Task_Approve").isNotWaitingAt("Task_Escalate");
    complete(task(), withVariables("approved", true));
    assertThat(processInstance)
        .hasPassed("Task_Approve")
        .isEnded()
        .variables()
        .containsEntry("approved", true);
  }

  @Test
  public void escalatesAfterOneDay() {
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("order-process");

    assertThat(processInstance).isWaitingAt("Task_Approve");
    execute(job());
    assertThat(processInstance)
        .isWaitingAt("Task_Escalate")
        .isNotWaitingAt("Task_Approve");
  }

  @Test
  public void continuesAfterAsync() {
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("async-process");

    assertThat(processInstance).isNotEnded();
    execute(job());
    assertThat(processInstance).isWaitingAt("Task_AfterAsync");
  }

  @Test(expected = IllegalStateException.class)
  public void failsWhenDelegateThrows() {
    runtimeService().startProcessInstanceByKey("failing-process");
  }
}
