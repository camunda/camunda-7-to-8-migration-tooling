/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.conversion;

import static io.camunda.migration.diagram.converter.BpmnElementFactory.*;
import static io.camunda.migration.diagram.converter.NamespaceUri.ZEEBE;

import io.camunda.migration.diagram.converter.convertible.FormDefinitionConvertible;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class FormDefinitionConversion extends AbstractTypedConversion<FormDefinitionConvertible> {

  @Override
  protected Class<FormDefinitionConvertible> type() {
    return FormDefinitionConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, FormDefinitionConvertible convertible) {
    if (canAddFormDefinition(convertible)) {
      DomElement extensionElements = getExtensionElements(element);
      extensionElements.appendChild(createFormDefinition(element.getDocument(), convertible));
    }
  }

  private boolean canAddFormDefinition(FormDefinitionConvertible convertible) {
    return convertible.getZeebeFormDefinition().getFormKey() != null
        || convertible.getZeebeFormDefinition().getFormId() != null;
  }

  private DomElement createFormDefinition(
      DomDocument document, FormDefinitionConvertible convertible) {
    DomElement formDefinition = document.createElement(ZEEBE, "formDefinition");
    if (convertible.getZeebeFormDefinition().getFormKey() != null) {
      if (convertible.isZeebeUserTask()) {
        formDefinition.setAttribute(
            ZEEBE, "externalReference", convertible.getZeebeFormDefinition().getFormKey());
      } else {
        formDefinition.setAttribute(
            ZEEBE, "formKey", convertible.getZeebeFormDefinition().getFormKey());
      }
    }
    if (convertible.getZeebeFormDefinition().getFormId() != null) {
      formDefinition.setAttribute(
          ZEEBE, "formId", convertible.getZeebeFormDefinition().getFormId());
    }
    if (convertible.getZeebeFormDefinition().getBindingType() != null) {
      formDefinition.setAttribute(
          ZEEBE, "bindingType", convertible.getZeebeFormDefinition().getBindingType().name());
    }
    if (StringUtils.isNotBlank(convertible.getZeebeFormDefinition().getVersionTag())) {
      formDefinition.setAttribute(
          ZEEBE, "versionTag", convertible.getZeebeFormDefinition().getVersionTag());
    }
    return formDefinition;
  }
}
