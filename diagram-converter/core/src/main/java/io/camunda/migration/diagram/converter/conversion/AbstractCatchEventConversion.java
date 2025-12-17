/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.conversion;

import static io.camunda.migration.diagram.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.convertible.AbstractCatchEventConvertible;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.DomElement;

public class AbstractCatchEventConversion
    extends AbstractTypedConversion<AbstractCatchEventConvertible> {
  @Override
  protected Class<AbstractCatchEventConvertible> type() {
    return AbstractCatchEventConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, AbstractCatchEventConvertible convertible) {
    getTimerEventDefinition(element)
        .ifPresent(
            timerEventDefinition -> {
              getTimeDuration(timerEventDefinition)
                  .ifPresent(
                      timeDuration ->
                          applyExpression(timeDuration, convertible.getTimeDurationExpression()));
              getTimeDate(timerEventDefinition)
                  .ifPresent(
                      timeDate -> applyExpression(timeDate, convertible.getTimeDateExpression()));
              getTimeCycle(timerEventDefinition)
                  .ifPresent(
                      timeCycle ->
                          applyExpression(timeCycle, convertible.getTimeCycleExpression()));
            });
  }

  private void applyExpression(DomElement container, String expression) {
    if (expression != null) {
      container.setTextContent(expression);
    }
  }

  private Optional<DomElement> getTimerEventDefinition(DomElement element) {
    return getChildElement(element, BPMN, "timerEventDefinition");
  }

  private Optional<DomElement> getTimeDuration(DomElement timerEventDefinition) {
    return getChildElement(timerEventDefinition, BPMN, "timeDuration");
  }

  private Optional<DomElement> getTimeDate(DomElement timerEventDefinition) {
    return getChildElement(timerEventDefinition, BPMN, "timeDate");
  }

  private Optional<DomElement> getTimeCycle(DomElement timerEventDefinition) {
    return getChildElement(timerEventDefinition, BPMN, "timeCycle");
  }

  private Optional<DomElement> getChildElement(
      DomElement element, String namespaceUri, String localName) {
    return element.getChildElementsByNameNs(namespaceUri, localName).stream().findFirst();
  }
}
