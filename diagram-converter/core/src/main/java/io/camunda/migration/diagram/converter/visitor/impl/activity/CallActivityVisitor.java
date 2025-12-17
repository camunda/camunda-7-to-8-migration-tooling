/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.activity;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.NamespaceUri;
import io.camunda.migration.diagram.converter.convertible.CallActivityConvertible;
import io.camunda.migration.diagram.converter.convertible.Convertible;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractActivityVisitor;

public class CallActivityVisitor extends AbstractActivityVisitor {

  public static final String CALLED_ELEMENT = "calledElement";

  @Override
  public String localName() {
    return "callActivity";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new CallActivityConvertible();
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel(
            "Called process", context.getElement().getAttribute(NamespaceUri.BPMN, CALLED_ELEMENT));
    if (transformationResult != null) {
      context.addConversion(
          CallActivityConvertible.class,
          conversion ->
              conversion.getZeebeCalledElement().setProcessId(transformationResult.result()));
      context.addMessage(
          ExpressionTransformationResultMessageFactory.getMessage(
              transformationResult,
              "https://docs.camunda.io/docs/components/modeler/bpmn/call-activities/#defining-the-called-process"));
    }
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }
}
