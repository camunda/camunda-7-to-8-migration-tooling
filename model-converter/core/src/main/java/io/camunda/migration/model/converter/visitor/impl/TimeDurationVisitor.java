/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.AbstractCatchEventConvertible;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractTimerExpressionVisitor;

public class TimeDurationVisitor extends AbstractTimerExpressionVisitor {

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    if (isBoundaryEvent(context.getElement())
        || isIntermediateEvent(context.getElement())
        || isEventSubprocess(context.getElement())) {
      return SemanticVersion._8_0;
    }
    return null;
  }

  @Override
  protected void setNewExpression(AbstractCatchEventConvertible convertible, String newExpression) {
    convertible.setTimeDurationExpression(newExpression);
  }

  @Override
  public String localName() {
    return "timeDuration";
  }
}
