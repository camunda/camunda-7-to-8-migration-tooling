/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.fixture.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-promotions.dmn")
class PromotionsDecisionTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private CamundaClient client;

  @Test
  void evaluatesRequiredDecisionAndCollectsPromotions() throws Exception {
    EvaluateDecisionResponse response =
        client
            .newEvaluateDecisionCommand()
            .decisionId("promotions")
            .variables(Map.of("region", "EU"))
            .send()
            .join();

    CamundaAssert.assertThat(response).isEvaluated();
    List<String> promotions =
        OBJECT_MAPPER.readValue(
            response.getDecisionOutput(), new TypeReference<List<String>>() {});

    assertThat(promotions).containsExactlyInAnyOrder("travel", "lounge");
    assertThat(response.getEvaluatedDecisions()).hasSize(2);
  }

  @Test
  void raisesUniqueHitPolicyViolation() {
    EvaluateDecisionResponse response =
        client
            .newEvaluateDecisionCommand()
            .decisionId("uniqueViolation")
            .variables(Map.of("region", "EU"))
            .send()
            .join();

    assertThat(response.getFailedDecisionId()).isEqualTo("uniqueViolation");
    assertThat(response.getFailureMessage()).isNotBlank();
  }
}
