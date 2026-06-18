/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.ZeebeJobPriorityConvertible;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

final class JobPriorityWriter {
  private static final String LINK =
      "https://docs.camunda.io/docs/components/concepts/job-workers/#job-prioritization";

  private JobPriorityWriter() {}

  /**
   * Applies the Camunda 8 {@code zeebe:jobPriorityDefinition} conversion for a single C7 priority
   * value. JUEL expressions are transformed to FEEL like the other expression-carrying attributes
   * (dueDate, followUpDate, etc.). Emits the always-on "scales merged" REVIEW as a side message on
   * success and returns the primary outcome message — version-gate WARNING, invalid-literal TASK,
   * or the standard expression-transformation message — for the caller to attach. Returns an empty
   * message when nothing additional needs to be reported (literal value in range).
   */
  static Message apply(
      DomElementVisitorContext context,
      String attribute,
      String attributeName,
      String expressionContextLabel) {
    String elementLocalName = context.getElement().getLocalName();

    if (StringUtils.isBlank(attribute)) {
      return null;
    }

    SemanticVersion target = SemanticVersion.parse(context.getProperties().getPlatformVersion());
    if (target.ordinal() < SemanticVersion._8_10.ordinal()) {
      return MessageFactory.attributeNotSupported(attributeName, elementLocalName, attribute);
    }

    ExpressionTransformationResult priority =
        ExpressionTransformer.transformToFeel(expressionContextLabel, attribute);

    if (isInvalidLiteral(priority)) {
      return MessageFactory.priorityInvalid(elementLocalName, attribute);
    }

    context.addConversion(
        ZeebeJobPriorityConvertible.class,
        conv -> conv.getZeebeJobPriorityDefinition().setPriority(priority.result()));
    context.addMessage(MessageFactory.priorityScalesMerged());
    return ExpressionTransformationResultMessageFactory.getMessage(priority, LINK);
  }

  /**
   * A priority literal is invalid when it doesn't fit C8's int32 priority slot — either out of
   * range or not parseable as an integer at all. Expressions ({@code result != juelExpression})
   * always pass; we can't validate them at conversion time and the engine will evaluate them at
   * runtime.
   */
  private static boolean isInvalidLiteral(ExpressionTransformationResult priority) {
    if (priority.hasMethodInvocation() || priority.hasExecutionOnly()) {
      return false;
    }
    if (!Objects.equals(priority.result(), priority.juelExpression())) {
      return false;
    }
    try {
      long value = Long.parseLong(priority.result().trim());
      return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE;
    } catch (NumberFormatException e) {
      return true;
    }
  }
}
