/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EvaluateDecisionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DECISION_ID = "sla_package_id";
    private final CamundaClient camundaClient;

    public EvaluateDecisionService(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    public String getSlaPackageId(String timezone, String sla, String tier, String account)
            throws JsonProcessingException {
        return extractSlaPackageId(evaluateDecision(timezone, sla, tier, account));
    }

    static String extractSlaPackageId(EvaluateDecisionResponse response) throws JsonProcessingException {
        if (response == null || response.getDecisionOutput() == null) {
            return null;
        }
        JsonNode output = OBJECT_MAPPER.readTree(response.getDecisionOutput());
        if (output == null) {
            throw new IllegalStateException("Decision output is empty");
        }
        if (output.isNull()) {
            return null;
        }
        if (!output.isObject()) {
            throw new IllegalStateException("Expected decision output to be a JSON object");
        }
        JsonNode slaPackageId = output.get("slaPackageId");
        if (slaPackageId == null) {
            throw new IllegalStateException("Decision output is missing slaPackageId");
        }
        if (slaPackageId.isNull()) {
            return null;
        }
        if (!slaPackageId.isTextual()) {
            throw new IllegalStateException("Expected slaPackageId to be a string");
        }
        return slaPackageId.textValue();
    }

    public EvaluateDecisionResponse evaluateDecision(String timezone, String sla, String tier, String account) {
        if (timezone == null) {
            return null;
        }

        return camundaClient.newEvaluateDecisionCommand()
                .decisionId(DECISION_ID)
                .variables(createDecisionVariables(timezone, sla, tier, account))
                .send()
                .join();
    }

    static Map<String, Object> createDecisionVariables(String timezone, String sla, String tier, String account) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("timezone", timezone);
        variables.put("sla", sla);
        variables.put("tier", tier);
        variables.put("account", account);
        return variables;
    }
}
