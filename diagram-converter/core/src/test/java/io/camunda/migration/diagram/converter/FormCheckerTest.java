/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.*;

import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckMessage;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckResult;
import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import org.junit.jupiter.api.Test;

class FormCheckerTest {

  private static final String CLEAN_C7_FORM =
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
        "schemaVersion": %s
      }
      """;

  @Test
  void shouldReportNothingForCleanRecentForm() {
    DiagramCheckResult result = check(String.format(CLEAN_C7_FORM, 18));

    assertThat(result.getResults()).isEmpty();
  }

  @Test
  void shouldSetFilename() {
    DiagramCheckResult result = check(String.format(CLEAN_C7_FORM, 18));

    assertThat(result.getFilename()).isEqualTo("myForm.form");
  }

  @Test
  void shouldReportOutdatedSchemaVersionAsFileLevelFinding() {
    DiagramCheckResult result = check(String.format(CLEAN_C7_FORM, 16));

    assertThat(result.getResults()).hasSize(1);
    ElementCheckResult element = result.getResults().get(0);
    assertThat(element.getElementType()).isEqualTo(FormChecker.FORM_ELEMENT_TYPE);
    assertThat(element.getElementId()).isEqualTo("myForm");
    assertThat(element.getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-schema-version-outdated");
              assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
              assertThat(message.getMessage()).contains("16").contains("18");
              assertThat(message.getLink()).isNotBlank();
            });
  }

  @Test
  void shouldReportMissingSchemaVersion() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [],
              "type": "default"
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-schema-version-missing");
              assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
            });
  }

  @Test
  void shouldReportAlreadyCamunda8Form() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Cloud",
              "executionPlatformVersion": "8.9.0",
              "id": "myForm",
              "components": [],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).getElementType())
        .isEqualTo(FormChecker.FORM_ELEMENT_TYPE);
    assertThat(result.getResults().get(0).getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-already-camunda-8");
              assertThat(message.getSeverity()).isEqualTo(Severity.INFO);
            });
  }

  @Test
  void shouldReportTransformedAndUntransformedJuelExpressionsOnComponent() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                {
                  "label": "Hello ${customerName}",
                  "type": "textfield",
                  "key": "greeting",
                  "defaultValue": "#{defaultGreeting}"
                }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    ElementCheckResult element = result.getResults().get(0);
    assertThat(element.getElementId()).isEqualTo("greeting");
    assertThat(element.getElementType()).isEqualTo("textfield");
    assertThat(element.getElementName()).isEqualTo("Hello ${customerName}");
    assertThat(element.getMessages())
        .hasSize(2)
        .anySatisfy(
            message -> {
              assertThat(message.getId()).isEqualTo("form-juel-expression");
              assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
              assertThat(message.getMessage())
                  .contains("${customerName}")
                  .contains("label")
                  .contains("Migrate this expression manually.");
            })
        .anySatisfy(
            message -> {
              assertThat(message.getId()).isEqualTo("expression");
              assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
              assertThat(message.getMessage())
                  .contains("#{defaultGreeting}")
                  .contains("= defaultGreeting")
                  .contains("defaultValue");
            });
  }

  @Test
  void shouldNotReportFeelExpressions() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                {
                  "label": "= customerName",
                  "type": "textfield",
                  "key": "greeting"
                }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).isEmpty();
  }

  @Test
  void shouldReportTransformedJuelExpressionsInNestedProperties() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                {
                  "label": "Options",
                  "type": "select",
                  "key": "choice",
                  "values": [
                    { "label": "${optionA}", "value": "a" },
                    { "label": "Static", "value": "b" }
                  ]
                }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    ElementCheckResult element = result.getResults().get(0);
    assertThat(element.getElementId()).isEqualTo("choice");
    assertThat(element.getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("expression");
              assertThat(message.getMessage())
                  .contains("${optionA}")
                  .contains("= optionA")
                  .contains("values");
            });
  }

  @Test
  void shouldReportUnknownComponentType() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                {
                  "label": "Legacy widget",
                  "type": "camunda7-legacy-widget",
                  "key": "legacy"
                }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    ElementCheckResult element = result.getResults().get(0);
    assertThat(element.getElementId()).isEqualTo("legacy");
    assertThat(element.getElementType()).isEqualTo("camunda7-legacy-widget");
    assertThat(element.getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-component-unknown");
              assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
              assertThat(message.getMessage()).contains("camunda7-legacy-widget");
            });
  }

  @Test
  void shouldCheckComponentsNestedInGroups() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                {
                  "type": "group",
                  "id": "group1",
                  "components": [
                    {
                      "label": "Nested ${value}",
                      "type": "textfield",
                      "key": "nested"
                    }
                  ]
                }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    ElementCheckResult element = result.getResults().get(0);
    assertThat(element.getElementId()).isEqualTo("nested");
    assertThat(element.getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-juel-expression");
              assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
              assertThat(message.getMessage()).contains("Migrate this expression manually.");
            });
  }

  @Test
  void shouldGroupFindingsPerComponentAndReportEachComponent() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                { "label": "A ${x}", "type": "textfield", "key": "first" },
                { "label": "B ${y}", "type": "textarea", "key": "second" }
              ],
              "type": "default",
              "schemaVersion": 16
            }
            """);

    assertThat(result.getResults()).hasSize(3);
    assertThat(result.getResults())
        .extracting(ElementCheckResult::getElementType)
        .containsExactly(FormChecker.FORM_ELEMENT_TYPE, "textfield", "textarea");
    assertThat(result.getResults())
        .extracting(ElementCheckResult::getElementId)
        .containsExactly("myForm", "first", "second");
  }

  @Test
  void shouldReportComponentWithMissingType() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                { "label": "No type", "key": "typeless" }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    assertThat(result.getResults()).hasSize(1);
    ElementCheckResult element = result.getResults().get(0);
    assertThat(element.getElementId()).isEqualTo("typeless");
    assertThat(element.getElementType()).isEqualTo("unknown");
    assertThat(element.getMessages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.getId()).isEqualTo("form-component-unknown");
              assertThat(message.getMessage()).contains("missing");
            });
  }

  @Test
  void shouldKeepFindingsSeparateForComponentsWithoutKeyOrId() {
    DiagramCheckResult result =
        check(
            """
            {
              "executionPlatform": "Camunda Platform",
              "executionPlatformVersion": "7.23.0",
              "id": "myForm",
              "components": [
                { "type": "textfield", "defaultValue": "${a}" },
                { "type": "textfield", "defaultValue": "${b}" }
              ],
              "type": "default",
              "schemaVersion": 18
            }
            """);

    // both keyless textfields fall back to the type as display id, but must not be merged
    assertThat(result.getResults()).hasSize(2);
    assertThat(result.getResults())
        .extracting(ElementCheckResult::getElementId)
        .containsExactly("textfield", "textfield");
    assertThat(result.getResults())
        .flatExtracting(ElementCheckResult::getMessages)
        .extracting(ElementCheckMessage::getMessage)
        .anySatisfy(message -> assertThat(message).contains("${a}"))
        .anySatisfy(message -> assertThat(message).contains("${b}"));
  }

  @Test
  void shouldRejectInvalidJson() {
    assertThatThrownBy(() -> check("{ not json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not valid JSON");
  }

  @Test
  void shouldRejectNonObjectJson() {
    assertThatThrownBy(() -> check("[1, 2]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object");
  }

  @Test
  void shouldRejectInvalidPlatformVersion() {
    DefaultConverterProperties properties = new DefaultConverterProperties();
    properties.setPlatformVersion("not-a-version");

    assertThatThrownBy(
            () -> FormChecker.check("myForm.form", String.format(CLEAN_C7_FORM, 18), properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not a valid platform version");
  }

  private DiagramCheckResult check(String formContent) {
    return FormChecker.check(
        "myForm.form", formContent, ConverterPropertiesFactory.getInstance().get());
  }
}
