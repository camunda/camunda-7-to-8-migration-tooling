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
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckMessage;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckResult;
import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
            "disabled": true,
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
  void shouldPreservePropertiesWithoutConvertibleExpressions() throws Exception {
    JsonNode source = OBJECT_MAPPER.readTree(C7_FORM);
    JsonNode converted =
        OBJECT_MAPPER.readTree(FormConverter.convert(C7_FORM, defaultProperties()));

    assertThat(converted.get("executionPlatform").asText()).isEqualTo("Camunda Cloud");
    assertThat(converted.get("executionPlatformVersion").asText()).isEqualTo("8.9.0");
    assertThat(converted.get("schemaVersion").asInt()).isEqualTo(16);
    assertThat(converted.path("components").path(0).path("disabled").asBoolean()).isTrue();

    ((ObjectNode) source).remove("executionPlatform");
    ((ObjectNode) source).remove("executionPlatformVersion");
    ((ObjectNode) converted).remove("executionPlatform");
    ((ObjectNode) converted).remove("executionPlatformVersion");

    assertThat(converted).isEqualTo(source);
  }

  @Test
  void shouldTransformSimpleJuelReferencesAndReportReviewFindings() throws Exception {
    String input =
        """
        {
          "executionPlatform": "Camunda Platform",
          "executionPlatformVersion": "7.23.0",
          "id": "myForm",
          "components": [
            {
              "label": "${customerName}",
              "type": "textfield",
              "key": "greeting",
              "defaultValue": "#{defaultGreeting}",
              "values": [
                { "label": "${optionLabel}", "value": "a" }
              ]
            },
            {
              "type": "group",
              "id": "group",
              "components": [
                {
                  "type": "text",
                  "id": "message",
                  "text": "${messageText}"
                }
              ]
            }
          ],
          "type": "default",
          "schemaVersion": 18
        }
        """;

    FormConversionResult result =
        FormConverter.convertAndCheck("myForm.form", input, defaultProperties());
    JsonNode converted = OBJECT_MAPPER.readTree(result.convertedForm());

    JsonNode firstComponent = converted.path("components").path(0);
    assertThat(firstComponent.path("label").asText()).isEqualTo("= customerName");
    assertThat(firstComponent.path("defaultValue").asText()).isEqualTo("= defaultGreeting");
    assertThat(firstComponent.path("values").path(0).path("label").asText())
        .isEqualTo("= optionLabel");
    assertThat(
            converted.path("components").path(1).path("components").path(0).path("text").asText())
        .isEqualTo("= messageText");

    assertThat(result.checkResult().getFilename()).isEqualTo("myForm.form");
    assertThat(result.checkResult().getResults())
        .extracting(ElementCheckResult::getElementId)
        .containsExactly("greeting", "message");
    assertThat(result.checkResult().getResults().get(0).getElementName())
        .isEqualTo("${customerName}");
    assertThat(result.checkResult().getResults())
        .flatExtracting(ElementCheckResult::getMessages)
        .hasSize(4)
        .allSatisfy(
            message -> {
              assertThat(message.getId()).isEqualTo("expression");
              assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
              assertThat(message.getMessage()).contains("Please review transformed expression");
              assertThat(message.getLink()).contains("/feel/");
            })
        .extracting(ElementCheckMessage::getMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("'${customerName}' -> '= customerName'")
                    .contains("property 'label'"))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("'#{defaultGreeting}' -> '= defaultGreeting'")
                    .contains("property 'defaultValue'"))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("'${optionLabel}' -> '= optionLabel'")
                    .contains("property 'values'"))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("'${messageText}' -> '= messageText'")
                    .contains("property 'text'"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "${customer.name}",
        "${customerService.lookup()}",
        "${execution}",
        "${execution.getVariable(\"customerName\")}",
        "${amount > 10}",
        "Hello ${customerName}",
        "${firstName}${lastName}",
        "${if}"
      })
  void shouldLeaveUnsafeJuelExpressionsUnchanged(String expression) throws Exception {
    String input =
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
              "defaultValue": %s
            }
          ],
          "type": "default",
          "schemaVersion": 18
        }
        """
            .formatted(OBJECT_MAPPER.writeValueAsString(expression));

    FormConversionResult result =
        FormConverter.convertAndCheck("myForm.form", input, defaultProperties());
    JsonNode converted = OBJECT_MAPPER.readTree(result.convertedForm());

    assertThat(converted.path("components").path(0).path("defaultValue").asText())
        .isEqualTo(expression);
    assertThat(result.checkResult().getResults())
        .flatExtracting(ElementCheckResult::getMessages)
        .isNotEmpty()
        .allSatisfy(
            message -> {
              assertThat(message.getId()).isEqualTo("form-juel-expression");
              assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
            });
  }

  @Test
  void shouldNotTransformStructuralComponentProperties() throws Exception {
    String input =
        """
        {
          "executionPlatform": "Camunda Platform",
          "executionPlatformVersion": "7.23.0",
          "id": "myForm",
          "components": [
            {
              "label": "Customer name",
              "type": "textfield",
              "key": "${customerName}"
            }
          ],
          "type": "default",
          "schemaVersion": 18
        }
        """;

    FormConversionResult result =
        FormConverter.convertAndCheck("myForm.form", input, defaultProperties());
    JsonNode converted = OBJECT_MAPPER.readTree(result.convertedForm());

    assertThat(converted.path("components").path(0).path("key").asText())
        .isEqualTo("${customerName}");
    assertThat(result.checkResult().getResults())
        .flatExtracting(ElementCheckResult::getMessages)
        .extracting(ElementCheckMessage::getId)
        .containsExactly("form-juel-expression");
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
