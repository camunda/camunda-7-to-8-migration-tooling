/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.MigrateSpinUsageRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class MigrateSpinUsageRecipeTest implements RewriteTest {

  @Test
  void allClientMigrationAddsSpinGuidance() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import static org.camunda.spin.Spin.JSON;
                import static org.camunda.spin.Spin.XML;

                import org.camunda.spin.json.SpinJsonNode;
                import org.camunda.spin.xml.SpinXmlElement;

                class SpinMigration {
                    record Payload(String status) {}

                    void convert(String jsonText, Payload payload, String xmlText) {
                        SpinJsonNode json = JSON(jsonText);
                        String customerId = json.prop("customerId").stringValue();
                        SpinJsonNode output = JSON(payload);
                        SpinJsonNode stringifiedOutput = JSON(String.valueOf(payload));
                        SpinXmlElement xml = XML(xmlText);
                    }
                }
                """,
            """
                package org.camunda.community.migration.example;

                import static org.camunda.spin.Spin.JSON;
                import static org.camunda.spin.Spin.XML;

                import org.camunda.spin.json.SpinJsonNode;
                import org.camunda.spin.xml.SpinXmlElement;

                // TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine. Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.
                class SpinMigration {
                    record Payload(String status) {}

                    void convert(String jsonText, Payload payload, String xmlText) {
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. If jsonText contains JSON text, use objectMapper.readValue(jsonText, Map.class). For a POJO/Map, pass it directly as a JSON variable or use objectMapper.writeValueAsString(pojo) when a JSON string is required.
                        SpinJsonNode json = JSON(jsonText);
                        // TODO: Replace SpinJsonNode.prop("customerId").stringValue() with JSON Map access such as map.get("customerId").toString() or Jackson mapping.
                        String customerId = json.prop("customerId").stringValue();
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Pass payload as a plain POJO/Map JSON variable; use objectMapper.writeValueAsString(payload) only when a JSON string is required. Do not parse String.valueOf(payload) as JSON.
                        SpinJsonNode output = JSON(payload);
                        // TODO: Camunda Spin JSON(...) receives String.valueOf(payload), which does not serialize a Java object as JSON. Pass payload directly as a POJO/Map variable; use objectMapper.writeValueAsString(payload) only when a JSON string is required.
                        SpinJsonNode stringifiedOutput = JSON(String.valueOf(payload));
                        // TODO: Camunda 8 does not provide native XML process variables. Keep XML as a String and parse it with standard Java XML APIs, or convert the data model to JSON.
                        SpinXmlElement xml = XML(xmlText);
                    }
                }
                """));
  }

  @Test
  void keepsGeneratedGuidanceSingleLine() {
    rewriteRun(
        spec -> spec.recipe(new MigrateSpinUsageRecipe()),
        // language=java
        java(
            """
                import static org.camunda.spin.Spin.JSON;

                import org.camunda.spin.json.SpinJsonNode;

                class SpinExpressionMigration {
                    record Payload(String status) {}

                    void convert(
                        String jsonText,
                        Payload payload,
                        Payload fallbackPayload,
                        boolean usePrimary,
                        int number,
                        char[] characters,
                        SpinJsonNode json) {
                        SpinJsonNode stringValue = JSON(
                            jsonText
                                .trim()
                        );
                        SpinJsonNode pojoValue = JSON(
                            usePrimary
                                ? payload
                                : fallbackPayload
                        );
                        SpinJsonNode stringifiedPojoValue = JSON(String.valueOf(
                            usePrimary
                                ? payload
                                : fallbackPayload
                        ));
                        SpinJsonNode numberValue = JSON(String.valueOf(number));
                        SpinJsonNode characterArrayValue = JSON(String.valueOf(characters));
                        String propertyValue = json.prop("first\\r\\nsecond").stringValue();
                    }
                }
                """,
            """
                import static org.camunda.spin.Spin.JSON;

                import org.camunda.spin.json.SpinJsonNode;

                // TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine. Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.
                class SpinExpressionMigration {
                    record Payload(String status) {}

                    void convert(
                        String jsonText,
                        Payload payload,
                        Payload fallbackPayload,
                        boolean usePrimary,
                        int number,
                        char[] characters,
                        SpinJsonNode json) {
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. If jsonText .trim() contains JSON text, use objectMapper.readValue(jsonText .trim(), Map.class). For a POJO/Map, pass it directly as a JSON variable or use objectMapper.writeValueAsString(pojo) when a JSON string is required.
                        SpinJsonNode stringValue = JSON(
                                jsonText
                                        .trim()
                        );
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Pass usePrimary ? payload : fallbackPayload as a plain POJO/Map JSON variable; use objectMapper.writeValueAsString(usePrimary ? payload : fallbackPayload) only when a JSON string is required. Do not parse String.valueOf(usePrimary ? payload : fallbackPayload) as JSON.
                        SpinJsonNode pojoValue = JSON(
                                usePrimary
                                        ? payload
                                        : fallbackPayload
                        );
                        // TODO: Camunda Spin JSON(...) receives String.valueOf(usePrimary ? payload : fallbackPayload), which does not serialize a Java object as JSON. Pass usePrimary ? payload : fallbackPayload directly as a POJO/Map variable; use objectMapper.writeValueAsString(usePrimary ? payload : fallbackPayload) only when a JSON string is required.
                        SpinJsonNode stringifiedPojoValue = JSON(String.valueOf(
                                usePrimary
                                        ? payload
                                        : fallbackPayload
                        ));
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. If String.valueOf(number) contains JSON text, use objectMapper.readValue(String.valueOf(number), Map.class). For a POJO/Map, pass it directly as a JSON variable or use objectMapper.writeValueAsString(pojo) when a JSON string is required.
                        SpinJsonNode numberValue = JSON(String.valueOf(number));
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. If String.valueOf(characters) contains JSON text, use objectMapper.readValue(String.valueOf(characters), Map.class). For a POJO/Map, pass it directly as a JSON variable or use objectMapper.writeValueAsString(pojo) when a JSON string is required.
                        SpinJsonNode characterArrayValue = JSON(String.valueOf(characters));
                        // TODO: Replace SpinJsonNode.prop("first\\r\\nsecond").stringValue() with JSON Map access such as map.get("first\\r\\nsecond").toString() or Jackson mapping.
                        String propertyValue = json.prop("first\\r\\nsecond").stringValue();
                    }
                }
                """));
  }

  @Test
  void readerInputsUseJacksonReaderOverload() {
    rewriteRun(
        spec -> spec.recipe(new MigrateSpinUsageRecipe()),
        // language=java
        java(
            """
                import static org.camunda.spin.Spin.JSON;

                import java.io.Reader;
                import org.camunda.spin.json.SpinJsonNode;

                class SpinReaderMigration {
                    static class CustomReader extends Reader {
                        @Override
                        public int read(char[] buffer, int offset, int length) {
                            return -1;
                        }

                        @Override
                        public void close() {}
                    }

                    void convert(Reader reader, CustomReader customReader) {
                        SpinJsonNode json = JSON(reader);
                        SpinJsonNode customJson = JSON(customReader);
                    }
                }
                """,
            """
                import static org.camunda.spin.Spin.JSON;

                import java.io.Reader;
                import org.camunda.spin.json.SpinJsonNode;

                // TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine. Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.
                class SpinReaderMigration {
                    static class CustomReader extends Reader {
                        @Override
                        public int read(char[] buffer, int offset, int length) {
                            return -1;
                        }

                        @Override
                        public void close() {}
                    }

                    void convert(Reader reader, CustomReader customReader) {
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Use Jackson's Reader overload objectMapper.readValue(reader, Map.class) to parse the JSON text before setting a JSON process variable.
                        SpinJsonNode json = JSON(reader);
                        // TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Use Jackson's Reader overload objectMapper.readValue(customReader, Map.class) to parse the JSON text before setting a JSON process variable.
                        SpinJsonNode customJson = JSON(customReader);
                    }
                }
                """));
  }
}
