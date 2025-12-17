/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.gateway;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.Convertible;
import io.camunda.migration.diagram.converter.convertible.ParallelGatewayConvertible;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractGatewayVisitor;

public class ParallelGatewayVisitor extends AbstractGatewayVisitor {
  @Override
  public String localName() {
    return "parallelGateway";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new ParallelGatewayConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }
}
