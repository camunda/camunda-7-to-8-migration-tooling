/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl.element;

import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.NamespaceUri;
import org.camunda.community.migration.converter.visitor.AbstractFilteringVisitor;

public class Camunda7NamespaceVisitor extends AbstractFilteringVisitor {

  @Override
  protected void visitFilteredElement(DomElementVisitorContext context) {
    context.addElementToRemove();
  }

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    return context.getElement().getNamespaceURI().equals(NamespaceUri.CAMUNDA);
  }

  @Override
  protected void logVisit(DomElementVisitorContext context) {
    // do not log at all
  }
}
