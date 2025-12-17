/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.event;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractExecutionListenerConvertible.ZeebeExecutionListener;
import io.camunda.migration.diagram.converter.convertible.AbstractExecutionListenerConvertible.ZeebeExecutionListener.EventType;
import io.camunda.migration.diagram.converter.convertible.Convertible;
import io.camunda.migration.diagram.converter.convertible.StartEventConvertible;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractEventVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public class StartEventVisitor extends AbstractEventVisitor {
  public static boolean isBlankProcessStartEvent(DomElement element) {
    if (!element.getParentElement().getLocalName().equals("process")) {
      return false;
    }
    if (!"startEvent".equals(element.getLocalName())) {
      return false;
    }

    // Look for any child element ending in EventDefinition (message, timer, signal, etc.)
    for (DomElement child : element.getChildElements()) {
      String localName = child.getLocalName();
      if (localName != null && localName.endsWith("EventDefinition")) {
        return false; // not blank
      }
    }

    return true; // blank: no event definitions
  }

  @Override
  public String localName() {
    return "startEvent";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new StartEventConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    if (context.getProperties().getAddDataMigrationExecutionListener()
        && isBlankProcessStartEvent(context.getElement())) {
      ZeebeExecutionListener executionListener = new ZeebeExecutionListener();
      executionListener.setEventType(EventType.end);
      executionListener.setListenerType(
          context.getProperties().getDataMigrationExecutionListenerJobType());
      context.addConversion(
          StartEventConvertible.class, s -> s.getZeebeExecutionListeners().add(executionListener));
      context.addMessage(
          MessageFactory.dataMigrationStartListenerAdded(
              executionListener.getListenerType(), context.getElement().getLocalName()));
    }
  }
}
