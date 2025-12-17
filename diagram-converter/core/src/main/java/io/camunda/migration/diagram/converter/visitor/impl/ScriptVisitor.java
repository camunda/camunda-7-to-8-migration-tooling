/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractDataMapperConvertible;
import io.camunda.migration.diagram.converter.convertible.ScriptTaskConvertible;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractBpmnElementVisitor;
import io.camunda.migration.diagram.converter.visitor.impl.activity.ScriptTaskVisitor;

public class ScriptVisitor extends AbstractBpmnElementVisitor {

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  @Override
  public String localName() {
    return "script";
  }

  @Override
  protected void visitBpmnElement(DomElementVisitorContext context) {
    String script = context.getElement().getTextContent();
    if (ScriptTaskVisitor.canBeInternalScript(context)) {
      context.addConversion(
          ScriptTaskConvertible.class, convertible -> convertible.setExpression("=" + script));
      context.addMessage(MessageFactory.internalScript());
    } else {
      context.addConversion(
          AbstractDataMapperConvertible.class,
          convertible ->
              convertible.addZeebeTaskHeader(context.getProperties().getScriptHeader(), script));
      context.addMessage(MessageFactory.script());
    }
    context.addElementToRemove();
  }
}
