/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.conversion;

import io.camunda.migration.model.converter.convertible.TextConvertible;
import java.util.List;
import org.camunda.bpm.model.xml.instance.DomElement;

public class TextConversion extends AbstractTypedConversion<TextConvertible> {
  @Override
  protected Class<TextConvertible> type() {
    return TextConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, TextConvertible convertible) {
    List<DomElement> text = element.getChildElementsByNameNs(element.getNamespaceURI(), "text");
    if (text == null || text.isEmpty()) {
      throw new IllegalStateException(
          "No text elements found for element " + element.getLocalName());
    }
    text.get(0).setTextContent(convertible.getContent());
  }
}
