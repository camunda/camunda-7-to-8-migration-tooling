/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EvaluateDecisionServiceTest {

    @ParameterizedTest
    @MethodSource("nullableInputs")
    void shouldKeepNullableInputsInDecisionVariables(
            String sla, String tier, String account, String nullableInput) {
        Map<String, Object> variables =
                EvaluateDecisionService.createDecisionVariables("Europe/Prague", sla, tier, account);

        assertThat(variables).containsKeys("timezone", "sla", "tier", "account").hasSize(4);
        assertThat(variables).containsKey(nullableInput);
        assertThat(variables.get(nullableInput)).isNull();
    }

    @Test
    void shouldKeepAllValuesForCompleteInputSet() {
        Map<String, Object> variables = EvaluateDecisionService.createDecisionVariables(
                "Europe/Prague", "gold", "priority", "acme");

        assertThat(variables)
                .containsEntry("timezone", "Europe/Prague")
                .containsEntry("sla", "gold")
                .containsEntry("tier", "priority")
                .containsEntry("account", "acme");
    }

    private static Stream<Arguments> nullableInputs() {
        return Stream.of(
                Arguments.of(null, "priority", "acme", "sla"),
                Arguments.of("gold", null, "acme", "tier"),
                Arguments.of("gold", "priority", null, "account"));
    }
}
