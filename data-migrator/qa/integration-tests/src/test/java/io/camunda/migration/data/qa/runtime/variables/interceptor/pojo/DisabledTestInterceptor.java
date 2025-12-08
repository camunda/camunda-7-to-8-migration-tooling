/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables.interceptor.pojo;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Set;

public class DisabledTestInterceptor implements VariableInterceptor {
  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(); // Handle all types
  }

  @Override
  public void execute(VariableContext context) {
    context.setC8Value("DISABLED_" + context.getC7Value());
  }
}
