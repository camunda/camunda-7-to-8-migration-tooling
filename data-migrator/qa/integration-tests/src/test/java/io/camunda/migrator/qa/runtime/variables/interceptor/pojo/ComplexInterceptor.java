/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.variables.interceptor.pojo;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComplexInterceptor implements VariableInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ComplexInterceptor.class);

  // Configurable properties that can be set declaratively
  private String logMessage = "Hello from declarative interceptor configured via properties";
  private boolean enableTransformation = true;
  private String targetVariable = "var";

  @Override
  public void execute(VariableInvocation invocation) {
    LOGGER.debug("Start {} execution for variable: {}", ComplexInterceptor.class,
        invocation.getC7Variable().getName());

    String variableName = invocation.getC7Variable().getName();

    if (targetVariable.equals(variableName)) {
      LOGGER.info(logMessage);

      if (enableTransformation) {
        String originalValue = String.valueOf(invocation.getC7Variable().getValue());
        String transformedValue = "transformedValue";
        invocation.setVariableValue(transformedValue);
        LOGGER.info("Transformed variable {} from '{}' to '{}'", variableName, originalValue, transformedValue);
      }
    }

    // Handle exception testing scenario
    if ("exFlag".equals(variableName)) {
      if (Boolean.parseBoolean(invocation.getC7Variable().getValue().toString())) {
        throw new RuntimeException("Expected exception from interceptor");
      } else {
        LOGGER.info("Success from interceptor");
      }
    }

    LOGGER.debug("End {} execution for variable: {}", ComplexInterceptor.class,
        invocation.getC7Variable().getName());
  }

  // Setter methods for configuration
  public void setLogMessage(String logMessage) {
    this.logMessage = logMessage;
  }

  public void setEnableTransformation(boolean enableTransformation) {
    this.enableTransformation = enableTransformation;
  }

  public void setTargetVariable(String targetVariable) {
    this.targetVariable = targetVariable;
  }

  // Getter methods for testing
  public String getLogMessage() {
    return logMessage;
  }

  public boolean isEnableTransformation() {
    return enableTransformation;
  }

  public String getTargetVariable() {
    return targetVariable;
  }
}
