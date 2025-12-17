/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.attribute;

import io.camunda.migration.diagram.converter.visitor.AbstractCurrentlyNotSupportedAttributeVisitor;

public class ErrorCodeVariableVisitor extends AbstractCurrentlyNotSupportedAttributeVisitor {
  @Override
  public String attributeLocalName() {
    return "errorCodeVariable";
  }
}
