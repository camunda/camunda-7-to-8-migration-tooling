/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class FormConverterTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String C7_FORM =
      """
      {
        "executionPlatform": "Camunda Platform",
        "executionPlatformVersion": "7.23.0",
        "id": "myForm",
        "components": [
          {
            "label": "Customer name",
            "type": "textfield",
            "key": "customerName",
            "validate": { "required": true }
          }
        ],
        "type": "default",
        "schemaVersion": 16
      }
      """;

  @Test
  void shouldIdentifyFormFile() {
    assertThat(FormConverter.isFormFile("myForm.form")).isTrue();
    assertThat(FormConverter.isFormFile("myForm.bpmn")).isFalse();
    assertThat(FormConverter.isFormFile("myForm.dmn")).isFalse();
    assertThat(FormConverter.isFormFile(null)).isFalse();
  }

  @Test
  void shouldSetTargetPlatformMetadata() throws Exception {
    String converted = FormConverter.convert(C7_FORM, defaultProperties());

    JsonNode root = OBJECT_MAPPER.readTree(converted);
    assertThat(root.get("executionPlatform").asText()).isEqualTo("Camunda Cloud");
    assertThat(root.get("executionPlatformVersion").asText()).isEqualTo("8.9.0");
  }

  @Test
  void shouldNotChangeAnythingButPlatformMetadata() throws Exception {
    JsonNode source = OBJECT_MAPPER.readTree(C7_FORM);
    JsonNode converted =
        OBJECT_MAPPER.readTree(FormConverter.convert(C7_FORM, defaultProperties()));

    assertThat(converted.get("executionPlatform").asText()).isEqualTo("Camunda Cloud");
    assertThat(converted.get("executionPlatformVersion").asText()).isEqualTo("8.9.0");

    ((ObjectNode) source).remove("executionPlatform");
    ((ObjectNode) source).remove("executionPlatformVersion");
    ((ObjectNode) converted).remove("executionPlatform");
    ((ObjectNode) converted).remove("executionPlatformVersion");

    assertThat(converted).isEqualTo(source);
  }

  @Test
  void shouldUseConfiguredPlatformVersion() throws Exception {
    DefaultConverterProperties properties = new DefaultConverterProperties();
    properties.setPlatformVersion("8.8");
    ConverterProperties merged = ConverterPropertiesFactory.getInstance().merge(properties);

    String converted = FormConverter.convert(C7_FORM, merged);

    JsonNode root = OBJECT_MAPPER.readTree(converted);
    assertThat(root.get("executionPlatformVersion").asText()).isEqualTo("8.8.0");
  }

  @Test
  void shouldNormalizePatchVersionToZero() throws Exception {
    DefaultConverterProperties properties = new DefaultConverterProperties();
    properties.setPlatformVersion("8.9.3");
    ConverterProperties merged = ConverterPropertiesFactory.getInstance().merge(properties);

    String converted = FormConverter.convert(C7_FORM, merged);

    JsonNode root = OBJECT_MAPPER.readTree(converted);
    assertThat(root.get("executionPlatformVersion").asText()).isEqualTo("8.9.0");
  }

  @Test
  void shouldAddPlatformMetadataIfAbsent() throws Exception {
    String input =
        """
        {
          "id": "myForm",
          "components": []
        }
        """;

    String converted = FormConverter.convert(input, defaultProperties());

    JsonNode root = OBJECT_MAPPER.readTree(converted);
    assertThat(root.get("executionPlatform").asText()).isEqualTo("Camunda Cloud");
    assertThat(root.get("executionPlatformVersion").asText()).isEqualTo("8.9.0");
    assertThat(root.get("id").asText()).isEqualTo("myForm");
  }

  @Test
  void shouldRejectNonObjectJson() {
    assertThatThrownBy(() -> FormConverter.convert("[1, 2, 3]", defaultProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object");
  }

  @Test
  void shouldRejectInvalidJson() {
    assertThatThrownBy(() -> FormConverter.convert("this is not json", defaultProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not valid JSON");
  }

  @Test
  void shouldRejectEmptyContent() {
    assertThatThrownBy(() -> FormConverter.convert("", defaultProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object")
        .hasMessageContaining("empty");
    assertThatThrownBy(() -> FormConverter.convert("   ", defaultProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object")
        .hasMessageContaining("empty");
  }

  @Test
  void shouldRejectInvalidPlatformVersion() {
    DefaultConverterProperties properties = new DefaultConverterProperties();
    properties.setPlatformVersion("not-a-version");

    assertThatThrownBy(() -> FormConverter.convert(C7_FORM, properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not a valid platform version");
  }

  @Test
  void shouldRejectMissingPlatformVersion() {
    assertThatThrownBy(() -> FormConverter.convert(C7_FORM, new DefaultConverterProperties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No platform version configured");
  }

  private ConverterProperties defaultProperties() {
    return ConverterPropertiesFactory.getInstance().get();
  }
}
