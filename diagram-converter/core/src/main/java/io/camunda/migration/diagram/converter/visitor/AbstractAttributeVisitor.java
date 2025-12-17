/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor;

import static io.camunda.migration.diagram.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractAttributeVisitor extends AbstractFilteringVisitor {
  @Override
  protected void visitFilteredElement(DomElementVisitorContext context) {
    Attribute attribute = resolveAttribute(context);
    visitAttribute(context, attribute.value());
    if (removeAttribute(context) && attribute.namespaceUri() != null) {
      context.addAttributeToRemove(attributeLocalName(), attribute.namespaceUri());
    }
  }

  private Attribute resolveAttribute(DomElementVisitorContext context) {
    for (String namespaceUri : namespaceUri()) {
      String attribute = context.getElement().getAttribute(namespaceUri, attributeLocalName());
      if (attribute != null) {
        return new Attribute(namespaceUri, attributeLocalName(), attribute);
      }
    }
    return new Attribute(null, attributeLocalName(), null);
  }

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    return resolveAttribute(context).value() != null
        && (context.getElement().getNamespaceURI().equals(BPMN)
            || Arrays.asList(DMN).contains(context.getElement().getNamespaceURI()));
  }

  protected abstract List<String> namespaceUri();

  public abstract String attributeLocalName();

  protected abstract void visitAttribute(DomElementVisitorContext context, String attribute);

  protected abstract boolean removeAttribute(DomElementVisitorContext context);

  @Override
  protected void logVisit(DomElementVisitorContext context) {
    LOG.debug(
        "Visiting attribute 'camunda:{}' on element '{}:{}'",
        attributeLocalName(),
        context.getElement().getPrefix(),
        context.getElement().getLocalName());
  }

  private record Attribute(String namespaceUri, String attributeLocalName, String value) {}
}
