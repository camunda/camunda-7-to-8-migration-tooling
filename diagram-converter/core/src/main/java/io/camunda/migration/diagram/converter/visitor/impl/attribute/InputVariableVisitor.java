/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractCamundaAttributeVisitor;

public class InputVariableVisitor extends AbstractCamundaAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "inputVariable";
  }

  @Override
  protected void visitAttribute(DomElementVisitorContext context, String attribute) {
    context.addMessage(MessageFactory.inputVariableNotSupported());
  }
}
