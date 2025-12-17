/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractCatchEventConvertible;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractTimerExpressionVisitor;

public class TimeCycleVisitor extends AbstractTimerExpressionVisitor {
  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    if (isStartEvent(context.getElement())
        || isNonInterruptingIntermediate(context.getElement())
        || isNonInterruptingStart(context.getElement())) {
      return SemanticVersion._8_0;
    }
    return null;
  }

  @Override
  protected void setNewExpression(AbstractCatchEventConvertible convertible, String newExpression) {
    convertible.setTimeCycleExpression(newExpression);
  }

  @Override
  public String localName() {
    return "timeCycle";
  }
}
