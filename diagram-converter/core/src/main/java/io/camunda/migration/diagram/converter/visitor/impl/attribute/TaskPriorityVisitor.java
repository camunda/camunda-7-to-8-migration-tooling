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
    // Some elements support taskPriority in Camunda 7 where the equivalent Camunda 8
    // element does not (e.g. messageEventDefinition).
    if (!JobPriorityScope.isEligible(context)) {
      return MessageFactory.priorityNotMigrated(
          context.getElement().getLocalName(), context.getElement().getAttribute("id"), attribute);
    }

    Message primary =
        JobPriorityWriter.apply(context, attribute, attributeLocalName(), "Task priority");

    String siblingJobPriority =
        context.getElement().getAttribute(NamespaceUri.CAMUNDA, "jobPriority");
    if (StringUtils.isNotBlank(siblingJobPriority)) {
      context.addMessage(
          MessageFactory.jobPriorityCollision(
              context.getElement().getLocalName(), siblingJobPriority, attribute));
    }
    return primary;
  }
}
