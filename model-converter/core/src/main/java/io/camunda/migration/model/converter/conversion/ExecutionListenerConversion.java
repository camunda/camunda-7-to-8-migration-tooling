/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.conversion;

import static io.camunda.migration.model.converter.BpmnElementFactory.*;
import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.model.converter.convertible.AbstractExecutionListenerConvertible;
import io.camunda.migration.model.converter.convertible.AbstractExecutionListenerConvertible.ZeebeExecutionListener;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ExecutionListenerConversion
    extends AbstractTypedConversion<AbstractExecutionListenerConvertible> {
  @Override
  protected Class<AbstractExecutionListenerConvertible> type() {
    return AbstractExecutionListenerConvertible.class;
  }

  @Override
  protected void convertTyped(
      DomElement element, AbstractExecutionListenerConvertible convertible) {
    if (convertible.getZeebeExecutionListeners() != null
        && !convertible.getZeebeExecutionListeners().isEmpty()) {
      DomElement extensionElements = getExtensionElements(element);
      DomElement executionListeners = createExecutionListeners(extensionElements);
      for (ZeebeExecutionListener executionListener : convertible.getZeebeExecutionListeners()) {
        createExecutionListener(executionListeners, executionListener);
      }
    }
  }

  private void createExecutionListener(
      DomElement executionListeners, ZeebeExecutionListener executionListener) {
    DomElement executionListenerDom =
        executionListeners.getDocument().createElement(ZEEBE, "executionListener");
    executionListenerDom.setAttribute(ZEEBE, "eventType", executionListener.getEventType().name());
    executionListenerDom.setAttribute(ZEEBE, "type", executionListener.getListenerType());
    if (StringUtils.isNotBlank(executionListener.getRetries())) {
      executionListenerDom.setAttribute(ZEEBE, "retries", executionListener.getRetries());
    }
    executionListeners.appendChild(executionListenerDom);
  }

  private DomElement createExecutionListeners(DomElement extensionElements) {
    DomElement executionListeners =
        extensionElements.getDocument().createElement(ZEEBE, "executionListeners");
    extensionElements.appendChild(executionListeners);
    return executionListeners;
  }
}
