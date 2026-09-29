/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openrewrite.java.Assertions.java;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class MigrateSpinUsageRecipeTest implements RewriteTest {

  @Test
  void allClientMigrationAddsSpinGuidance() {
    rewriteRun(
        spec ->
            spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        java(
            """
                import static org.camunda.spin.Spin.JSON;
                import static org.camunda.spin.Spin.XML;

                import java.io.Reader;
                import java.util.Map;
                import org.camunda.spin.json.SpinJsonNode;
                import org.camunda.spin.xml.SpinXmlElement;

                class SpinMigration {
                    record Payload(Map<String, Object> variables) {}

                    void convert(String jsonText, Payload payload, Reader reader, String xmlText, SpinJsonNode json) {
                        SpinJsonNode fromText = JSON(jsonText);
                        SpinJsonNode fromPojo = JSON(payload);
                        SpinJsonNode fromObjectString = JSON(String.valueOf(payload));
                        SpinJsonNode fromReader = JSON(reader);
                        String property = json.prop("customerId").stringValue();
                        SpinXmlElement xml = XML(xmlText);
                    }
                }
                """,
            """
                import static org.camunda.spin.Spin.JSON;
                import static org.camunda.spin.Spin.XML;

                import java.io.Reader;
                import java.util.Map;
                import org.camunda.spin.json.SpinJsonNode;
                import org.camunda.spin.xml.SpinXmlElement;

                // TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine. Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.
                class SpinMigration {
                    record Payload(Map<String, Object> variables) {}

                    void convert(String jsonText, Payload payload, Reader reader, String xmlText, SpinJsonNode json) {
                        // TODO: Replace Spin JSON(...): parse existing JSON text or streams with objectMapper.readValue(input, Map.class); pass POJOs/Maps directly (or use objectMapper.writeValueAsString(pojo) when a JSON string is needed). Do not parse String.valueOf(pojo) as JSON.
                        SpinJsonNode fromText = JSON(jsonText);
                        // TODO: Replace Spin JSON(...): parse existing JSON text or streams with objectMapper.readValue(input, Map.class); pass POJOs/Maps directly (or use objectMapper.writeValueAsString(pojo) when a JSON string is needed). Do not parse String.valueOf(pojo) as JSON.
                        SpinJsonNode fromPojo = JSON(payload);
                        // TODO: Replace Spin JSON(...): parse existing JSON text or streams with objectMapper.readValue(input, Map.class); pass POJOs/Maps directly (or use objectMapper.writeValueAsString(pojo) when a JSON string is needed). Do not parse String.valueOf(pojo) as JSON.
                        SpinJsonNode fromObjectString = JSON(String.valueOf(payload));
                        // TODO: Replace Spin JSON(...): parse existing JSON text or streams with objectMapper.readValue(input, Map.class); pass POJOs/Maps directly (or use objectMapper.writeValueAsString(pojo) when a JSON string is needed). Do not parse String.valueOf(pojo) as JSON.
                        SpinJsonNode fromReader = JSON(reader);
                        // TODO: Replace SpinJsonNode.prop(...).stringValue() with Map access or Jackson mapping; handle missing or null properties.
                        String property = json.prop("customerId").stringValue();
                        // TODO: Camunda 8 does not provide native XML process variables. Keep XML as a String and parse it with Java XML APIs, or convert the data model to JSON.
                        SpinXmlElement xml = XML(xmlText);
                    }
                }
                """));
  }

  @Test
  void jacksonSerializesNestedPojoInsteadOfParsingItsToString() throws JsonProcessingException {
    record Payload(Map<String, Object> variables) {}

    Payload payload = new Payload(Map.of("order", Map.of("status", "ready")));
    ObjectMapper mapper = new ObjectMapper();

    assertEquals(
        "ready",
        mapper
            .readTree(mapper.writeValueAsString(payload))
            .path("variables")
            .path("order")
            .path("status")
            .asText());
    assertThrows(JsonProcessingException.class, () -> mapper.readTree(String.valueOf(payload)));
  }

  @Test
  void propertyKeysAreNotInterpolatedIntoComments() {
    rewriteRun(
        spec -> spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        java(
            """
                import org.camunda.spin.json.SpinJsonNode;

                class SpecialKey {
                    String read(SpinJsonNode json) {
                        return json.prop("literal\\\\u000a\\r\\n").stringValue();
                    }
                }
                """,
            """
                import org.camunda.spin.json.SpinJsonNode;

                // TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine. Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.
                class SpecialKey {
                    String read(SpinJsonNode json) {
                        // TODO: Replace SpinJsonNode.prop(...).stringValue() with Map access or Jackson mapping; handle missing or null properties.
                        return json.prop("literal\\\\u000a\\r\\n").stringValue();
                    }
                }
                """));
  }
}
