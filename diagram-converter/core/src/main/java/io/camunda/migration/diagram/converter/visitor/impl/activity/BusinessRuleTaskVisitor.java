/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.activity;

import static io.camunda.migration.diagram.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.BusinessRuleTaskConvertible;
import io.camunda.migration.diagram.converter.convertible.Convertible;
import io.camunda.migration.diagram.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractActivityVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public class BusinessRuleTaskVisitor extends AbstractActivityVisitor {
  public static boolean isDmnImplementation(DomElementVisitorContext context) {
    return context.getElement().getAttribute(CAMUNDA, "decisionRef") != null;
  }

  @Override
  public String localName() {
    return "businessRuleTask";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    if (isDmnImplementation(context)) {
      return new BusinessRuleTaskConvertible();
    } else {
      return new ServiceTaskConvertible();
    }
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    if (isDmnImplementation(context) && !hasDecisionResult(context.getElement())) {
      context.addConversion(
          BusinessRuleTaskConvertible.class,
          br -> br.getZeebeCalledDecision().setResultVariable("decisionResult"));
    }
  }

  private boolean hasDecisionResult(DomElement element) {
    return element.hasAttribute(CAMUNDA, "resultVariable");
  }
}
