/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import java.util.Objects;

final class PriorityRangeValidator {
  private PriorityRangeValidator() {}

  /**
   * Returns a TASK message if the priority resolves to a literal long value that doesn't fit into
   * C8's int32 priority slot. Returns {@code null} for expressions (can't be validated at
   * conversion time), for in-range numeric literals, and for non-numeric literals (malformed C7,
   * passed through unchanged).
   */
  static Message outOfRangeOrNull(
      ExpressionTransformationResult priority, String elementLocalName) {
    boolean isLiteral = Objects.equals(priority.result(), priority.juelExpression());
    if (!isLiteral) {
      return null;
    }
    try {
      long value = Long.parseLong(priority.result().trim());
      if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
        return MessageFactory.priorityOutOfRange(elementLocalName, priority.juelExpression());
      }
    } catch (NumberFormatException ignored) {
      // Non-numeric literal — malformed C7 input. Preserve existing pass-through behavior.
    }
    return null;
  }
}
