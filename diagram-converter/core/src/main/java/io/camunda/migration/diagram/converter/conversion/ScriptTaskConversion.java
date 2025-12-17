/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.conversion;

import static io.camunda.migration.diagram.converter.BpmnElementFactory.*;

import io.camunda.migration.diagram.converter.NamespaceUri;
import io.camunda.migration.diagram.converter.convertible.ScriptTaskConvertible;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ScriptTaskConversion extends AbstractTypedConversion<ScriptTaskConvertible> {
  @Override
  protected Class<ScriptTaskConvertible> type() {
    return ScriptTaskConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, ScriptTaskConvertible convertible) {
    DomElement extensionElements = getExtensionElements(element);
    extensionElements.appendChild(createScriptElement(element.getDocument(), convertible));
  }

  private DomElement createScriptElement(DomDocument document, ScriptTaskConvertible convertible) {
    DomElement scriptElement = document.createElement(NamespaceUri.ZEEBE, "script");
    if (convertible.getExpression() != null) {
      scriptElement.setAttribute("expression", convertible.getExpression());
    }
    if (convertible.getResultVariable() != null) {
      scriptElement.setAttribute("resultVariable", convertible.getResultVariable());
    }
    return scriptElement;
  }
}
