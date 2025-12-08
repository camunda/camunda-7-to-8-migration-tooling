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
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.XML;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Set;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for XML Object variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Converts XML Object variables to raw string format for C8 compatibility.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(11)  // Transform XML objects - runs after validators
@Component
public class ObjectXmlVariableTransformer implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(ObjectValue.class); // Only handle ObjectValue types
  }

  @Override
  public void execute(VariableContext context) {
    ObjectValue objectValue = (ObjectValue) context.getC7TypedValue();

    if (XML.getName().equals(objectValue.getSerializationDataFormat())) {
      logStartExecution(this.getClass(), context.getName());

      String xmlValue = objectValue.getValueSerialized();
      context.setC8Value(xmlValue);
      logEndExecution(this.getClass(), context.getName());
    }
  }
}
