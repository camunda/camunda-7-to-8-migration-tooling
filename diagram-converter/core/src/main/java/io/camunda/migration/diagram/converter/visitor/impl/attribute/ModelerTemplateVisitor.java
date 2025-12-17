/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.attribute;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.AbstractActivityConvertible;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.visitor.AbstractSupportedAttributeVisitor;

public class ModelerTemplateVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    context.addConversion(
        AbstractActivityConvertible.class, c -> c.setZeebeModelerTemplate(attribute));
    return MessageFactory.modelerTemplate();
  }

  @Override
  public String attributeLocalName() {
    return "modelerTemplate";
  }
}
