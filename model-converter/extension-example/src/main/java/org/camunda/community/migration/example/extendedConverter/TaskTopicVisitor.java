/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.example.extendedConverter;

import io.camunda.migration.model.converter.DiagramCheckResult.Severity;
import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.model.converter.message.ComposedMessage;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.visitor.AbstractSupportedAttributeVisitor;

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
