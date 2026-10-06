/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests.assertThat;

import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

public class SupportCaseTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Test
  @Deployment(resources = "support-case.cmmn")
  public void startsSupportCase() {
    CaseInstance instance =
        processEngineRule.getCaseService().createCaseInstanceByKey("support-case");

    assertThat(instance).isActive();
  }
}
