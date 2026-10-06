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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.camunda.bpm.dmn.engine.DmnDecision;
import org.camunda.bpm.dmn.engine.DmnDecisionTableResult;
import org.camunda.bpm.dmn.engine.DmnEngineException;
import org.camunda.bpm.dmn.engine.test.DmnEngineRule;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DiscountDecisionTest {
  @Rule public DmnEngineRule dmnEngineRule = new DmnEngineRule();

  private DmnDecision discountDecision;
  private DmnDecision ruleOrderDecision;
  private DmnDecision uniqueViolationDecision;

  @Before
  public void parseDiscountDecision() throws IOException {
    discountDecision = parseDecision("discount");
    ruleOrderDecision = parseDecision("ruleOrder");
    uniqueViolationDecision = parseDecision("uniqueViolation");
  }

  @Test
  public void evaluatesEveryOutputColumn() {
    VariableMap variables = Variables.createVariables().putValue("customerType", "gold");

    DmnDecisionTableResult result =
        dmnEngineRule.getDmnEngine().evaluateDecisionTable(discountDecision, variables);

    assertThat(result.getSingleResult().getEntryMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("discountRate", "15%", "segment", "premium"));
  }

  @Test
  public void handlesNullOutputColumn() {
    VariableMap variables = Variables.createVariables().putValue("customerType", "trial");

    DmnDecisionTableResult result =
        dmnEngineRule.getDmnEngine().evaluateDecisionTable(discountDecision, variables);

    Object discountRate = result.getSingleResult().getEntry("discountRate");
    Object segment = result.getSingleResult().getEntry("segment");

    assertThat(discountRate).isNull();
    assertThat(segment).isEqualTo("basic");
  }

  @Test
  public void returnsNoMatch() {
    VariableMap variables = Variables.createVariables().putValue("customerType", "bronze");

    DmnDecisionTableResult result =
        dmnEngineRule.getDmnEngine().evaluateDecisionTable(discountDecision, variables);

    assertThat(result).isEmpty();
    assertThat(result.getSingleResult()).isNull();
  }

  @Test
  public void preservesNullInRuleOrderResults() {
    VariableMap variables = Variables.createVariables().putValue("customerType", "gold");

    DmnDecisionTableResult result =
        dmnEngineRule.getDmnEngine().evaluateDecisionTable(ruleOrderDecision, variables);

    assertThat(result.collectEntries("value")).containsExactly("first", null, "last");
  }

  @Test
  public void preservesNullInput() {
    VariableMap variables = Variables.createVariables();
    variables.putValue("customerType", null);

    DmnDecisionTableResult result =
        dmnEngineRule.getDmnEngine().evaluateDecisionTable(discountDecision, variables);

    assertThat(result).isEmpty();
  }

  @Test
  public void raisesUniqueHitPolicyViolation() {
    VariableMap variables = Variables.createVariables().putValue("customerType", "gold");

    assertThrows(
        DmnEngineException.class,
        () ->
            dmnEngineRule
                .getDmnEngine()
                .evaluateDecisionTable(uniqueViolationDecision, variables));
  }

  private DmnDecision parseDecision(String decisionId) throws IOException {
    try (InputStream dmn = getClass().getResourceAsStream("/discount.dmn")) {
      assertThat(dmn).isNotNull();
      return dmnEngineRule.getDmnEngine().parseDecision(decisionId, dmn);
    }
  }
}
