/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables.interceptor.pojo;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComplexInterceptor implements VariableInterceptor {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ComplexInterceptor.class);

  // Configurable properties that can be set declaratively
  protected String logMessage = "Hello from declarative interceptor configured via properties";
  protected boolean enableTransformation = true;
  protected String targetVariable = "var";

  @Override
  public void execute(VariableContext context) {
    LOGGER.debug("Start {} execution for variable: {}", ComplexInterceptor.class, context.getName());

    String variableName = context.getName();
    if (targetVariable.equals(variableName)) {
      LOGGER.info(logMessage);

      if (enableTransformation) {
        String originalValue = String.valueOf(context.getC7Value());
        String transformedValue = "transformedValue";
        context.setC8Value(transformedValue);
        LOGGER.info("Transformed variable {} from '{}' to '{}'", variableName, originalValue, transformedValue);
      }
    }

    // Handle exception testing scenario
    if ("exFlag".equals(variableName)) {
      if ((boolean) context.getC7Value()) {
        throw new RuntimeException("Expected exception from interceptor");
      } else {
        LOGGER.info("Success from interceptor");
      }
    }

    LOGGER.debug("End {} execution for variable: {}", ComplexInterceptor.class, context.getName());
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
