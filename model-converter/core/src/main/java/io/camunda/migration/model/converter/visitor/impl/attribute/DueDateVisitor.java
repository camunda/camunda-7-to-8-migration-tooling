/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.attribute;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.UserTaskConvertible;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.model.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.model.converter.expression.ExpressionTransformer;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.visitor.AbstractSupportedAttributeVisitor;

public class DueDateVisitor extends AbstractSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "dueDate";
  }

  @Override
  protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
    ExpressionTransformationResult dueDate =
        ExpressionTransformer.transformToFeel("Due date", attribute);
    context.addConversion(
        UserTaskConvertible.class,
        conv -> conv.getZeebeTaskSchedule().setDueDate(dueDate.result()));
    return ExpressionTransformationResultMessageFactory.getMessage(
        dueDate, "https://docs.camunda.io/docs/components/modeler/bpmn/user-tasks/#scheduling");
  }
}
