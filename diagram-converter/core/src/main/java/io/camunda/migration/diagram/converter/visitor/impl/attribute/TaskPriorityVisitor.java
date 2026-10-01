/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.NamespaceUri;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractSupportedAttributeVisitor;
import org.apache.commons.lang3.StringUtils;

public class TaskPriorityVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "taskPriority";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    String elementLocalName = context.getElement().getLocalName();
    if ("userTask".equals(elementLocalName)) {
      String siblingJobPriority =
          context.getElement().getAttribute(NamespaceUri.CAMUNDA, "jobPriority");
      if (StringUtils.isNotBlank(siblingJobPriority)) {
        context.addMessage(
            MessageFactory.userTaskPriorityCollision(
                context.getElement().getAttribute("id"), siblingJobPriority, attribute));
      }
      return MessageFactory.userTaskPriorityNotMigrated(
          attributeLocalName(), context.getElement().getAttribute("id"), attribute);
    }
    return MessageFactory.attributeNotSupported(attributeLocalName(), elementLocalName, attribute);
  }
}
