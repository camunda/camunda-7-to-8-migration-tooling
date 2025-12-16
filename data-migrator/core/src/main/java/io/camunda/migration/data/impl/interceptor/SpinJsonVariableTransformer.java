/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor;

import static io.camunda.migration.data.impl.logging.VariableServiceLogs.JSON_DESERIALIZATION_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.logEndExecution;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.logStartExecution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Map;
import java.util.Set;
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
  public void execute(VariableContext context) {
    TypedValue typedValue = context.getC7TypedValue();

    if (SpinValueType.JSON.equals(typedValue.getType())) {
      logStartExecution(this.getClass(), context.getName());

      String jsonValue = typedValue.getValue().toString();

      if (context.isHistory()) {
        context.setC8Value(jsonValue);

      } else if (context.isRuntime()) {
        setJsonVariable(context, jsonValue);

      }

      logEndExecution(this.getClass(), context.getName());
    }
  }

  protected void setJsonVariable(VariableContext context, String jsonString) {
    try {
      context.setC8Value(objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {}));
    } catch (JsonProcessingException e) {
      throw new VariableInterceptorException(JSON_DESERIALIZATION_ERROR, e);
    }
  }
}
