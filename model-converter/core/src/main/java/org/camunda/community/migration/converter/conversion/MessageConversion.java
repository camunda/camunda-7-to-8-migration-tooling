/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.conversion;

import static org.camunda.community.migration.converter.BpmnElementFactory.*;

import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.community.migration.converter.NamespaceUri;
import org.camunda.community.migration.converter.convertible.MessageConvertible;

public class MessageConversion extends AbstractTypedConversion<MessageConvertible> {

  private DomElement createSubscription(DomDocument document, MessageConvertible convertible) {
    DomElement subscription = document.createElement(NamespaceUri.ZEEBE, "subscription");
    subscription.setAttribute(
        "correlationKey", convertible.getZeebeSubscription().getCorrelationKey());
    return subscription;
  }

  @Override
  protected void convertTyped(DomElement element, MessageConvertible convertible) {
    DomElement extensionElements = getExtensionElements(element);
    if (convertible.getZeebeSubscription().getCorrelationKey() != null) {
      extensionElements.appendChild(createSubscription(element.getDocument(), convertible));
    }
  }

  @Override
  protected Class<MessageConvertible> type() {
    return MessageConvertible.class;
  }
}
