/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.conversion;

import static io.camunda.migration.model.converter.BpmnElementFactory.*;

import io.camunda.migration.model.converter.NamespaceUri;
import io.camunda.migration.model.converter.convertible.AbstractProcessElementConvertible;
import io.camunda.migration.model.converter.convertible.AbstractProcessElementConvertible.ZeebeProperty;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ProcessElementConversion
    extends AbstractTypedConversion<AbstractProcessElementConvertible> {
  @Override
  protected Class<AbstractProcessElementConvertible> type() {
    return AbstractProcessElementConvertible.class;
  }

  @Override
  public final void convertTyped(
      DomElement element, AbstractProcessElementConvertible convertible) {
    DomElement extensionElements = getExtensionElements(element);
    if (convertible.getZeebeProperties() != null && !convertible.getZeebeProperties().isEmpty()) {
      extensionElements.appendChild(createProperties(element.getDocument(), convertible));
    }
  }

  private DomElement createProperties(
      DomDocument document, AbstractProcessElementConvertible convertible) {
    DomElement properties = document.createElement(NamespaceUri.ZEEBE, "properties");
    convertible
        .getZeebeProperties()
        .forEach(property -> properties.appendChild(createProperty(property, document)));
    return properties;
  }

  private DomElement createProperty(ZeebeProperty zeebeProperty, DomDocument document) {
    DomElement property = document.createElement(NamespaceUri.ZEEBE, "property");
    if (zeebeProperty.getName() != null) {
      property.setAttribute("name", zeebeProperty.getName());
    }
    if (zeebeProperty.getValue() != null) {
      property.setAttribute("value", zeebeProperty.getValue());
    }
    return property;
  }
}
