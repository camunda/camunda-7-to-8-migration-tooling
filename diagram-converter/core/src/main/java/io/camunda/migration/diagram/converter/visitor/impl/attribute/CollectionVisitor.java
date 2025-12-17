/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractActivityConvertible;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractSupportedAttributeVisitor;

public class CollectionVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "collection";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel("Collection", attribute);
    context.addConversion(
        AbstractActivityConvertible.class,
        AbstractActivityConvertible::initializeLoopCharacteristics);
    context.addConversion(
        AbstractActivityConvertible.class,
        conversion ->
            conversion
                .getBpmnMultiInstanceLoopCharacteristics()
                .getZeebeLoopCharacteristics()
                .setInputCollection(transformationResult.result()));
    context.addMessage(MessageFactory.collectionHint());
    return ExpressionTransformationResultMessageFactory.getMessage(
        transformationResult,
        "https://docs.camunda.io/docs/components/modeler/bpmn/multi-instance/#defining-the-collection-to-iterate-over");
  }
}
