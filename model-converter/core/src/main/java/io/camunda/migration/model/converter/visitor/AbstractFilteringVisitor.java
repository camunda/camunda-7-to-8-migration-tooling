/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.exception.VisitorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractFilteringVisitor implements DomElementVisitor {
  protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

  @Override
  public void visit(DomElementVisitorContext context) {
    try {
      if (canVisit(context)) {
        logVisit(context);
        visitFilteredElement(context);
      }
    } catch (Exception e) {
      VisitorException visitorException =
          new VisitorException(this.getClass(), context.getElement(), e);
      context.notify(visitorException);
      LOG.error("Exception while visiting an element", visitorException);
    }
  }

  protected abstract void visitFilteredElement(DomElementVisitorContext context);

  protected abstract boolean canVisit(DomElementVisitorContext context);

  protected void logVisit(DomElementVisitorContext context) {
    LOG.debug(
        "Visiting {}:{}", context.getElement().getPrefix(), context.getElement().getLocalName());
  }
}
