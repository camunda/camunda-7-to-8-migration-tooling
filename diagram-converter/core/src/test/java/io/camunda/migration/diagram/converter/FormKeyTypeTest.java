/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormKeyTypeTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "embedded:app:forms/loan-approval.html",
        "embedded:deployment:forms/loan-approval.html",
        "embedded:deployment:form.html",
        // a dynamic location does not change the form type
        "embedded:app:forms/${formName}.html",
        "embedded:deployment:forms/#{formName}.html"
      })
  void shouldClassifyEmbeddedTaskForms(String formKey) {
    assertThat(FormKeyType.of(formKey)).isEqualTo(FormKeyType.EMBEDDED);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "camunda-forms:deployment:forms/userTask.form",
        "camunda-forms:app:forms/userTask.form",
        "camunda-forms:deployment:${formName}.form"
      })
  void shouldClassifyCamundaFormReferences(String formKey) {
    assertThat(FormKeyType.of(formKey)).isEqualTo(FormKeyType.CAMUNDA_FORM);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "${formKey}",
        "#{formKey}",
        "${formType}:app:forms/loan.html",
        "app:forms/${formName}.html"
      })
  void shouldClassifyFormKeysWithoutAResolvableFormType(String formKey) {
    assertThat(FormKeyType.of(formKey)).isEqualTo(FormKeyType.EXPRESSION);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://my-app/forms/loan",
        "https://my-app.example.com/forms/loan?taskId=1",
        "app:forms/loan-approval.html",
        "deployment:forms/loan-approval.html",
        // a bare .html reference is not evidence of an embedded form
        "forms/loan-approval.html",
        "loanApprovalForm",
        // Camunda 7 matches the form type prefix exactly, so a different case is not a form type
        "Embedded:app:forms/loan.html",
        "CAMUNDA-FORMS:deployment:loan.form"
      })
  void shouldClassifyEverythingElseAsExternal(String formKey) {
    assertThat(FormKeyType.of(formKey)).isEqualTo(FormKeyType.EXTERNAL);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {"null", "''", "'   '"})
  void shouldClassifyAbsentFormKeysAsExternal(String formKey) {
    assertThat(FormKeyType.of(formKey)).isEqualTo(FormKeyType.EXTERNAL);
  }

  @Test
  void shouldIgnoreSurroundingWhitespace() {
    assertThat(FormKeyType.of("  embedded:app:forms/loan.html  ")).isEqualTo(FormKeyType.EMBEDDED);
  }

  @ParameterizedTest
  @EnumSource(FormKeyType.class)
  void shouldProduceADistinctManualTaskFindingForEveryFormType(FormKeyType formKeyType) {
    Message message = MessageFactory.formKey("formKey", "userTask", "someFormKey", formKeyType);

    assertThat(message.getMessage()).contains("someFormKey");
    assertThat(message.getSeverity()).isEqualTo(DiagramCheckResult.Severity.TASK);
    assertThat(message.getLink()).isNotBlank();
  }

  @Test
  void shouldNotShareAFindingBetweenFormTypes() {
    assertThat(
            Arrays.stream(FormKeyType.values())
                .map(type -> MessageFactory.formKey("formKey", "userTask", "someFormKey", type))
                .map(Message::getMessage)
                .distinct()
                .count())
        .isEqualTo(FormKeyType.values().length);
  }
}
