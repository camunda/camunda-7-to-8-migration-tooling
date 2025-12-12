/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.attribute;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.CallActivityConvertible;
import io.camunda.migration.model.converter.convertible.CallActivityConvertible.ZeebeCalledElement.ZeebeCalledElementBindingType;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractSupportedAttributeVisitor;

public class CalledElementBindingVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "calledElementBinding";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    if (SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
            >= SemanticVersion._8_6.ordinal()
        && !attribute.equals("version")) {
      context.addConversion(
          CallActivityConvertible.class,
          callActivity ->
              callActivity.getZeebeCalledElement().setBindingType(mapBindingType(attribute)));
      return MessageFactory.calledElementRefBinding();
    } else {

      return MessageFactory.attributeNotSupported(
          attributeLocalName(), context.getElement().getLocalName(), attribute);
    }
  }

  private ZeebeCalledElementBindingType mapBindingType(String attribute) {
    return switch (attribute) {
      case "versionTag" -> ZeebeCalledElementBindingType.versionTag;
      case "deployment" -> ZeebeCalledElementBindingType.deployment;
      default -> null;
    };
  }
}
