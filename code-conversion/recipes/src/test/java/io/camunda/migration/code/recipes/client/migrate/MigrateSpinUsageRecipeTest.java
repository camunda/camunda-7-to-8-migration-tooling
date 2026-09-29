/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

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
}
