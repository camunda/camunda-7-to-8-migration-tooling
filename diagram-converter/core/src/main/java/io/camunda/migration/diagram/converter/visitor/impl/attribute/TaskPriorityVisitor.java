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
    String siblingJobPriority =
        context.getElement().getAttribute(NamespaceUri.CAMUNDA, "jobPriority");

    // Some elements support taskPriority in Camunda 7 where the equivalent Camunda 8
    // element does not (e.g. messageEventDefinition).
    if (!JobPriorityScope.isEligible(context)) {
      if ("userTask".equals(elementLocalName)) {
        if (StringUtils.isNotBlank(siblingJobPriority)) {
          context.addMessage(
              MessageFactory.userTaskPriorityCollision(
                  context.getElement().getAttribute("id"), siblingJobPriority, attribute));
        }
        return MessageFactory.userTaskPriorityNotMigrated(
            attributeLocalName(), context.getElement().getAttribute("id"), attribute);
      }
      return MessageFactory.priorityNotMigrated(
          elementLocalName, context.getElement().getAttribute("id"), attribute);
    }

    Message primary =
        JobPriorityWriter.apply(context, attribute, attributeLocalName(), "Task priority");

    if (StringUtils.isNotBlank(siblingJobPriority)) {
      context.addMessage(
          MessageFactory.jobPriorityCollision(elementLocalName, siblingJobPriority, attribute));
    }
    return primary;
  }
}
