/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.conversion;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.community.migration.converter.NamespaceUri;
import org.camunda.community.migration.converter.convertible.ErrorConvertible;

public class ErrorConversion extends AbstractTypedConversion<ErrorConvertible> {
  @Override
  protected Class<ErrorConvertible> type() {
    return ErrorConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, ErrorConvertible convertible) {
    if (convertible.getErrorCode() != null) {
      element.setAttribute(NamespaceUri.BPMN, "errorCode", convertible.getErrorCode());
    }
  }
}
