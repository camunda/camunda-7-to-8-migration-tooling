/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.eventDefinition;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractEventDefinitionVisitor;

public class CompensateEventDefinitionVisitor extends AbstractEventDefinitionVisitor {
  @Override
  public String localName() {
    return "compensateEventDefinition";
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    if (isNotEventSubProcessStartEvent(context.getElement())) {
      return SemanticVersion._8_5;
    }
    return null;
  }
}
