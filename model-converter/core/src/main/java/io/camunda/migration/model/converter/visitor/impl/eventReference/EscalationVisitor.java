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
import io.camunda.migration.model.converter.convertible.EscalationConvertible;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.model.converter.expression.ExpressionTransformer;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractEventReferenceVisitor;

public class EscalationVisitor extends AbstractEventReferenceVisitor {
  @Override
  public String localName() {
    return "escalation";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new EscalationConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_2;
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    String escalationCode = context.getElement().getAttribute(BPMN, "escalationCode");
    if (escalationCode != null) {
      ExpressionTransformationResult expressionTransformationResult =
          ExpressionTransformer.transformToFeel("Escalation", escalationCode);
      context.addConversion(
          EscalationConvertible.class,
          c -> c.setEscalationCode(expressionTransformationResult.result()));
      if (expressionTransformationResult != null
          && expressionTransformationResult.result().startsWith("=")) {
        context.addMessage(MessageFactory.escalationCodeNoExpression());
      }
    }
    // this can be enabled as soon as escalation codes can be expressions
    /*
    String escalationCode = context.getElement().getAttribute(NamespaceUri.BPMN, "escalationCode");
    if (escalationCode != null) {

        ExpressionTransformationResult expressionTransformationResult =
            ExpressionTransformer.transform(escalationCode);
        if (!expressionTransformationResult
            .getNewExpression()
            .equals(expressionTransformationResult.getOldExpression())) {
          context.addConversion(
              EscalationConvertible.class,
              convertible ->
                  convertible.setEscalationCode(expressionTransformationResult.getNewExpression()));
          context.addMessage(
              MessageFactory.escalationCode(
                  expressionTransformationResult.getOldExpression(),
                  expressionTransformationResult.getNewExpression()));
      }
    }*/
  }
}
