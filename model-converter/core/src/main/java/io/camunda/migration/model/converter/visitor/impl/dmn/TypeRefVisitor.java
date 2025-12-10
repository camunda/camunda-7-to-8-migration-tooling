/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.dmn;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.AbstractTypeRefConvertible;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.visitor.AbstractDmnAttributeVisitor;
import java.util.List;

public class TypeRefVisitor extends AbstractDmnAttributeVisitor {
  private static final List<String> NUMBER_TYPES = List.of("double", "long");

  @Override
  public String attributeLocalName() {
    return "typeRef";
  }

  @Override
  protected void visitAttribute(DomElementVisitorContext context, String attribute) {
    if (NUMBER_TYPES.contains(attribute)) {
      context.addConversion(AbstractTypeRefConvertible.class, c -> c.setTypeRef("number"));
      context.addMessage(MessageFactory.numberType());
    }
  }
}
