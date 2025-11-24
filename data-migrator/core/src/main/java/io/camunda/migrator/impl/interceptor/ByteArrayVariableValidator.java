/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.interceptor;

import static io.camunda.migrator.impl.logging.VariableServiceLogs.BYTE_ARRAY_UNSUPPORTED_ERROR;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.logStartExecution;

import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.BytesValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validator for byte array variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Validates that primitive variables do not contain byte arrays, which are unsupported in C8.
 * This interceptor runs first to catch byte arrays before any other processing.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(1)  // Run first - validate byte arrays before any transformation
@Component
public class ByteArrayVariableValidator implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(BytesValue.class); // Only handle BytesValue types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    // No type checking needed - we're only called for BytesValue types
    VariableInstanceEntity variable = invocation.getC7Variable();
    logStartExecution(this.getClass(), variable.getName());
    throw new VariableInterceptorException(BYTE_ARRAY_UNSUPPORTED_ERROR);
  }
}
