/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;

import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.DateValue;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.PrimitiveValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.spin.plugin.variable.value.SpinValue;

/**
 * Utility class for determining variable type compatibility with interceptors using Camunda's native type system.
 * <p>
 * This class works with Camunda's existing variable value interfaces instead of custom enums,
 * providing better integration with the Camunda API.
 * </p>
 */
public final class VariableTypeDetector {

    protected VariableTypeDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if an interceptor supports a specific variable based on its typed value.
     *
     * @param interceptor the interceptor to check
     * @param invocation the variable invocation
     * @return true if the interceptor supports the variable type
     */
    public static boolean supportsVariable(VariableInterceptor interceptor, VariableInvocation invocation) {
        return supportsVariable(interceptor, invocation.getC7Variable());
    }

    /**
     * Checks if an interceptor supports a specific variable based on its typed value.
     *
     * @param interceptor the interceptor to check
     * @param variable the C7 variable instance
     * @return true if the interceptor supports the variable type
     */
    public static boolean supportsVariable(VariableInterceptor interceptor, VariableInstanceEntity variable) {
        TypedValue typedValue = variable.getTypedValue(false);
        return supportsTypedValue(interceptor, typedValue);
    }

    /**
     * Checks if an interceptor supports a specific typed value.
     *
     * @param interceptor the interceptor to check
     * @param typedValue the typed value to check
     * @return true if the interceptor supports the typed value
     */
    public static boolean supportsTypedValue(VariableInterceptor interceptor, TypedValue typedValue) {
        var supportedTypes = interceptor.getTypes();

        // Empty set means handle all types
        if (supportedTypes.isEmpty()) {
            return true;
        }

        // Check if any supported type matches the actual value type
        return supportedTypes.stream()
                .anyMatch(supportedType -> supportedType.isInstance(typedValue));
    }
}
