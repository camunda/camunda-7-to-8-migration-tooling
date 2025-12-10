/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl;

import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.SequenceFlowConvertible;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.model.converter.expression.ExpressionTransformer;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractBpmnElementVisitor;
import io.camunda.migration.model.converter.visitor.impl.gateway.ComplexGatewayVisitor;
import io.camunda.migration.model.converter.visitor.impl.gateway.ExclusiveGatewayVisitor;
import io.camunda.migration.model.converter.visitor.impl.gateway.InclusiveGatewayVisitor;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ConditionExpressionVisitor extends AbstractBpmnElementVisitor {

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return isConditionalFlow(context) ? null : SemanticVersion._8_0;
  }

  private boolean isConditionalFlow(DomElementVisitorContext context) {
    String sourceRef = context.getElement().getParentElement().getAttribute(BPMN, "sourceRef");
    if (sourceRef == null) {
      return false;
    }
    DomElement source = context.getElement().getDocument().getElementById(sourceRef);
    if (source == null) {
      return false;
    }
    return !isGateway(source);
  }

  private boolean isGateway(DomElement element) {
    return element.getNamespaceURI().equals(BPMN)
        && Arrays.asList(
                ExclusiveGatewayVisitor.ELEMENT_LOCAL_NAME,
                InclusiveGatewayVisitor.ELEMENT_LOCAL_NAME,
                ComplexGatewayVisitor.ELEMENT_LOCAL_NAME)
            .contains(element.getLocalName());
  }

  @Override
  public String localName() {
    return "conditionExpression";
  }

  @Override
  protected void visitBpmnElement(DomElementVisitorContext context) {
    String language = context.getElement().getAttribute(BPMN, "language");
    if (StringUtils.isBlank(language)) {
      handleJuelExpression(context);
    } else {
      handleLanguage(context, language);
    }
  }

  private void handleLanguage(DomElementVisitorContext context, String language) {
    String resource = context.getElement().getAttribute(CAMUNDA, "resource");
    if (StringUtils.isNotBlank(resource)) {
      context.addMessage(MessageFactory.resourceOnConditionalFlow(resource));
      return;
    }
    if ("feel".equalsIgnoreCase(language)) {
      handleFeelExpression(context);
      return;
    }
    context.addMessage(
        MessageFactory.scriptOnConditionalFlow(language, context.getElement().getTextContent()));
  }

  @Override
  protected Message cannotBeConvertedMessage(DomElementVisitorContext context) {
    return MessageFactory.conditionalFlow();
  }

  private void handleFeelExpression(DomElementVisitorContext context) {
    String oldExpression = context.getElement().getTextContent();
    String newExpression = "=" + oldExpression;
    context.addConversion(
        SequenceFlowConvertible.class, c -> c.setConditionExpression(newExpression));
    context.addMessage(MessageFactory.conditionExpressionFeel(oldExpression, newExpression));
  }

  private void handleJuelExpression(DomElementVisitorContext context) {
    String expression = context.getElement().getTextContent();
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel("Condition expression", expression);
    context.addConversion(
        SequenceFlowConvertible.class,
        conversion -> conversion.setConditionExpression(transformationResult.result()));
    context.addMessage(
        ExpressionTransformationResultMessageFactory.getMessage(
            transformationResult,
            "https://docs.camunda.io/docs/components/modeler/bpmn/exclusive-gateways/#conditions"));
  }
}
