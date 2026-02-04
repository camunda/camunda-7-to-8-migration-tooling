/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl;

import static io.camunda.migration.diagram.converter.NamespaceUri.BPMN;
import static io.camunda.migration.diagram.converter.NamespaceUri.CAMUNDA;
import static io.camunda.migration.diagram.converter.NamespaceUri.ZEEBE;

import io.camunda.migration.diagram.converter.BpmnElementFactory;
import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractBpmnElementVisitor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomElement;

/**
 * Visitor for {@code <bpmn:condition>} elements inside {@code <bpmn:conditionalEventDefinition>}.
 *
 * <p>This visitor handles conditional events by:
 *
 * <ul>
 *   <li>Adding a {@code <zeebe:conditionalFilter />} element when variableName/variableEvents are
 *       present
 *   <li>Processing FEEL expressions (adding the required '=' prefix)
 * </ul>
 *
 * <p>Note: JUEL to FEEL expression transformation will be added in a future iteration.
 */
public class ConditionVisitor extends AbstractBpmnElementVisitor {

  @Override
  public String localName() {
    return "condition";
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    // Only available when inside a conditionalEventDefinition
    if (isInsideConditionalEventDefinition(context)) {
      return SemanticVersion._8_9;
    }
    return null;
  }

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    return super.canVisit(context) && isInsideConditionalEventDefinition(context);
  }

  private boolean isInsideConditionalEventDefinition(DomElementVisitorContext context) {
    DomElement parent = context.getElement().getParentElement();
    return parent != null
        && BPMN.equals(parent.getNamespaceURI())
        && "conditionalEventDefinition".equals(parent.getLocalName());
  }

  @Override
  protected void visitBpmnElement(DomElementVisitorContext context) {
    String expression = context.getElement().getTextContent();
    if (expression == null || expression.isBlank()) {
      return;
    }

    // Add zeebe:conditionalFilter to the parent conditionalEventDefinition
    addConditionalFilterElement(context);

    // Check for language attribute - only process FEEL expressions for now
    String language = context.getElement().getAttribute(BPMN, "language");
    if ("feel".equalsIgnoreCase(language)) {
      handleFeelExpression(context, expression);
    }
  }

  private void handleFeelExpression(DomElementVisitorContext context, String expression) {
    String newExpression = "=" + expression;
    context.getElement().setTextContent(newExpression);
    context.addMessage(MessageFactory.conditionExpressionFeel(expression, newExpression));
  }

  private void addConditionalFilterElement(DomElementVisitorContext context) {
    DomElement conditionalEventDefinition = context.getElement().getParentElement();
    DomElement extensionElements =
        BpmnElementFactory.getExtensionElements(conditionalEventDefinition);
    String variableName = conditionalEventDefinition.getAttribute(CAMUNDA, "variableName");
    String variableEvents = conditionalEventDefinition.getAttribute(CAMUNDA, "variableEvents");

    if (StringUtils.isBlank(variableName) && StringUtils.isBlank(variableEvents)) {
      return;
    }

    DomElement conditionalFilter =
        context.getElement().getDocument().createElement(ZEEBE, "conditionalFilter");

    if (StringUtils.isNotBlank(variableName)) {
      conditionalFilter.setAttribute("variableNames", variableName);
      conditionalEventDefinition.removeAttribute(CAMUNDA, "variableName");
    }

    if (StringUtils.isNotBlank(variableEvents)) {
      conditionalFilter.setAttribute("variableEvents", variableEvents);
      conditionalEventDefinition.removeAttribute(CAMUNDA, "variableEvents");
    }

    extensionElements.appendChild(conditionalFilter);
  }
}
