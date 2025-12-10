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
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.spin.plugin.variable.type.SpinValueType;
import org.camunda.spin.plugin.variable.value.SpinValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for Spin XML variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Converts Spin XML variables to string format for C8 compatibility.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(13)  // Transform Spin XML - runs after validators and other transformers
@Component
public class SpinXmlVariableTransformer implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(SpinValue.class); // Only handle SpinValue types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();
    TypedValue typedValue = variable.getTypedValue(false);

    if (SpinValueType.XML.equals(typedValue.getType())) {
      logStartExecution(this.getClass(), variable.getName());
      invocation.setVariableValue(typedValue.getValue().toString());
      logEndExecution(this.getClass(), variable.getName());
    }
  }
}
