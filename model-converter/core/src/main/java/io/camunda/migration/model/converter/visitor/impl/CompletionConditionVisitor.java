/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.AbstractActivityConvertible;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.model.converter.expression.ExpressionTransformer;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractBpmnElementVisitor;

public class CompletionConditionVisitor extends AbstractBpmnElementVisitor {

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  @Override
  protected void visitBpmnElement(DomElementVisitorContext context) {
    String textContent = context.getElement().getTextContent();
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel("Completion condition", textContent);
    context.addConversion(
        AbstractActivityConvertible.class,
        conversion ->
            conversion
                .getBpmnMultiInstanceLoopCharacteristics()
                .setCompletionCondition(transformationResult.result()));
    context.addMessage(
        ExpressionTransformationResultMessageFactory.getMessage(
            transformationResult,
            "https://docs.camunda.io/docs/components/modeler/bpmn/multi-instance/#completion-condition"));
  }

  @Override
  public String localName() {
    return "completionCondition";
  }
}
