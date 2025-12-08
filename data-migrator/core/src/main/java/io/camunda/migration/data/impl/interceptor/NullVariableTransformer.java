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

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for null variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Handles variables of type {@link NullValueImpl}, representing null values.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(14)  // Transform null - runs after validators and complex type transformers
@Component
public class NullVariableTransformer implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(NullValueImpl.class);
  }

  @Override
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();

    logStartExecution(this.getClass(), variable.getName());
    invocation.setVariableValue(variable.getValue());
    logEndExecution(this.getClass(), variable.getName());
  }
}
