/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

final class PriorityLiteralExtractor {
  private static final Pattern FEEL_LITERAL =
      Pattern.compile(
          "(?:[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?|true|false|null|\"(?:\\\\.|[^\"\\\\])*\")");

  private PriorityLiteralExtractor() {}

  static String extract(ExpressionTransformationResult priority) {
    String result = StringUtils.trimToNull(priority.result());
    if (result == null) {
      return null;
    }

    if (result.equals(StringUtils.trimToEmpty(priority.juelExpression()))) {
      return result;
    }

    if (!result.startsWith("=")) {
      return null;
    }

    String feelLiteral = StringUtils.trimToNull(result.substring(1));
    if (feelLiteral == null || !FEEL_LITERAL.matcher(feelLiteral).matches()) {
      return null;
    }
    return feelLiteral;
  }
}
