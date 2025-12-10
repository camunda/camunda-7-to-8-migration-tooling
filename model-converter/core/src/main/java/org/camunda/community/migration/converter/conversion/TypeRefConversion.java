/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.conversion;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.community.migration.converter.convertible.AbstractTypeRefConvertible;

public class TypeRefConversion extends AbstractTypedConversion<AbstractTypeRefConvertible> {
  @Override
  protected Class<AbstractTypeRefConvertible> type() {
    return AbstractTypeRefConvertible.class;
  }

  @Override
  protected void convertTyped(DomElement element, AbstractTypeRefConvertible convertible) {
    if (convertible.getTypeRef() != null) {
      element.setAttribute("typeRef", convertible.getTypeRef());
    }
  }
}
