/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.example;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example variable interceptor that can be packaged as a JAR
 * and configured via config data file without Spring Boot annotations.
 *
 * This demonstrates:
 * - How to create a standalone interceptor
 * - How to handle configurable properties
 * - How to perform variable transformations
 */
public class MyCustomVariableInterceptor implements VariableInterceptor {

    protected static final Logger LOGGER = LoggerFactory.getLogger(MyCustomVariableInterceptor.class);

    // Configurable properties that can be set via YAML
    protected boolean enableLogging = true;
    protected String prefix = "CUSTOM_";

    @Override
    public void execute(VariableInvocation invocation) {
        VariableInstanceEntity variable = invocation.getC7Variable();
      TypedValue typedValue = variable.getTypedValue(false);
      if (enableLogging) {
            LOGGER.info("Processing variable: {} with value: {}",
                variable.getName(),
                typedValue.getValue());
        }

        if (ValueType.STRING.getName().equals(typedValue.getType().getName())) {
            Object originalValue = invocation.getMigrationVariable().getValue();
            if (originalValue != null) {
                String stringValue = originalValue.toString();
                invocation.setVariableValue(prefix + stringValue);

                if (enableLogging) {
                    LOGGER.info("Converted variable {} from {} to String: {}",
                        variable.getName(), variable.getValue(), stringValue);
                }
            }
        }

        if (enableLogging) {
            LOGGER.info("Finished processing variable: {} with transformed value: {}",
                invocation.getMigrationVariable().getName(),
                invocation.getMigrationVariable().getValue());
        }
    }

    // Setter methods for config data properties
    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    // Getter methods
    public boolean isEnableLogging() {
        return enableLogging;
    }

    public String getPrefix() {
        return prefix;
    }
}
