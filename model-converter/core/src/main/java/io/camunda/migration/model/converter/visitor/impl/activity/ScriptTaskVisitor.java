/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.activity;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.NamespaceUri;
import io.camunda.migration.model.converter.convertible.Convertible;
import io.camunda.migration.model.converter.convertible.ScriptTaskConvertible;
import io.camunda.migration.model.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractActivityVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ScriptTaskVisitor extends AbstractActivityVisitor {

  public static boolean canBeInternalScript(DomElementVisitorContext context) {
    DomElement element = context.getElement();
    while (!element.getLocalName().equals("scriptTask")
        && !element.getRootElement().equals(element)) {
      element = element.getParentElement();
    }
    String scriptFormat = element.getAttribute(NamespaceUri.BPMN, "scriptFormat");
    return scriptFormat != null
        && !scriptFormat.trim().isEmpty()
        && scriptFormat.equalsIgnoreCase("feel")
        && SemanticVersion._8_2.ordinal()
            <= SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal();
  }

  @Override
  public String localName() {
    return "scriptTask";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    if (canBeInternalScript(context)) {
      return new ScriptTaskConvertible();
    }
    return new ServiceTaskConvertible();
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    String scriptFormat = context.getElement().getAttribute(NamespaceUri.BPMN, "scriptFormat");
    if (scriptFormat != null && !scriptFormat.trim().isEmpty()) {
      if (!canBeInternalScript(context)) {
        context.addConversion(
            ServiceTaskConvertible.class,
            convertible ->
                convertible.addZeebeTaskHeader(
                    context.getProperties().getScriptFormatHeader(), scriptFormat));
        context.addConversion(
            ServiceTaskConvertible.class,
            convertible ->
                convertible
                    .getZeebeTaskDefinition()
                    .setType(context.getProperties().getScriptJobType()));
        context.addMessage(
            MessageFactory.scriptFormat(
                context.getProperties().getScriptFormatHeader(), scriptFormat));
        context.addMessage(
            MessageFactory.scriptJobType(
                context.getElement().getLocalName(), context.getProperties().getScriptJobType()));
      }
    }
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }
}
