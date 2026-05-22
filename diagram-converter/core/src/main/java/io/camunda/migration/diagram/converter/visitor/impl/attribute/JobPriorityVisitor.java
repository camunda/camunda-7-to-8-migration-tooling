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

public class JobPriorityVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "jobPriority";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    // TaskPriorityVisitor is responsible when both attributes are present — it wins the collision
    // and emits the collision REVIEW message. Drop out silently here to avoid double-handling.
    String sibling = context.getElement().getAttribute(NamespaceUri.CAMUNDA, "taskPriority");
    if (StringUtils.isNotBlank(sibling)) {
      return null;
    }

    // Handle elements for which job priority is not supported in Camunda 8
    if (!JobPriorityScope.isEligible(context)) {
      return MessageFactory.priorityNotMigrated(
          context.getElement().getLocalName(), context.getElement().getAttribute("id"), attribute);
    }

    return JobPriorityWriter.apply(context, attribute, attributeLocalName(), "Job priority");
  }
}
