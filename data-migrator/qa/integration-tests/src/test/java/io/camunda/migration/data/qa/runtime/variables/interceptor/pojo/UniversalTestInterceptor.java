/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.runtime.variables.interceptor.pojo;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import java.util.Set;

// Additional test interceptor to verify getTypes() with empty set (universal)
public class UniversalTestInterceptor implements VariableInterceptor {
  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    invocation.setVariableValue("UNIVERSAL_" + invocation.getC7Variable().getValue());
  }
}
