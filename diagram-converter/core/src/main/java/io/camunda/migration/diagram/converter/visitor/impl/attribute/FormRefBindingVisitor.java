/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.FormDefinitionConvertible;
import io.camunda.migration.diagram.converter.convertible.UserTaskConvertible.ZeebeFormDefinition.ZeebeFormDefinitionBindingType;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractSupportedAttributeVisitor;

public class FormRefBindingVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "formRefBinding";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    if (SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
            >= SemanticVersion._8_6.ordinal()
        && !attribute.equals("version")) {
      context.addConversion(
          FormDefinitionConvertible.class,
          userTask -> userTask.getZeebeFormDefinition().setBindingType(mapBindingType(attribute)));
      return MessageFactory.formRefBinding();
    } else {

      return MessageFactory.attributeNotSupported(
          attributeLocalName(), context.getElement().getLocalName(), attribute);
    }
  }

  private ZeebeFormDefinitionBindingType mapBindingType(String attribute) {
    if (attribute.equals("deployment")) {
      return ZeebeFormDefinitionBindingType.deployment;
    }
    return null;
  }
}
