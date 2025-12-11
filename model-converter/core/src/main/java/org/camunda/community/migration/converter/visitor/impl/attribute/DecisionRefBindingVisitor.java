/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl.attribute;

import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.convertible.BusinessRuleTaskConvertible;
import org.camunda.community.migration.converter.convertible.BusinessRuleTaskConvertible.ZeebeCalledDecision.ZeebeCalledDecisionBindingType;
import org.camunda.community.migration.converter.message.Message;
import org.camunda.community.migration.converter.message.MessageFactory;
import org.camunda.community.migration.converter.version.SemanticVersion;
import org.camunda.community.migration.converter.visitor.AbstractSupportedAttributeVisitor;

public class DecisionRefBindingVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "decisionRefBinding";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    if (SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
            >= SemanticVersion._8_6.ordinal()
        && !attribute.equals("version")) {
      context.addConversion(
          BusinessRuleTaskConvertible.class,
          businessRuleTask ->
              businessRuleTask.getZeebeCalledDecision().setBindingType(mapBindingType(attribute)));
      return MessageFactory.decisionRefBinding();
    } else {

      return MessageFactory.attributeNotSupported(
          attributeLocalName(), context.getElement().getLocalName(), attribute);
    }
  }

  private ZeebeCalledDecisionBindingType mapBindingType(String attribute) {
    return switch (attribute) {
      case "versionTag" -> ZeebeCalledDecisionBindingType.versionTag;
      case "deployment" -> ZeebeCalledDecisionBindingType.deployment;
      default -> null;
    };
  }
}
