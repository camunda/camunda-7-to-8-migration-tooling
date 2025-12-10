/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl.attribute;

import static org.camunda.community.migration.converter.NamespaceUri.*;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.convertible.AbstractDataMapperConvertible;
import org.camunda.community.migration.converter.message.Message;
import org.camunda.community.migration.converter.message.MessageFactory;
import org.camunda.community.migration.converter.visitor.AbstractSupportedAttributeVisitor;

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
