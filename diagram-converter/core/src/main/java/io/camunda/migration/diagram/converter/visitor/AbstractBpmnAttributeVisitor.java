/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.NamespaceUri;
import java.util.List;

public abstract class AbstractBpmnAttributeVisitor extends AbstractAttributeVisitor {
  @Override
  protected List<String> namespaceUri() {
    return List.of(NamespaceUri.BPMN);
  }

  @Override
  protected boolean removeAttribute(DomElementVisitorContext context) {
    return false;
  }
}
