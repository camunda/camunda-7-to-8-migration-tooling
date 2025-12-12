/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.message.MessageFactory;

public abstract class AbstractRemoveAttributeVisitor extends AbstractCamundaAttributeVisitor {

  @Override
  protected void visitAttribute(DomElementVisitorContext context, String attribute) {
    context.addMessage(
        MessageFactory.attributeRemoved(attributeLocalName(), context.getElement().getLocalName()));
  }
}
