/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.interceptor;

import static io.camunda.migrator.impl.logging.VariableServiceLogs.JSON_DESERIALIZATION_ERROR;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.logEndExecution;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.logStartExecution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.spin.plugin.variable.type.SpinValueType;
import org.camunda.spin.plugin.variable.value.SpinValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for Spin JSON variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Converts Spin JSON variables to C8 compatible Map format.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(12)  // Transform Spin JSON - runs after validators and object transformers
@Component
public class SpinJsonVariableTransformer implements VariableInterceptor {

  @Autowired
  protected ObjectMapper objectMapper;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(SpinValue.class); // Only handle SpinValue types
  }

  @Override
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();
    TypedValue typedValue = variable.getTypedValue(false);

    if (SpinValueType.JSON.equals(typedValue.getType())) {
      logStartExecution(this.getClass(), variable.getName());
      setJsonVariable(invocation, typedValue.getValue().toString());
      logEndExecution(this.getClass(), variable.getName());
    }
  }

  protected void setJsonVariable(VariableInvocation invocation, String jsonString) {
    try {
      invocation.setVariableValue(objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {}));
    } catch (JsonProcessingException e) {
      throw new VariableInterceptorException(JSON_DESERIALIZATION_ERROR, e);
    }
  }
}
