/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-promotions.dmn")
class PromotionsDecisionTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private CamundaClient client;

  @Test
  void evaluatesRequiredDecision() throws Exception {
    EvaluateDecisionResponse response = evaluate("promotions", Map.of("amount", 120));

    CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated();
    assertThat(response.getEvaluatedDecisions()).hasSize(2);
    List<String> promotions =
        OBJECT_MAPPER.readValue(
            response.getDecisionOutput(), new TypeReference<List<String>>() {});
    assertThat(promotions).hasSize(2);
  }

  @Test
  void collectsPromotionRules() throws Exception {
    EvaluateDecisionResponse response = evaluate("promotions", Map.of("amount", 120));
    CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated();
    List<String> promotions =
        OBJECT_MAPPER.readValue(
            response.getDecisionOutput(), new TypeReference<List<String>>() {});

    assertThat(promotions)
        .containsExactlyInAnyOrder("free-shipping", "priority-support");
  }

  @Test
  void raisesUniqueHitPolicyViolation() {
    EvaluateDecisionResponse response =
        evaluate("promotionUniqueViolation", Map.of("tier", "gold"));

    assertThat(response.getFailedDecisionId()).isEqualTo("promotionUniqueViolation");
    assertThat(response.getFailureMessage()).isNotBlank();
  }

  private EvaluateDecisionResponse evaluate(
      String decisionId, Map<String, Object> variables) {
    return client.newEvaluateDecisionCommand()
        .decisionId(decisionId)
        .variables(variables)
        .send()
        .join();
  }
}
