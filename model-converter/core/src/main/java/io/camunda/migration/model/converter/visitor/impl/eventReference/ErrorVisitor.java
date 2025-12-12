/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.eventReference;

import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.Convertible;
import io.camunda.migration.model.converter.convertible.ErrorConvertible;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.model.converter.expression.ExpressionTransformer;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractEventReferenceVisitor;

public class ErrorVisitor extends AbstractEventReferenceVisitor {
  @Override
  public String localName() {
    return "error";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new ErrorConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    String errorCode = context.getElement().getAttribute(BPMN, "errorCode");
    if (errorCode == null) {
      return;
    }
    ExpressionTransformationResult expressionTransformationResult =
        ExpressionTransformer.transformToFeel("Error", errorCode);
    if (expressionTransformationResult.result().startsWith("=")) {
      context.addMessage(MessageFactory.errorCodeNoExpression());
    }
    context.addConversion(
        ErrorConvertible.class, c -> c.setErrorCode(expressionTransformationResult.result()));
    // this can be enabled as soon as error codes can be expressions
    /*
    if (SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
        >= SemanticVersion._8_2_0.ordinal()) {
      String errorCode = context.getElement().getAttribute(BPMN, "errorCode");
      if (errorCode != null) {
        ExpressionTransformationResult expressionTransformationResult =
            ExpressionTransformer.transform(errorCode);
        if (!expressionTransformationResult
            .getNewExpression()
            .equals(expressionTransformationResult.getOldExpression())) {
          context.addConversion(
              ErrorConvertible.class,
              convertible ->
                  convertible.setErrorCode(expressionTransformationResult.getNewExpression()));
          context.addMessage(
              MessageFactory.errorCode(
                  expressionTransformationResult.getOldExpression(),
                  expressionTransformationResult.getNewExpression()));
        }
      }
    }*/
  }
}
