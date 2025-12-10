/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.example.extendedConverter;

import org.camunda.community.migration.converter.DiagramCheckResult.Severity;
import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.convertible.ServiceTaskConvertible;
import org.camunda.community.migration.converter.message.ComposedMessage;
import org.camunda.community.migration.converter.message.Message;
import org.camunda.community.migration.converter.visitor.AbstractSupportedAttributeVisitor;

public class TaskTopicVisitor extends AbstractSupportedAttributeVisitor {

  @Override
  public String attributeLocalName() {
    return "topic";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    context.addConversion(
        ServiceTaskConvertible.class,
        serviceTaskConversion ->
            serviceTaskConversion.addZeebeTaskHeader(attributeLocalName(), attribute));
    context.addConversion(
        ServiceTaskConvertible.class,
        serviceTaskConversion ->
            serviceTaskConversion.getZeebeTaskDefinition().setType("GenericWorker"));
    ComposedMessage composedMessage = new ComposedMessage();
    composedMessage.setMessage("Tasktopic has been transformed: " + attribute);
    composedMessage.setSeverity(Severity.INFO);
    composedMessage.setLink("Link");
    return composedMessage;
  }
}
