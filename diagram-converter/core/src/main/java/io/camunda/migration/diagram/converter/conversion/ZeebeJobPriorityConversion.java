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

import io.camunda.migration.diagram.converter.convertible.ZeebeJobPriorityConvertible;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ZeebeJobPriorityConversion
    extends AbstractTypedConversion<ZeebeJobPriorityConvertible> {
  @Override
  protected Class<ZeebeJobPriorityConvertible> type() {
    return ZeebeJobPriorityConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, ZeebeJobPriorityConvertible convertible) {
    String priority = convertible.getZeebeJobPriorityDefinition().getPriority();

    if (StringUtils.isBlank(priority)) {
      return;
    }
    DomElement extensionElements = getExtensionElements(element);
    DomElement jobPriorityDefinition =
        extensionElements.getDocument().createElement(ZEEBE, "jobPriorityDefinition");
    jobPriorityDefinition.setAttribute("priority", priority);
    extensionElements.appendChild(jobPriorityDefinition);
  }
}
