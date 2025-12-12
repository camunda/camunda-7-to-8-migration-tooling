/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import org.camunda.bpm.engine.variable.value.TypedValue;

/**
 * Utility class for determining variable type compatibility with interceptors using Camunda's native type system.
 * <p>
 * This class works with Camunda's existing variable value interfaces instead of custom enums,
 * providing better integration with the Camunda API.
 * </p>
 */
public class VariableTypeDetector {

  /**
   * Checks if an interceptor supports a specific variable based on its typed value.
   *
   * @param interceptor the interceptor to check
   * @param context  the variable context
   * @return true if the interceptor supports the variable type
   */
  public static boolean supportsVariable(VariableInterceptor interceptor, VariableContext context) {
    var supportedTypes = interceptor.getTypes();
    // Empty set means handle all types
    if (supportedTypes.isEmpty()) {
      return true;
    }

    TypedValue typedValue = context.getC7TypedValue();
    // Check if any supported type matches the actual value type
    return supportedTypes.stream().anyMatch(supportedType -> supportedType.isInstance(typedValue));
  }

}
