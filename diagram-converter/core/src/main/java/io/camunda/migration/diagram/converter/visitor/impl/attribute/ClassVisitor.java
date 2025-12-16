/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractDelegateImplementationVisitor;
import java.util.List;
import java.util.function.Consumer;

public class ClassVisitor extends AbstractDelegateImplementationVisitor {

  @Override
  public String attributeLocalName() {
    return "class";
  }

  @Override
  protected String extractJobType(DomElementVisitorContext context, String attribute) {
    int lastDot = attribute.lastIndexOf('.');
    return decapitalize(attribute.substring(lastDot + 1));
  }

  @Override
  protected List<Consumer<ServiceTaskConvertible>> additionalConversions(
      DomElementVisitorContext context, String attribute) {
    return List.of();
  }

  @Override
  protected Message returnMessage(DomElementVisitorContext context, String attribute) {
    return MessageFactory.delegateExpressionAsJobType(
        extractJobType(context, attribute), attribute);
  }

  private String decapitalize(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
