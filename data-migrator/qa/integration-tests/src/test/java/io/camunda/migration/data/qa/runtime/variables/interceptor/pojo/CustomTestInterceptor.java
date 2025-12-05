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
import org.camunda.bpm.engine.variable.value.StringValue;

public class CustomTestInterceptor implements VariableInterceptor {
  protected String prefix;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(StringValue.class); // Only handle StringValue type
  }

  @Override
  public void execute(VariableInvocation invocation) {
    invocation.setVariableValue(prefix + invocation.getC7Variable().getValue());
  }

  // Setter for property binding from YAML
  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }
}
