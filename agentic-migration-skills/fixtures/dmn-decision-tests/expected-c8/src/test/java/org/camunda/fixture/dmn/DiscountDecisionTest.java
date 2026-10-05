/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.fixture.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-discount.dmn")
class DiscountDecisionTest {
  private CamundaClient client;

  @Test
  void evaluatesEveryOutputColumn() {
    EvaluateDecisionResponse response = evaluate(Map.of("customerType", "gold"));

    CamundaAssert.assertThat(response)
        .isEvaluated()
        .hasOutput(Map.of("discountRate", "15%", "segment", "premium"));
  }

  @Test
  void returnsNoMatch() {
    EvaluateDecisionResponse response = evaluate(Map.of("customerType", "bronze"));

    CamundaAssert.assertThat(response).isEvaluated().hasNoMatchedRules();
  }

  @Test
  void preservesNullInput() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("customerType", null);

    EvaluateDecisionResponse response = evaluate(variables);

    CamundaAssert.assertThat(response).isEvaluated().hasNoMatchedRules();
  }

  @Test
  void raisesUniqueHitPolicyViolation() {
    EvaluateDecisionResponse response =
        evaluate("uniqueViolation", Map.of("customerType", "gold"));

    assertThat(response.getFailedDecisionId()).isEqualTo("uniqueViolation");
    assertThat(response.getFailureMessage()).isNotBlank();
  }

  private EvaluateDecisionResponse evaluate(Map<String, Object> variables) {
    return evaluate("discount", variables);
  }

  private EvaluateDecisionResponse evaluate(
      String decisionId, Map<String, Object> variables) {
    return client
        .newEvaluateDecisionCommand()
        .decisionId(decisionId)
        .variables(variables)
        .send()
        .join();
  }
}
