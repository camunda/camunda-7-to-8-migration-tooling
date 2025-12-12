/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.conversion;

import static io.camunda.migration.model.converter.BpmnElementFactory.*;

import io.camunda.migration.model.converter.convertible.SequenceFlowConvertible;
import org.camunda.bpm.model.xml.instance.DomElement;

public class SequenceFlowConversion extends AbstractTypedConversion<SequenceFlowConvertible> {

  @Override
  protected void convertTyped(DomElement element, SequenceFlowConvertible convertible) {
    if (convertible.getConditionExpression() != null
        && convertible.getConditionExpression().trim().length() > 0) {
      getConditionExpression(element).setTextContent(convertible.getConditionExpression());
    }
  }

  @Override
  protected Class<SequenceFlowConvertible> type() {
    return SequenceFlowConvertible.class;
  }
}
