/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.element;

import static io.camunda.migration.diagram.converter.NamespaceUri.CAMUNDA;
import static io.camunda.migration.diagram.converter.visitor.AbstractDelegateImplementationVisitor.*;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractExecutionListenerConvertible;
import io.camunda.migration.diagram.converter.convertible.AbstractExecutionListenerConvertible.ZeebeExecutionListener;
import io.camunda.migration.diagram.converter.convertible.AbstractExecutionListenerConvertible.ZeebeExecutionListener.EventType;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractListenerVisitor;
import io.camunda.migration.diagram.converter.visitor.AbstractListenerVisitor.ListenerImplementation.DelegateExpressionImplementation;
import java.util.List;
import java.util.regex.Matcher;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ExecutionListenerVisitor extends AbstractListenerVisitor {
  @Override
  public String localName() {
    return "executionListener";
  }

  @Override
  protected Message visitListener(
      DomElementVisitorContext context, String event, ListenerImplementation implementation) {
    if (isExecutionListenerSupported(
        SemanticVersion.parse(context.getProperties().getPlatformVersion()), event)) {
      ZeebeExecutionListener executionListener = new ZeebeExecutionListener();
      executionListener.setEventType(EventType.valueOf(event));
      if (implementation instanceof DelegateExpressionImplementation) {
        Matcher matcher = DELEGATE_NAME_EXTRACT.matcher(implementation.implementation());
        String delegateName = matcher.find() ? matcher.group(1) : implementation.implementation();
        executionListener.setListenerType(delegateName);
      } else {
        executionListener.setListenerType(implementation.implementation());
      }
      addStaticTaskHeaders(context, executionListener);
      context.addConversion(
          AbstractExecutionListenerConvertible.class,
          c -> c.addZeebeExecutionListener(executionListener));
      return MessageFactory.executionListenerSupported(event, implementation.implementation());
    }
    return MessageFactory.executionListenerNotSupported(
        event, ListenerImplementation.type(implementation), implementation.implementation());
  }

  private void addStaticTaskHeaders(
      DomElementVisitorContext context, ZeebeExecutionListener executionListener) {
    SemanticVersion version = SemanticVersion.parse(context.getProperties().getPlatformVersion());
    if (version.ordinal() < SemanticVersion._8_10.ordinal()) {
      return;
    }

    for (DomElement field : context.getElement().getChildElementsByNameNs(CAMUNDA, "field")) {
      String name = field.getAttribute("name");
      List<DomElement> children = field.getChildElements();
      if (name != null
          && !name.isBlank()
          && children.size() == 1
          && "string".equals(children.get(0).getLocalName())
          && CAMUNDA.equals(children.get(0).getNamespaceURI())) {
        executionListener.addZeebeTaskHeader(name, children.get(0).getTextContent());
        context.addMessage(MessageFactory.executionListenerField(name));
      }
    }
  }

  private boolean isExecutionListenerSupported(SemanticVersion version, String event) {
    return version.ordinal() >= SemanticVersion._8_6.ordinal() && isKnownEventType(event);
  }

  private boolean isKnownEventType(String event) {
    for (EventType eventType : EventType.values()) {
      if (eventType.name().equals(event)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return isExecutionListenerSupported(
        SemanticVersion.parse(context.getProperties().getPlatformVersion()),
        findEventName(context));
  }
}
