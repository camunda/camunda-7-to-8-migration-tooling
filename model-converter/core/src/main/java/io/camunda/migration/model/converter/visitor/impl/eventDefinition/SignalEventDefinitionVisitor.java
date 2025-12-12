/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.eventDefinition;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractEventDefinitionVisitor;

public class SignalEventDefinitionVisitor extends AbstractEventDefinitionVisitor {
  @Override
  public String localName() {
    return "signalEventDefinition";
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    if (isStartEvent(context) && isNotEventSubProcessStartEvent(context.getElement())) {
      return SemanticVersion._8_2;
    }
    return SemanticVersion._8_3;
  }

  private boolean isStartEvent(DomElementVisitorContext context) {
    return context.getElement().getParentElement().getLocalName().equals("startEvent");
  }
}
