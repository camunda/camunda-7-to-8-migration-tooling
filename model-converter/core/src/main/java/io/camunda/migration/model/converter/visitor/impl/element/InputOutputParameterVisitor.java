/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.element;

import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.AbstractDataMapperConvertible;
import io.camunda.migration.model.converter.convertible.AbstractDataMapperConvertible.MappingDirection;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.model.converter.expression.ExpressionTransformer;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.visitor.AbstractCamundaElementVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public abstract class InputOutputParameterVisitor extends AbstractCamundaElementVisitor {
  public static final String INPUT_PARAMETER = "inputParameter";
  public static final String OUTPUT_PARAMETER = "outputParameter";

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return !isNotStringOrExpression(context.getElement());
  }

  @Override
  protected Message visitCamundaElement(DomElementVisitorContext context) {
    DomElement element = context.getElement();
    String name = element.getAttribute("name");
    MappingDirection direction = findMappingDirection(element);
    if (isScript(element)) {
      // Scripts are handled in ScriptVisitor
      return MessageFactory.inputOutputScript();
    }
    if (isNotStringOrExpression(element)) {
      return MessageFactory.inputOutputParameterIsNoExpression(localName(), name);
    }
    String expression = element.getTextContent();
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel(
            direction.getName() + " parameter '" + name + "'", expression);
    context.addConversion(
        AbstractDataMapperConvertible.class,
        abstractTaskConversion ->
            abstractTaskConversion.addZeebeIoMapping(
                direction, transformationResult.result(), name));
    return ExpressionTransformationResultMessageFactory.getMessage(
        transformationResult,
        "https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings");
  }

  private boolean isScript(DomElement element) {
    return element.getChildElements().stream()
        .anyMatch(e -> e.getNamespaceURI().equals(CAMUNDA) && e.getLocalName().equals("script"));
  }

  private MappingDirection findMappingDirection(DomElement element) {
    if (isInputParameter(element.getLocalName())) {
      return MappingDirection.INPUT;
    }
    if (isOutputParameter(element.getLocalName())) {
      return MappingDirection.OUTPUT;
    }
    throw new IllegalStateException("Must be input or output!");
  }

  private boolean isNotStringOrExpression(DomElement element) {
    return !element.getChildElements().isEmpty();
  }

  private boolean isInputParameter(String localName) {
    return INPUT_PARAMETER.equals(localName);
  }

  private boolean isOutputParameter(String localName) {
    return OUTPUT_PARAMETER.equals(localName);
  }

  public static class InputParameterVisitor extends InputOutputParameterVisitor {

    @Override
    public String localName() {
      return INPUT_PARAMETER;
    }
  }

  public static class OutputParameterVisitor extends InputOutputParameterVisitor {

    @Override
    public String localName() {
      return OUTPUT_PARAMETER;
    }
  }
}
