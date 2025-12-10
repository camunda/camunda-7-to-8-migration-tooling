/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl.element;

import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.convertible.AbstractProcessElementConvertible;
import org.camunda.community.migration.converter.message.Message;
import org.camunda.community.migration.converter.message.MessageFactory;
import org.camunda.community.migration.converter.visitor.AbstractCamundaElementVisitor;

public class PropertyVisitor extends AbstractCamundaElementVisitor {
  @Override
  public String localName() {
    return "property";
  }

  @Override
  protected Message visitCamundaElement(DomElementVisitorContext context) {
    String name = context.getElement().getAttribute("name");
    String value = context.getElement().getAttribute("value");
    context.addConversion(
        AbstractProcessElementConvertible.class,
        conversion -> conversion.addZeebeProperty(name, value));
    return MessageFactory.property(context.getElement().getLocalName(), name);
  }

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return true;
  }
}
