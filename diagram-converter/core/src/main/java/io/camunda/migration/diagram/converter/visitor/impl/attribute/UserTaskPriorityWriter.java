/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.UserTaskConvertible;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

final class UserTaskPriorityWriter {
  private static final String LINK =
      "https://docs.camunda.io/docs/components/modeler/bpmn/user-tasks/#define-user-task-priority";

  private UserTaskPriorityWriter() {}

  static Message apply(DomElementVisitorContext context, String attribute) {
    if (StringUtils.isBlank(attribute)) {
      return null;
    }

    SemanticVersion target = SemanticVersion.parse(context.getProperties().getPlatformVersion());
    if (target.ordinal() < SemanticVersion._8_6.ordinal()) {
      return MessageFactory.attributeNotSupported(
          "priority", context.getElement().getLocalName(), attribute);
    }

    ExpressionTransformationResult priority =
        ExpressionTransformer.transformToFeel("User task priority", attribute);
    if (isInvalidLiteral(priority)) {
      return MessageFactory.priorityInvalid(context.getElement().getLocalName(), attribute);
    }

    if (priority.hasMethodInvocation() || priority.hasExecutionOnly()) {
      return ExpressionTransformationResultMessageFactory.getMessage(priority, LINK);
    }

    context.addConversion(
        UserTaskConvertible.class,
        convertible -> convertible.setZeebeUserTaskPriority(priority.result()));
    return ExpressionTransformationResultMessageFactory.getMessage(priority, LINK);
  }

  private static boolean isInvalidLiteral(ExpressionTransformationResult priority) {
    if (priority.hasMethodInvocation() || priority.hasExecutionOnly()) {
      return false;
    }
    if (!Objects.equals(priority.result(), priority.juelExpression())) {
      return false;
    }
    try {
      int value = Integer.parseInt(priority.result().trim());
      return value < 0 || value > 100;
    } catch (NumberFormatException e) {
      return true;
    }
  }
}
