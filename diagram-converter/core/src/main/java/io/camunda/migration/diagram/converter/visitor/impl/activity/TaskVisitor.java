/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.activity;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.Convertible;
import io.camunda.migration.diagram.converter.convertible.TaskConvertible;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractActivityVisitor;

public class TaskVisitor extends AbstractActivityVisitor {
  @Override
  public String localName() {
    return "task";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new TaskConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_2;
  }
}
