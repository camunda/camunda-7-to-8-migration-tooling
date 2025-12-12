/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.dmn;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.convertible.Convertible;
import io.camunda.migration.model.converter.convertible.OutputValuesConvertible;
import io.camunda.migration.model.converter.visitor.AbstractDecisionElementVisitor;

public class OutputValuesVisitor extends AbstractDecisionElementVisitor {
  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new OutputValuesConvertible();
  }

  @Override
  public String localName() {
    return "outputValues";
  }
}
