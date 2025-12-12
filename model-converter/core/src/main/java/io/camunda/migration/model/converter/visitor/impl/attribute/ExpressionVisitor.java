/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.attribute;

import static io.camunda.migration.model.converter.expression.ExpressionTransformer.*;
import static io.camunda.migration.model.converter.message.MessageFactory.*;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.visitor.AbstractDelegateImplementationVisitor;
import java.util.List;
import java.util.function.Consumer;

public class ExpressionVisitor extends AbstractDelegateImplementationVisitor {
  @Override
  public String attributeLocalName() {
    return "expression";
  }

  @Override
  protected String extractJobType(DomElementVisitorContext context, String attribute) {
    return transformToJobType(attribute).result();
  }

  @Override
  protected List<Consumer<ServiceTaskConvertible>> additionalConversions(
      DomElementVisitorContext context, String attribute) {
    return List.of();
  }

  @Override
  protected Message returnMessage(DomElementVisitorContext context, String attribute) {
    return delegateExpressionAsJobType(extractJobType(context, attribute), attribute);
  }
}
