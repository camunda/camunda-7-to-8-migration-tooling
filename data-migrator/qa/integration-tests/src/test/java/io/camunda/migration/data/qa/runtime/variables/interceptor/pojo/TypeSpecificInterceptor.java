/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.runtime.variables.interceptor.pojo;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import java.util.Set;
import org.camunda.bpm.engine.variable.value.DateValue;

// New interceptor that only handles specific types (DateValue)
public class TypeSpecificInterceptor implements VariableInterceptor {
  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DateValue.class); // Only handle DateValue types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    invocation.setVariableValue("DATE_SPECIFIC_" + invocation.getC7Variable().getValue());
  }
}
