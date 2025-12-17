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

import io.camunda.migration.diagram.converter.convertible.BusinessRuleTaskConvertible;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class BusinessRuleTaskConversion
    extends AbstractTypedConversion<BusinessRuleTaskConvertible> {

  private DomElement createCalledDecision(
      DomDocument document, BusinessRuleTaskConvertible convertible) {
    DomElement calledDecision = document.createElement(ZEEBE, "calledDecision");
    calledDecision.setAttribute(
        ZEEBE, "decisionId", convertible.getZeebeCalledDecision().getDecisionId());
    if (convertible.getZeebeCalledDecision().getBindingType() != null) {
      calledDecision.setAttribute(
          ZEEBE, "bindingType", convertible.getZeebeCalledDecision().getBindingType().name());
    }
    if (StringUtils.isNotBlank(convertible.getZeebeCalledDecision().getVersionTag())) {
      calledDecision.setAttribute(
          ZEEBE, "versionTag", convertible.getZeebeCalledDecision().getVersionTag());
    }
    calledDecision.setAttribute(
        "resultVariable", convertible.getZeebeCalledDecision().getResultVariable());
    return calledDecision;
  }

  @Override
  protected void convertTyped(DomElement element, BusinessRuleTaskConvertible convertible) {
    DomElement extensionElements = getExtensionElements(element);
    extensionElements.appendChild(createCalledDecision(element.getDocument(), convertible));
  }

  @Override
  protected Class<BusinessRuleTaskConvertible> type() {
    return BusinessRuleTaskConvertible.class;
  }
}
