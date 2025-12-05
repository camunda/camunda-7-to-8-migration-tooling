/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor;

import static io.camunda.migration.data.impl.logging.VariableServiceLogs.JAVA_SERIALIZED_UNSUPPORTED_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.logStartExecution;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.JAVA;

import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validator for Java serialized object variables during migration from Camunda 7 to Camunda 8.
 * <p>
 * Validates that Java serialized objects are not used, as they are unsupported in Camunda 8.
 * Can be disabled via the configuration file using the {@code enabled} property.
 */
@Order(3)  // Run early - validate Java serialized objects before transformation
@Component
public class ObjectJavaVariableValidator implements VariableInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(ObjectValue.class); // Handle ObjectValue types (will filter by serialization format in execute)
  }

  @Override
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();
    ObjectValue objectValue = (ObjectValue) variable.getTypedValue(false);

    // Check if this is a Java serialized object
    if (JAVA.getName().equals(objectValue.getSerializationDataFormat())) {
      logStartExecution(this.getClass(), variable.getName());
      throw new VariableInterceptorException(JAVA_SERIALIZED_UNSUPPORTED_ERROR);
    }
  }
}
