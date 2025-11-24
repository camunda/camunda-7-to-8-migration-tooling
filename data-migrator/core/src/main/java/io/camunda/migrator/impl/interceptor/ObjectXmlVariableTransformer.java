/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.interceptor;

import static io.camunda.migrator.impl.logging.VariableServiceLogs.logEndExecution;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.logStartExecution;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.XML;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
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
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();
    ObjectValue objectValue = (ObjectValue) variable.getTypedValue(false);

    if (XML.getName().equals(objectValue.getSerializationDataFormat())) {

      logStartExecution(this.getClass(), variable.getName());
      invocation.setVariableValue(objectValue.getValueSerialized());
      logEndExecution(this.getClass(), variable.getName());
    }
  }
}
