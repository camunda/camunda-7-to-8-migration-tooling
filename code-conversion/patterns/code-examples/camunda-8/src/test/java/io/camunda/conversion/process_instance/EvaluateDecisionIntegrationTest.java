/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class EvaluateDecisionIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private EvaluateDecisionService service;

    @BeforeEach
    void deployDecision() {
        camundaClient.newDeployResourceCommand()
                .addResourceFromClasspath("dmn/sla-package.dmn")
                .send()
                .join();
    }

    @Test
    void shouldPreserveMissingTimezoneBehavior() throws JsonProcessingException {
        assertThat(service.getSlaPackageId(null, null, null, null)).isNull();
    }

    @Test
    void shouldEvaluateDecisionWithNullSla() throws JsonProcessingException {
        assertThat(service.getSlaPackageId("Europe/Prague", null, "priority", "acme"))
                .isEqualTo("null-sla");
    }

    @Test
    void shouldEvaluateDecisionWithNullTier() throws JsonProcessingException {
        assertThat(service.getSlaPackageId("Europe/Prague", "gold", null, "acme"))
                .isEqualTo("null-tier");
    }

    @Test
    void shouldEvaluateDecisionWithNullAccount() throws JsonProcessingException {
        assertThat(service.getSlaPackageId("Europe/Prague", "gold", "priority", null))
                .isEqualTo("null-account");
    }

    @Test
    void shouldEvaluateDecisionWithCompleteInputSet() throws JsonProcessingException {
        assertThat(service.getSlaPackageId("Europe/Prague", "gold", "priority", "acme"))
                .isEqualTo("complete");
    }

    @Test
    void shouldReturnNullWhenNoRuleMatches() throws JsonProcessingException {
        EvaluateDecisionResponse response = service.evaluateDecision("UTC", "bronze", "basic", "other");

        assertThat(EvaluateDecisionService.extractSlaPackageId(response)).isNull();
        CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response))
                .isEvaluated()
                .hasNoMatchedRules();
    }

    @Test
    void shouldExposeCamundaDecisionResponseShape() throws JsonProcessingException {
        EvaluateDecisionResponse response =
                service.evaluateDecision("Europe/Prague", "gold", "priority", "acme");
        JsonNode output = OBJECT_MAPPER.readTree(response.getDecisionOutput());

        assertThat(output.isObject()).isTrue();
        assertThat(output.get("slaPackageId").asText()).isEqualTo("complete");
        assertThat(output.get("matchedRule").asText()).isEqualTo("complete");
        assertThat(response.getEvaluatedDecisions()).hasSize(1);
        CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response))
                .isEvaluated()
                .hasOutput(Map.of("slaPackageId", "complete", "matchedRule", "complete"))
                .hasMatchedRules(1);
    }
}
