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
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractBpmnElementVisitor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomElement;

/**
 * Visitor for {@code <bpmn:condition>} elements inside {@code <bpmn:conditionalEventDefinition>}.
 *
 * <p>This visitor transforms JUEL expressions to FEEL expressions for conditional events. It only
 * processes condition elements that are children of conditionalEventDefinition elements.
 *
 * <p>It also adds a {@code <zeebe:conditionalFilter />} element to the parent
 * conditionalEventDefinition's extension elements.
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

    // Check for language attribute to determine expression type
    String language = context.getElement().getAttribute(BPMN, "language");
    if (StringUtils.isBlank(language)) {
      handleJuelExpression(context, expression);
    } else {
      handleLanguage(context, language, expression);
    }
  }

  private void handleJuelExpression(DomElementVisitorContext context, String expression) {
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel("Conditional event condition", expression);

    // Update the condition element's text content with the transformed expression
    context.getElement().setTextContent(transformationResult.result());

    context.addMessage(
        ExpressionTransformationResultMessageFactory.getMessage(
            transformationResult,
            "https://docs.camunda.io/docs/components/modeler/bpmn/conditional-events/"));
  }

  private void handleLanguage(
      DomElementVisitorContext context, String language, String expression) {
    String resource = context.getElement().getAttribute(CAMUNDA, "resource");
    if (StringUtils.isNotBlank(resource)) {
      context.addMessage(MessageFactory.resourceOnConditionalFlow(resource));
      return;
    }
    if ("feel".equalsIgnoreCase(language)) {
      handleFeelExpression(context, expression);
      return;
    }
    // Other script languages (JavaScript, Groovy, etc.) - add warning
    context.addMessage(MessageFactory.scriptOnConditionalEvent(language, expression));
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
