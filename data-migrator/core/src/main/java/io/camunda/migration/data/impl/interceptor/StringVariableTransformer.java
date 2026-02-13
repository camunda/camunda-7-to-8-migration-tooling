/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor;

import static io.camunda.migration.data.impl.logging.VariableServiceLogs.logEndExecution;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.logStartExecution;

import io.camunda.migration.data.interceptor.VariableContext;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.variable.value.PrimitiveValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(16)  // Transform strings - runs after validators and complex type transformers
@Component
public class StringVariableTransformer implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(StringValue.class);
  }

  @Override
  public void execute(VariableContext context) {
    logStartExecution(this.getClass(), context.getName());
    Object stringValue = context.getC7Value();
    context.setC8Value(context.isHistory() ? String.format("\"%s\"", stringValue) : stringValue);
    logEndExecution(this.getClass(), context.getName());
  }
}
