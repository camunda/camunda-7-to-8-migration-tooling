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
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.JSON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for JSON Object variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Converts JSON Object variables to C8 compatible Map format.
 */
@Order(10)  // Transform JSON objects - runs after validators
@Component
public class ObjectJsonVariableTransformer implements VariableInterceptor {

  @Autowired
  protected ObjectMapper objectMapper;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(ObjectValue.class); // Handle ObjectValue types (will filter by serialization format in execute)
  }

  @Override
  public void execute(VariableContext context) {
    ObjectValue objectValue = (ObjectValue) context.getC7TypedValue();

    // Check if this is a JSON object
    if (JSON.getName().equals(objectValue.getSerializationDataFormat())) {
      String jsonValue = objectValue.getValueSerialized();

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
