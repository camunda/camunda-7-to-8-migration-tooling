/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import static io.camunda.migration.diagram.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractDataMapperConvertible;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractSupportedAttributeVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ResourceVisitor extends AbstractSupportedAttributeVisitor {

  @Override
  public String attributeLocalName() {
    return "resource";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    if (isSequenceFlow(context.getElement())) {
      return null;
    }
    context.addConversion(
        AbstractDataMapperConvertible.class,
        convertible ->
            convertible.addZeebeTaskHeader(context.getProperties().getResourceHeader(), attribute));
    return MessageFactory.resource(
        attributeLocalName(),
        context.getElement().getLocalName(),
        context.getProperties().getResourceHeader());
  }

  private boolean isSequenceFlow(DomElement element) {
    if (element == null) {
      return false;
    }
    return element.getLocalName().equals("sequenceFlow") && element.getNamespaceURI().equals(BPMN)
        || isSequenceFlow(element.getParentElement());
  }
}
