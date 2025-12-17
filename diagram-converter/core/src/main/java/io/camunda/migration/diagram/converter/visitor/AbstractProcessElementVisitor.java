/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.Convertible;

public abstract class AbstractProcessElementVisitor extends AbstractBpmnElementVisitor {
  @Override
  protected final void visitBpmnElement(DomElementVisitorContext context) {
    context.setAsDiagramElement(createConvertible(context));
    postCreationVisitor(context);
  }

  protected abstract Convertible createConvertible(DomElementVisitorContext context);

  protected void postCreationVisitor(DomElementVisitorContext context) {
    // do nothing
  }
}
