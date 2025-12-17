/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.conversion;

import static io.camunda.migration.diagram.converter.BpmnElementFactory.*;
import static io.camunda.migration.diagram.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.convertible.CallActivityConvertible;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class CallActivityConversion extends AbstractTypedConversion<CallActivityConvertible> {

  private DomElement createCalledElement(
      DomDocument document, CallActivityConvertible convertible) {
    DomElement calledElement = document.createElement(ZEEBE, "calledElement");
    if (convertible.getZeebeCalledElement().getProcessId() != null) {
      calledElement.setAttribute(
          ZEEBE, "processId", convertible.getZeebeCalledElement().getProcessId());
    }
    if (convertible.getZeebeCalledElement().getBindingType() != null) {
      calledElement.setAttribute(
          ZEEBE, "bindingType", convertible.getZeebeCalledElement().getBindingType().name());
    }
    if (StringUtils.isNotBlank(convertible.getZeebeCalledElement().getVersionTag())) {
      calledElement.setAttribute(
          ZEEBE, "versionTag", convertible.getZeebeCalledElement().getVersionTag());
    }
    calledElement.setAttribute(
        "propagateAllChildVariables",
        Boolean.toString(convertible.getZeebeCalledElement().isPropagateAllChildVariables()));
    calledElement.setAttribute(
        "propagateAllParentVariables",
        Boolean.toString(convertible.getZeebeCalledElement().isPropagateAllParentVariables()));
    return calledElement;
  }

  @Override
  protected void convertTyped(DomElement element, CallActivityConvertible convertible) {
    DomElement extensionProperties = getExtensionElements(element);
    extensionProperties.appendChild(createCalledElement(element.getDocument(), convertible));
  }

  @Override
  protected Class<CallActivityConvertible> type() {
    return CallActivityConvertible.class;
  }
}
