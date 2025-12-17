/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.conversion;

import static io.camunda.migration.diagram.converter.BpmnElementFactory.*;
import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.convertible.AbstractActivityConvertible;
import io.camunda.migration.diagram.converter.convertible.AbstractActivityConvertible.BpmnMultiInstanceLoopCharacteristics;
import io.camunda.migration.diagram.converter.convertible.AbstractActivityConvertible.ZeebeElementTemplate;
import io.camunda.migration.diagram.converter.convertible.AbstractActivityConvertible.ZeebeLoopCharacteristics;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;

public class ActivityConversion extends AbstractTypedConversion<AbstractActivityConvertible> {

  @Override
  protected Class<AbstractActivityConvertible> type() {
    return AbstractActivityConvertible.class;
  }

  @Override
  public final void convertTyped(DomElement element, AbstractActivityConvertible convertible) {
    if (convertible.wasLoopCharacteristicsInitialized()) {
      createMultiInstance(element, convertible.getBpmnMultiInstanceLoopCharacteristics());
    }
    if (convertible.getZeebeElementTemplate() != null) {
      createElementTemplate(element, convertible.getZeebeElementTemplate());
    }
  }

  private void createElementTemplate(
      DomElement element, ZeebeElementTemplate zeebeElementTemplate) {
    if (zeebeElementTemplate.getModelerTemplate() != null) {
      element.setAttribute(ZEEBE, "modelerTemplate", zeebeElementTemplate.getModelerTemplate());
    }
    if (zeebeElementTemplate.getModelerTemplateVersion() != null) {
      element.setAttribute(
          ZEEBE, "modelerTemplateVersion", zeebeElementTemplate.getModelerTemplateVersion());
    }
  }

  private void createMultiInstance(
      DomElement element,
      BpmnMultiInstanceLoopCharacteristics bpmnMultiInstanceLoopCharacteristics) {
    DomElement multiInstanceLoopCharacteristics = getMultiInstanceLoopCharacteristics(element);
    if (bpmnMultiInstanceLoopCharacteristics.isSequential()) {
      multiInstanceLoopCharacteristics.setAttribute("isSequential", Boolean.toString(true));
    }
    DomElement extensionElements = getExtensionElements(multiInstanceLoopCharacteristics);
    extensionElements.appendChild(
        createLoopCharacteristics(element.getDocument(), bpmnMultiInstanceLoopCharacteristics));
    if (bpmnMultiInstanceLoopCharacteristics.getCompletionCondition() != null) {
      createCompletionCondition(
          multiInstanceLoopCharacteristics, bpmnMultiInstanceLoopCharacteristics);
    }
  }

  private DomElement createLoopCharacteristics(
      DomDocument document,
      BpmnMultiInstanceLoopCharacteristics bpmnMultiInstanceLoopCharacteristics) {
    DomElement loopCharacteristics = document.createElement(ZEEBE, "loopCharacteristics");
    ZeebeLoopCharacteristics zbLoopCharacteristics =
        bpmnMultiInstanceLoopCharacteristics.getZeebeLoopCharacteristics();
    if (zbLoopCharacteristics.getInputCollection() != null) {
      loopCharacteristics.setAttribute(
          "inputCollection", zbLoopCharacteristics.getInputCollection());
    }
    if (zbLoopCharacteristics.getInputElement() != null) {
      loopCharacteristics.setAttribute("inputElement", zbLoopCharacteristics.getInputElement());
    }
    if (zbLoopCharacteristics.getOutputCollection() != null) {
      loopCharacteristics.setAttribute(
          "outputCollection", zbLoopCharacteristics.getOutputCollection());
    }
    if (zbLoopCharacteristics.getOutputElement() != null) {
      loopCharacteristics.setAttribute("outputElement", zbLoopCharacteristics.getOutputElement());
    }
    return loopCharacteristics;
  }

  private void createCompletionCondition(
      DomElement element,
      BpmnMultiInstanceLoopCharacteristics bpmnMultiInstanceLoopCharacteristics) {
    DomElement completionCondition = getCompletionCondition(element);
    completionCondition.setTextContent(
        bpmnMultiInstanceLoopCharacteristics.getCompletionCondition());
  }
}
