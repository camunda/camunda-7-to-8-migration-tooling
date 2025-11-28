/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.variables.interceptor.bean;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(99) // Run before string only interceptor
@Component
@Profile("programmatic")
public class UniversalInterceptor implements VariableInterceptor {
  protected final AtomicInteger executionCount = new AtomicInteger(0);

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    executionCount.incrementAndGet();
    invocation.setVariableValue("UNIVERSAL_" + invocation.getC7Variable().getValue());
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void resetCounter() {
    executionCount.set(0);
  }
}
