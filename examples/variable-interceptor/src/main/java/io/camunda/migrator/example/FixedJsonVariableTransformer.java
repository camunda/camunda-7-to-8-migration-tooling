/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.example;

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
import org.camunda.spin.plugin.variable.value.impl.JsonValueImpl;

public class FixedJsonVariableTransformer implements VariableInterceptor {

  protected ObjectMapper objectMapper = new ObjectMapper();

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
      setJsonVariable(invocation, ((JsonValueImpl) typedValue).getValueSerialized());
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

