/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(99) // Run before specific interceptors
@Component
@Profile("entity-programmatic")
public class UniversalEntityInterceptor implements EntityInterceptor<Object, Object> {
  protected final AtomicInteger executionCount = new AtomicInteger(0);

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all entity types
  }

  @Override
  public void execute(EntityConversionContext<Object, Object> context) {
    executionCount.incrementAndGet();
    // Universal interceptor - processes all entity types
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void resetCounter() {
    executionCount.set(0);
  }
}

