/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.convertible;

import org.camunda.community.migration.converter.convertible.UserTaskConvertible.ZeebeFormDefinition;

public interface FormDefinitionConvertible extends Convertible {
  ZeebeFormDefinition getZeebeFormDefinition();

  boolean isZeebeUserTask();
}
