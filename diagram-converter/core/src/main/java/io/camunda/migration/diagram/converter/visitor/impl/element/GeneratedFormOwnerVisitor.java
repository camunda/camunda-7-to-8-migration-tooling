/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.element;

import static io.camunda.migration.diagram.converter.NamespaceUri.BPMN;
import static io.camunda.migration.diagram.converter.NamespaceUri.CAMUNDA;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractBpmnElementVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public abstract class GeneratedFormOwnerVisitor extends AbstractBpmnElementVisitor {

  @Override
  protected void visitBpmnElement(DomElementVisitorContext context) {
    String generatedFormElement = generatedFormElement(context.getElement());
    if (generatedFormElement != null) {
      context.addMessage(MessageFactory.formData(generatedFormElement));
    }
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  private String generatedFormElement(DomElement owner) {
    boolean hasFormProperty = false;
    for (DomElement extensionElements : owner.getChildElementsByNameNs(BPMN, "extensionElements")) {
      if (!extensionElements.getChildElementsByNameNs(CAMUNDA, "formData").isEmpty()) {
        return "formData";
      }
      hasFormProperty |=
          !extensionElements.getChildElementsByNameNs(CAMUNDA, "formProperty").isEmpty();
    }
    return hasFormProperty ? "formProperty" : null;
  }

  public static class UserTaskVisitor extends GeneratedFormOwnerVisitor {

    @Override
    public String localName() {
      return "userTask";
    }
  }

  public static class StartEventVisitor extends GeneratedFormOwnerVisitor {

    @Override
    public String localName() {
      return "startEvent";
    }
  }
}
