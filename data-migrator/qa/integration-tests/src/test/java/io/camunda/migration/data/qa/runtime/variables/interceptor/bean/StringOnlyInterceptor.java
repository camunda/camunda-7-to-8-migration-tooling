/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables.interceptor.bean;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(101) // Run after universal interceptor
@Component
@Profile("programmatic")
public class StringOnlyInterceptor implements VariableInterceptor {
  protected final AtomicInteger executionCount = new AtomicInteger(0);

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(StringValue.class); // Only process string variables
  }

  @Override
  public void execute(VariableContext context) {
    executionCount.incrementAndGet();
    context.setC8Value("STRING_" + context.getC7Value());
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void resetCounter() {
    executionCount.set(0);
  }
}
