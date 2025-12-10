/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl;

import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.message.MessageFactory;
import org.camunda.community.migration.converter.version.SemanticVersion;
import org.camunda.community.migration.converter.visitor.AbstractBpmnAttributeVisitor;

public class IsForCompensationVisitor extends AbstractBpmnAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "isForCompensation";
  }

  @Override
  protected void visitAttribute(DomElementVisitorContext context, String attribute) {
    if (Boolean.parseBoolean(attribute)
        && SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
            < SemanticVersion._8_5.ordinal()) {
      context.addMessage(
          MessageFactory.elementNotSupportedHint(
              "Compensation Task", context.getProperties().getPlatformVersion()));
    }
  }
}
