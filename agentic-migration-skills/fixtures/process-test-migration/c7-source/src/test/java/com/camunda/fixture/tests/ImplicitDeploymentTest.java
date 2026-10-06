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
import org.camunda.bpm.engine.test.ProcessEngineTestCase;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;

@Deployment
public class ImplicitDeploymentTest extends ProcessEngineTestCase {

  private String processDefinitionKey;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    processDefinitionKey = "implicit-process";
  }

  @Override
  protected void tearDown() throws Exception {
    processDefinitionKey = null;
    super.tearDown();
  }

  public void testStartsImplicitlyDeployedProcess() {
    ProcessInstance processInstance =
        runtimeService().startProcessInstanceByKey(processDefinitionKey);

    assertThat(processInstance).isEnded();
  }
}
