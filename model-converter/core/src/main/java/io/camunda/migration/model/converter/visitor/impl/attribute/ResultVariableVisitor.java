/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.attribute;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.AbstractDataMapperConvertible;
import io.camunda.migration.model.converter.convertible.BusinessRuleTaskConvertible;
import io.camunda.migration.model.converter.convertible.ScriptTaskConvertible;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.visitor.AbstractSupportedAttributeVisitor;
import io.camunda.migration.model.converter.visitor.impl.activity.BusinessRuleTaskVisitor;
import io.camunda.migration.model.converter.visitor.impl.activity.ScriptTaskVisitor;

public abstract class ResultVariableVisitor extends AbstractSupportedAttributeVisitor {

  public static boolean isBusinessRuleTask(DomElementVisitorContext context) {
    return context.getElement().getLocalName().equals("businessRuleTask")
        && BusinessRuleTaskVisitor.isDmnImplementation(context);
  }

  @Override
  public String attributeLocalName() {
    return "resultVariable";
  }

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    return super.canVisit(context) && canVisitResultVariable(context);
  }

  protected abstract boolean canVisitResultVariable(DomElementVisitorContext context);

  public static class ResultVariableOnBusinessRuleTaskVisitor extends ResultVariableVisitor {
    @Override
    protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
      context.addConversion(
          BusinessRuleTaskConvertible.class,
          conversion -> conversion.getZeebeCalledDecision().setResultVariable(attribute));
      return MessageFactory.resultVariableBusinessRule(
          attributeLocalName(), context.getElement().getLocalName());
    }

    @Override
    protected boolean canVisitResultVariable(DomElementVisitorContext context) {
      return ResultVariableVisitor.isBusinessRuleTask(context);
    }
  }

  public static class ResultVariableOnRestVisitor extends ResultVariableVisitor {

    @Override
    protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
      context.addConversion(
          AbstractDataMapperConvertible.class,
          convertible ->
              convertible.addZeebeTaskHeader(
                  context.getProperties().getResultVariableHeader(), attribute));
      return MessageFactory.resultVariableRest(
          attributeLocalName(),
          context.getElement().getLocalName(),
          context.getProperties().getResultVariableHeader());
    }

    @Override
    protected boolean canVisitResultVariable(DomElementVisitorContext context) {
      return !ResultVariableVisitor.isBusinessRuleTask(context)
          && !ScriptTaskVisitor.canBeInternalScript(context);
    }
  }

  public static class ResultVariableOnInternalScriptVisitor extends ResultVariableVisitor {

    @Override
    protected Message visitSupportedAttribute(DomElementVisitorContext context, String attribute) {
      context.addConversion(
          ScriptTaskConvertible.class, convertible -> convertible.setResultVariable(attribute));
      return MessageFactory.resultVariableInternalScript();
    }

    @Override
    protected boolean canVisitResultVariable(DomElementVisitorContext context) {
      return ScriptTaskVisitor.canBeInternalScript(context);
    }
  }
}
