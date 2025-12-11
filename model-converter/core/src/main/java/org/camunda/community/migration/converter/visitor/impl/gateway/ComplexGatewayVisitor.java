/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl.gateway;

import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.convertible.ComplexGatewayConvertible;
import org.camunda.community.migration.converter.convertible.Convertible;
import org.camunda.community.migration.converter.version.SemanticVersion;
import org.camunda.community.migration.converter.visitor.AbstractGatewayVisitor;

public class ComplexGatewayVisitor extends AbstractGatewayVisitor {

  public static final String ELEMENT_LOCAL_NAME = "complexGateway";

  @Override
  public String localName() {
    return ELEMENT_LOCAL_NAME;
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new ComplexGatewayConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return null;
  }
}
