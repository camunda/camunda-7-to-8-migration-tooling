/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.convertible;

import io.camunda.migration.model.converter.convertible.UserTaskConvertible.ZeebeFormDefinition;

public class StartEventConvertible extends AbstractCatchEventConvertible
    implements FormDefinitionConvertible {
  private final ZeebeFormDefinition zeebeFormDefinition = new ZeebeFormDefinition();

  @Override
  public ZeebeFormDefinition getZeebeFormDefinition() {
    return zeebeFormDefinition;
  }

  @Override
  public boolean isZeebeUserTask() {
    // not relevant, the start form will only have a form id
    return true;
  }
}
