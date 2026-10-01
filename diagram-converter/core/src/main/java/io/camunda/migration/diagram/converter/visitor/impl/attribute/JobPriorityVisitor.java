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
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractRemoveAttributeVisitor;
import org.apache.commons.lang3.StringUtils;

public class JobPriorityVisitor extends AbstractRemoveAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "jobPriority";
  }

  @Override
  protected void visitAttribute(DomElementVisitorContext context, String attribute) {
    if ("userTask".equals(context.getElement().getLocalName())) {
      String siblingTaskPriority =
          context.getElement().getAttribute(NamespaceUri.CAMUNDA, "taskPriority");
      if (StringUtils.isNotBlank(siblingTaskPriority)) {
        return;
      }
      context.addMessage(
          MessageFactory.userTaskPriorityNotMigrated(
              attributeLocalName(), context.getElement().getAttribute("id"), attribute));
      return;
    }
    super.visitAttribute(context, attribute);
  }
}
