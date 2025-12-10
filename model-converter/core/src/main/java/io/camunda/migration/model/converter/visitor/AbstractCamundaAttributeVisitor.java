/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.NamespaceUri;
import java.util.List;

public abstract class AbstractCamundaAttributeVisitor extends AbstractAttributeVisitor {

  protected List<String> namespaceUri() {
    return List.of(NamespaceUri.CAMUNDA, NamespaceUri.CAMUNDA_DMN);
  }

  protected boolean removeAttribute(DomElementVisitorContext context) {
    return true;
  }

  @Override
  protected void logVisit(DomElementVisitorContext context) {
    LOG.debug(
        "Visiting attribute '{}:{}' on element '{}:{}'",
        namespaceUri(),
        attributeLocalName(),
        context.getElement().getPrefix(),
        context.getElement().getLocalName());
  }
}
