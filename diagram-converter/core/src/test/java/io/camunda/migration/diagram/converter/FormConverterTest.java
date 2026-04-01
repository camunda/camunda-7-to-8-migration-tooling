/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormConverterTest {

  @Test
  void shouldIdentifyFormFile() {
    assertThat(FormConverter.isFormFile("myForm.form")).isTrue();
    assertThat(FormConverter.isFormFile("myForm.bpmn")).isFalse();
    assertThat(FormConverter.isFormFile("myForm.dmn")).isFalse();
    assertThat(FormConverter.isFormFile(null)).isFalse();
  }

  @Test
  void shouldConvertExecutionPlatformFields() {
    String input =
        """
        {
          "executionPlatform": "Camunda Platform",
          "executionPlatformVersion": "7.23.0",
          "id": "myForm",
          "components": [],
          "type": "default",
          "schemaVersion": 16
        }
        """;

    String converted = FormConverter.convert(input);

    assertThat(converted).contains("\"executionPlatform\": \"Camunda Cloud\"");
    assertThat(converted).contains("\"executionPlatformVersion\": \"8.9.0\"");
    assertThat(converted).doesNotContain("\"Camunda Platform\"");
    assertThat(converted).doesNotContain("\"7.23.0\"");
  }

  @Test
  void shouldKeepOtherFieldsUnchanged() {
    String input =
        """
        {
          "executionPlatform": "Camunda Platform",
          "executionPlatformVersion": "7.23.0",
          "id": "myForm",
          "components": [],
          "type": "default",
          "schemaVersion": 16
        }
        """;

    String converted = FormConverter.convert(input);

    assertThat(converted).contains("\"id\": \"myForm\"");
    assertThat(converted).contains("\"components\": []");
    assertThat(converted).contains("\"type\": \"default\"");
    assertThat(converted).contains("\"schemaVersion\": 16");
  }

  @Test
  void shouldLeaveContentUnchangedIfFieldsAbsent() {
    String input =
        """
        {
          "id": "myForm",
          "components": [],
          "type": "default"
        }
        """;

    String converted = FormConverter.convert(input);

    assertThat(converted).isEqualTo(input);
  }

  @Test
  void shouldHandleAlreadyConvertedForm() {
    String input =
        """
        {
          "executionPlatform": "Camunda Cloud",
          "executionPlatformVersion": "8.9.0",
          "id": "myForm"
        }
        """;

    String converted = FormConverter.convert(input);

    assertThat(converted).contains("\"executionPlatform\": \"Camunda Cloud\"");
    assertThat(converted).contains("\"executionPlatformVersion\": \"8.9.0\"");
  }
}
