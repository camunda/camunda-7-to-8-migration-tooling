/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for Configuration class.
 * Contains all log messages and string constants used in configuration.
 */
public class ConfigurationLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationLogs.class);

  // Error Messages
  public static final String ERROR_CLASS_NAME_NULL_OR_EMPTY = "Variable interceptor class name cannot be null or empty";
  public static final String ERROR_CLASS_NOT_IMPLEMENT_INTERFACE = "Class %s does not implement VariableInterceptor interface";
  public static final String ERROR_FAILED_TO_REGISTER = "Failed to register variable interceptor: ";
  public static final String ERROR_PARSING_CONFIGURATION = "An exception occurred while parsing interceptor configuration.";
  public static final String ERROR_C8_RDBMS_USER_CHAR_COLUMN_SIZE = "Could not determine userCharColumnSize for C8 database schema creation.";

  // Info Messages
  public static final String INFO_CONFIGURING_INTERCEPTORS = "Configuring variable interceptors";
  public static final String INFO_TOTAL_INTERCEPTORS_CONFIGURED = "In total {} variable interceptors configured";
  public static final String INFO_SUCCESSFULLY_REGISTERED = "Successfully registered variable interceptor: {}";
  public static final String INFO_LIQUIBASE_CREATING_TABLE_SCHEMA = "Creating table schema with Liquibase change log file '{}' with table prefix '{}'.";

  // Debug Messages
  public static final String DEBUG_NO_INTERCEPTORS = "No variable interceptors configured in configuration file";
  public static final String DEBUG_CREATING_INSTANCE = "Creating variable interceptor instance for class: {}";
  public static final String DEBUG_SETTING_PROPERTIES = "Setting properties for variable interceptor: {}";

  /**
   * Logs the start of interceptor configuration process.
   */
  public static void logConfiguringInterceptors() {
    LOGGER.info(INFO_CONFIGURING_INTERCEPTORS);
  }

  /**
   * Logs the total number of configured interceptors.
   *
   * @param count the number of configured interceptors
   */
  public static void logTotalInterceptorsConfigured(int count) {
    LOGGER.info(INFO_TOTAL_INTERCEPTORS_CONFIGURED, count);
  }

  /**
   * Logs when no interceptors are configured in config file.
   */
  public static void logNoInterceptorsConfigured() {
    LOGGER.debug(DEBUG_NO_INTERCEPTORS);
  }

  /**
   * Logs successful registration of an interceptor.
   *
   * @param className the class name of the registered interceptor
   */
  public static void logSuccessfullyRegistered(String className) {
    LOGGER.info(INFO_SUCCESSFULLY_REGISTERED, className);
  }

  /**
   * Logs failure to register an interceptor.
   *
   * @param className the class name of the failed interceptor
   * @param exception the exception that occurred
   */
  public static void logFailedToRegister(String className, Exception exception) {
    LOGGER.error(ERROR_FAILED_TO_REGISTER + className, exception);
  }

  /**
   * Logs the creation of an interceptor instance.
   *
   * @param className the class name of the interceptor being created
   */
  public static void logCreatingInstance(String className) {
    LOGGER.debug(DEBUG_CREATING_INSTANCE, className);
  }

  /**
   * Logs when setting properties for an interceptor.
   *
   * @param className the class name of the interceptor
   */
  public static void logSettingProperties(String className) {
    LOGGER.debug(DEBUG_SETTING_PROPERTIES, className);
  }

  /**
   * Logs when any interceptor is disabled via configuration.
   *
   * @param className the class name of the disabled interceptor
   */
  public static void logInterceptorDisabled(String className) {
    LOGGER.info("Interceptor disabled via configuration: {}", className);
  }

  /**
   * Logs when an interceptor is already loaded and doesn't need to be created again.
   *
   * @param className the class name of the interceptor already loaded
   */
  public static void logInterceptorAlreadyLoaded(String className) {
    LOGGER.debug("Interceptor already loaded: {}", className);
  }

  /**
   * Logs when an interceptor specified for disabling was not found in the context.
   *
   * @param className the class name of the interceptor not found
   */
  public static void logInterceptorNotFoundForDisabling(String className) {
    LOGGER.warn("Interceptor specified for disabling not found in context: {}", className);
  }

  /**
   * Logs when creating table schema properties for an interceptor.
   *
   * @param changeLogFile the changeLog file used for Liquibase
   * @param tablePrefix   the prefix for the tables
   */
  public static void logCreatingTableSchema(String changeLogFile, String tablePrefix) {
    LOGGER.info(INFO_LIQUIBASE_CREATING_TABLE_SCHEMA, changeLogFile, tablePrefix);
  }

  /**
   * Gets the error message for null or empty class name.
   *
   * @return the error message
   */
  public static String getClassNameNullOrEmptyError() {
    return ERROR_CLASS_NAME_NULL_OR_EMPTY;
  }

  /**
   * Gets the error message for class not implementing interface.
   *
   * @param className the class name
   * @return the formatted error message
   */
  public static String getClassNotImplementInterfaceError(String className) {
    return String.format(ERROR_CLASS_NOT_IMPLEMENT_INTERFACE, className);
  }

  /**
   * Gets the error message for failed registration.
   *
   * @param className the class name
   * @return the formatted error message
   */
  public static String getFailedToRegisterError(String className) {
    return ERROR_FAILED_TO_REGISTER + className;
  }

  /**
   * Gets the error message for parsing configuration.
   *
   * @return the error message
   */
  public static String getParsingConfigurationError() {
    return ERROR_PARSING_CONFIGURATION;
  }

  /**
   * Gets the error message for C8 RDBMS user char column size.
   *
   * @return the error message
   */
  public static String getC8RdbmsUserCharColumnSizeError() {
    return ERROR_C8_RDBMS_USER_CHAR_COLUMN_SIZE;
  }

}
