/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.BusinessRuleTaskConvertible;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractSupportedAttributeVisitor;

public class DecisionRefVersionTagVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "decisionRefVersionTag";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    if (SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
        >= SemanticVersion._8_6.ordinal()) {
      context.addConversion(
          BusinessRuleTaskConvertible.class,
          businessRuleTask -> businessRuleTask.getZeebeCalledDecision().setVersionTag(attribute));
      return MessageFactory.decisionRefVersionTag();
    } else {

      return MessageFactory.attributeNotSupported(
          attributeLocalName(), context.getElement().getLocalName(), attribute);
    }
  }
}
