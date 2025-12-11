/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor;

import java.util.List;
import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.NamespaceUri;

public abstract class AbstractBpmnElementVisitor extends AbstractElementVisitor {
  @Override
  protected final List<String> namespaceUri() {
    return List.of(NamespaceUri.BPMN);
  }

  @Override
  protected void visitElement(DomElementVisitorContext context) {
    visitBpmnElement(context);
  }

  protected abstract void visitBpmnElement(DomElementVisitorContext context);
}
