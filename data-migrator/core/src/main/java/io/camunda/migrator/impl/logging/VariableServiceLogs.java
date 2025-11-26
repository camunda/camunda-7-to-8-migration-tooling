/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.VariableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging and constants class for all variable-related operations.
 * Contains all logging statements, error messages, and constants used in variable processing and transformation.
 */
public class VariableServiceLogs {

  static final Logger LOGGER = LoggerFactory.getLogger(VariableService.class);

  // Date format constants
  public static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  // Log message templates
  public static final String START_EXECUTION_LOG = "Start {} execution for variable: {}";
  public static final String END_EXECUTION_LOG = "End {} execution for variable: {}";
  public static final String CONVERTING_DATE_LOG = "Converting date variable: {}";
  public static final String CONVERTED_DATE_LOG = "Converted date from variable with name {} from {} to {}";

  // Error message templates
  public static final String VARIABLE_INTERCEPTOR_FAILED_MSG = "%s failed for variable with name '%s'";

  // Error message constants (for exception throwing)
  public static final String BYTE_ARRAY_UNSUPPORTED_ERROR = "Variable of type 'byte[]' is unsupported in C8.";
  public static final String FILE_TYPE_UNSUPPORTED_ERROR = "Variable of type 'file' is unsupported in C8.";
  public static final String JAVA_SERIALIZED_UNSUPPORTED_ERROR = "Objects serialized as 'application/x-java-serialized-object' are unsupported in C8.";
  public static final String JSON_DESERIALIZATION_ERROR = "Error while deserializing JSON into Map type.";
  public static final String GENERIC_TYPE_UNSUPPORTED_ERROR = "Variable of type '%s' is unsupported in C8.";

  /**
   * Logs a warning message for variable interceptor failure.
   *
   * @param interceptorName the name of the failed interceptor
   * @param variableName the name of the variable being processed
   */
  public static void logInterceptorWarn(String interceptorName, String variableName) {
    LOGGER.warn(formatInterceptorWarn(interceptorName, variableName));
  }

  /**
   * Creates a formatted error message for variable interceptor failure.
   *
   * @param interceptorName the interceptor class name
   * @param variableName the variable name
   * @return formatted error message
   */
  public static String formatInterceptorWarn(String interceptorName, String variableName) {
    return String.format(VARIABLE_INTERCEPTOR_FAILED_MSG, interceptorName, variableName);
  }

  /**
   * Logs the start of variable transformation execution.
   *
   * @param transformerClass the transformer class
   * @param variableName the variable name being processed
   */
  public static void logStartExecution(Class<?> transformerClass, String variableName) {
    LOGGER.debug(START_EXECUTION_LOG, transformerClass, variableName);
  }

  /**
   * Logs the end of variable transformation execution.
   *
   * @param transformerClass the transformer class
   * @param variableName the variable name being processed
   */
  public static void logEndExecution(Class<?> transformerClass, String variableName) {
    LOGGER.debug(END_EXECUTION_LOG, transformerClass, variableName);
  }

  /**
   * Logs the start of date variable conversion.
   *
   * @param variableName the date variable name
   */
  public static void logConvertingDate(String variableName) {
    LOGGER.debug(CONVERTING_DATE_LOG, variableName);
  }

  /**
   * Logs the result of date conversion.
   *
   * @param name           the name of the date variable
   * @param originalValue  the original date value
   * @param formattedValue the formatted date string
   */
  public static void logConvertedDate(String name, Object originalValue, String formattedValue) {
    LOGGER.debug(CONVERTED_DATE_LOG, name, originalValue, formattedValue);
  }

}
