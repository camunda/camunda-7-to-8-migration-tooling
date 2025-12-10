/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.element;

import static io.camunda.migration.model.converter.visitor.AbstractDelegateImplementationVisitor.DELEGATE_NAME_EXTRACT;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.UserTaskConvertible;
import io.camunda.migration.model.converter.convertible.UserTaskConvertible.ZeebeTaskListener;
import io.camunda.migration.model.converter.convertible.UserTaskConvertible.ZeebeTaskListener.EventType;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor;
import io.camunda.migration.model.converter.visitor.AbstractListenerVisitor.ListenerImplementation.DelegateExpressionImplementation;
import java.util.Optional;
import java.util.regex.Matcher;

public class TaskListenerVisitor extends AbstractListenerVisitor {
  @Override
  public String localName() {
    return "taskListener";
  }

  @Override
  protected Message visitListener(
      DomElementVisitorContext context, String event, ListenerImplementation implementation) {
    if (isTaskListenerSupported(context)) {
      ZeebeTaskListener taskListener = new ZeebeTaskListener();
      taskListener.setEventType(EventType.fromName(event).get());

      if (implementation instanceof DelegateExpressionImplementation) {
        Matcher matcher = DELEGATE_NAME_EXTRACT.matcher(implementation.implementation());
        String delegateName = matcher.find() ? matcher.group(1) : implementation.implementation();
        taskListener.setListenerType(delegateName);
      } else {
        taskListener.setListenerType(implementation.implementation());
      }
      context.addConversion(UserTaskConvertible.class, c -> c.addZeebeTaskListener(taskListener));
      return MessageFactory.taskListenerSupported(event, implementation.implementation());
    }
    return MessageFactory.taskListenerNotSupported(
        event, ListenerImplementation.type(implementation), implementation.implementation());
  }

  private boolean isTaskListenerSupported(DomElementVisitorContext context) {
    Optional<EventType> eventType = EventType.fromName(findEventName(context));
    SemanticVersion semanticVersion =
        SemanticVersion.parse(context.getProperties().getPlatformVersion());
    return (semanticVersion.ordinal() >= SemanticVersion._8_8.ordinal())
        && eventType.isPresent()
        && eventType.get().isMapped();
  }

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return isTaskListenerSupported(context);
  }
}
