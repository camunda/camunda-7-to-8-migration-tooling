/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.diagram.converter.DiagramCheckResult.Severity;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.message.Message;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExpressionTransformationResultMessageFactoryTest {

  @Test
  void shouldReturnExecutionMessageWhenExpressionReferencesExecution() {
    ExpressionTransformationResult result =
        new ExpressionTransformationResult(
            "Job priority",
            "${execution.getProcessInstanceId()}",
            "=execution.getProcessInstanceId()",
            false,
            true);

    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(result, "https://example.test");

    assertThat(message.getId()).isEqualTo("expression-execution-not-available");
    assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
  }

  @Test
  void shouldReturnMethodMessageWhenExpressionHasMethodInvocation() {
    ExpressionTransformationResult result =
        new ExpressionTransformationResult(
            "Job priority", "${order.getPriority()}", "=order.getPriority()", true, false);

    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(result, "https://example.test");

    assertThat(message.getId()).isEqualTo("expression-method-not-possible");
    assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
  }

  @Test
  void shouldPreferExecutionMessageWhenBothFlagsAreSet() {
    ExpressionTransformationResult result =
        new ExpressionTransformationResult(
            "Job priority",
            "${execution.order.getPriority()}",
            "=execution.order.getPriority()",
            true,
            true);

    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(result, "https://example.test");

    assertThat(message.getId()).isEqualTo("expression-execution-not-available");
    assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
  }

  @Test
  void shouldReturnEmptyMessageForStaticExpressionWithoutTransformation() {
    ExpressionTransformationResult result =
        new ExpressionTransformationResult(
            "Job priority", "static-value", "static-value", false, false);

    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(result, "https://example.test");

    assertThat(message.getId()).isEmpty();
  }

  @Test
  void shouldReturnReviewExpressionMessageWhenTransformationSucceeds() {
    ExpressionTransformationResult result =
        new ExpressionTransformationResult(
            "Job priority", "${jobPriority}", "=jobPriority", false, false);

    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(result, "https://example.test");

    assertThat(message.getId()).isEqualTo("expression");
    assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
  }

  @ParameterizedTest
  @MethodSource("failedFeelTransformations")
  void shouldReportFailedFeelTransformationsAsTasks(
      ExpressionTransformationResult transformationResult) {
    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(
            transformationResult, "https://example.test");

    assertThat(message.getSeverity()).isEqualTo(Severity.TASK);
  }

  @ParameterizedTest
  @MethodSource("successfulFeelTransformations")
  void shouldReportSuccessfulFeelTransformationsAsReviews(
      ExpressionTransformationResult transformationResult) {
    Message message =
        ExpressionTransformationResultMessageFactory.getMessage(
            transformationResult, "https://example.test");

    assertThat(message.getSeverity()).isEqualTo(Severity.REVIEW);
  }

  private static Stream<ExpressionTransformationResult> failedFeelTransformations() {
    return Stream.of(
        ExpressionTransformer.transformToFeel("BPMN expression", "${order.getPriority()}"),
        ExpressionTransformer.transformToFeel(
            "BPMN expression", "${execution.getProcessInstanceId()}"),
        ExpressionTransformer.transformToFeelDmn("DMN expression", "${order.getPriority()}"),
        ExpressionTransformer.transformToFeelDmn(
            "DMN expression", "${execution.getProcessInstanceId()}"));
  }

  private static Stream<ExpressionTransformationResult> successfulFeelTransformations() {
    return Stream.of(
        ExpressionTransformer.transformToFeel("BPMN expression", "${customerName}"),
        ExpressionTransformer.transformToFeelDmn("DMN expression", "${customerName}"));
  }
}
