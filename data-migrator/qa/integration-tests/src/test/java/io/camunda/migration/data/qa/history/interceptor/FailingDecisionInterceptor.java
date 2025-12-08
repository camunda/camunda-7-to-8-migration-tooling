/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.interceptor;

import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Set;
import org.camunda.bpm.engine.impl.history.event.HistoricDecisionInputInstanceEntity;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;

/**
 * Test interceptor that throws an exception only for specific decision input variables
 * to test selective error handling during decision instance migration.
 */
public class FailingDecisionInterceptor implements VariableInterceptor {

  @Override
  public Set<Class<? extends ValueFields>> getEntityTypes() {
    // Only handle decision inputs
    return Set.of(HistoricDecisionInputInstanceEntity.class);
  }

  @Override
  public void execute(VariableContext context) {
    // Fail only for variables with specific value
    if ("FAIL".equals(context.getC7Value())) {
      throw new VariableInterceptorException("Test exception: Unsupported input value FAIL");
    }
    // Otherwise, just pass through
    context.setC8Value(context.getC7Value());
  }
}

