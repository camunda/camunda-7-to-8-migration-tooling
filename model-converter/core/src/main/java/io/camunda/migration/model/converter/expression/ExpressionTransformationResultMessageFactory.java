/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.expression;

import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import java.util.Objects;

public class ExpressionTransformationResultMessageFactory {
  public static Message getMessage(
      ExpressionTransformationResult transformationResult, String link) {
    // no transformation has happened (because the expression is not an expression)
    if (Objects.equals(transformationResult.result(), transformationResult.juelExpression())) {
      return MessageFactory.noExpressionTransformation();
    }
    // check for execution reference

    if (transformationResult.hasExecutionOnly()) {
      return MessageFactory.expressionExecutionNotAvailable(
          transformationResult.context(),
          transformationResult.juelExpression(),
          transformationResult.result(),
          link);

    } else
    // check for method invocation
    if (transformationResult.hasMethodInvocation()) {
      return MessageFactory.expressionMethodNotPossible(
          transformationResult.context(),
          transformationResult.juelExpression(),
          transformationResult.result(),
          link);
    } else {
      // if all is good, just give the default message
      return MessageFactory.expression(
          transformationResult.context(),
          transformationResult.juelExpression(),
          transformationResult.result(),
          link);
    }
  }
}
