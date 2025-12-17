/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.element;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.RetryTimeCycleConverter;
import io.camunda.migration.diagram.converter.convertible.Convertible;
import io.camunda.migration.diagram.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractCamundaElementVisitor;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FailedJobRetryTimeCycleVisitor extends AbstractCamundaElementVisitor {

  @Override
  protected Message visitCamundaElement(DomElementVisitorContext context) {
    String timecycle = context.getElement().getTextContent();
    try {
      List<String> durations = RetryTimeCycleConverter.convert(timecycle);
      context.addConversion(
          ServiceTaskConvertible.class,
          convertible -> convertible.getZeebeTaskDefinition().setRetries(durations.size()));
      return MessageFactory.failedJobRetryTimeCycle(
          context.getElement().getLocalName(),
          timecycle,
          durations.size(),
          String.join("', '", durations));
    } catch (Exception e) {
      return MessageFactory.failedJobRetryTimeCycleError(
          context.getElement().getLocalName(), timecycle);
    }
  }

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return true;
  }

  @Override
  public String localName() {
    return "failedJobRetryTimeCycle";
  }

  @Override
  protected boolean canVisit(DomElementVisitorContext context) {
    return super.canVisit(context) && isServiceTask(context);
  }

  private boolean isServiceTask(DomElementVisitorContext context) {
    AtomicReference<Convertible> ref = new AtomicReference<>();
    context.addConversion(Convertible.class, ref::set);
    return ref.get() instanceof ServiceTaskConvertible;
  }

  public static class FailedJobRetryTimeCycleRemovalVisitor extends FailedJobRetryTimeCycleVisitor {
    @Override
    protected boolean canVisit(DomElementVisitorContext context) {
      return super.canVisit(context) && isNoServiceTask(context);
    }

    private boolean isNoServiceTask(DomElementVisitorContext context) {
      AtomicReference<Convertible> ref = new AtomicReference<>();
      context.addConversion(Convertible.class, ref::set);
      return !(ref.get() instanceof ServiceTaskConvertible);
    }

    @Override
    protected Message visitCamundaElement(DomElementVisitorContext context) {
      String timecycle = context.getElement().getTextContent();
      return MessageFactory.failedJobRetryTimeCycleRemoved(
          context.getElement().getLocalName(), timecycle);
    }
  }
}
