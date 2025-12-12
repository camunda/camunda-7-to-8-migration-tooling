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

import io.camunda.migration.model.converter.convertible.ServiceTaskConvertible;
import io.camunda.migration.model.converter.convertible.ServiceTaskConvertible.ZeebeTaskDefinition;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ServiceTaskConversion extends AbstractTypedConversion<ServiceTaskConvertible> {

  private DomElement createTaskDefinition(
      DomDocument document, ZeebeTaskDefinition zeebeTaskDefinition) {
    DomElement taskDefinition = document.createElement(ZEEBE, "taskDefinition");
    if (zeebeTaskDefinition.getType() != null) {
      taskDefinition.setAttribute(ZEEBE, "type", zeebeTaskDefinition.getType());
    }
    if (zeebeTaskDefinition.getRetries() != null) {
      taskDefinition.setAttribute(ZEEBE, "retries", zeebeTaskDefinition.getRetries().toString());
    }
    return taskDefinition;
  }

  @Override
  protected void convertTyped(DomElement element, ServiceTaskConvertible convertible) {
    DomElement extensionElements = getExtensionElements(element);
    if (convertible.getZeebeTaskDefinition().getType() != null
        || convertible.getZeebeTaskDefinition().getRetries() != null) {
      extensionElements.appendChild(
          createTaskDefinition(element.getDocument(), convertible.getZeebeTaskDefinition()));
    }
  }

  @Override
  protected Class<ServiceTaskConvertible> type() {
    return ServiceTaskConvertible.class;
  }
}
