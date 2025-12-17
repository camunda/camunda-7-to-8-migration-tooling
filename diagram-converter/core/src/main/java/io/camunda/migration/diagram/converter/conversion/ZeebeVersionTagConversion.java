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

import io.camunda.migration.diagram.converter.convertible.ZeebeVersionTagConvertible;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ZeebeVersionTagConversion extends AbstractTypedConversion<ZeebeVersionTagConvertible> {
  @Override
  protected Class<ZeebeVersionTagConvertible> type() {
    return ZeebeVersionTagConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, ZeebeVersionTagConvertible convertible) {
    DomElement extensionElements = getExtensionElements(element);
    if (StringUtils.isNotBlank(convertible.getZeebeVersionTag())) {
      appendZeebeVersionTag(extensionElements, convertible.getZeebeVersionTag());
    }
  }

  private void appendZeebeVersionTag(DomElement extensionElements, String zeebeVersionTag) {
    DomElement versionTag = extensionElements.getDocument().createElement(ZEEBE, "versionTag");
    versionTag.setAttribute(ZEEBE, "value", zeebeVersionTag);
    extensionElements.appendChild(versionTag);
  }
}
