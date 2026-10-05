/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.fixture.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnEngineException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.Rule;
import org.junit.Test;

@Deployment(resources = "promotions.dmn")
public class PromotionsDecisionTest {
  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Test
  public void evaluatesRequiredDecisionAndCollectsPromotions() {
    DmnDecisionResult result =
        processEngineRule
            .getProcessEngine()
            .getDecisionService()
            .evaluateDecisionByKey("promotions")
            .variables(Variables.putValue("region", "EU"))
            .evaluate();

    assertThat(result.collectEntries("promotion"))
        .containsExactlyInAnyOrder("travel", "lounge");
  }

  @Test
  public void raisesUniqueHitPolicyViolation() {
    ProcessEngineException exception =
        assertThrows(
            ProcessEngineException.class,
            () ->
                processEngineRule
                    .getProcessEngine()
                    .getDecisionService()
                    .evaluateDecisionByKey("uniqueViolation")
                    .variables(Variables.putValue("region", "EU"))
                    .evaluate());
    assertThat(exception).hasRootCauseInstanceOf(DmnEngineException.class);
  }
}
