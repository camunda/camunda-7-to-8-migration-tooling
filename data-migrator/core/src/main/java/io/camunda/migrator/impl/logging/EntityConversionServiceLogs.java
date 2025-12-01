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
 * Centralized logging class for all entity conversion operations.
 * Contains all logging statements and error messages used in entity conversion and interceptor execution.
 */
public class EntityConversionServiceLogs {

  private static final Logger LOGGER = LoggerFactory.getLogger(EntityConversionServiceLogs.class);

  // Log message templates
  private static final String EXECUTING_INTERCEPTOR_LOG = "Executing interceptor {} for entity type: {}";
  private static final String INTERCEPTOR_ERROR_LOG = "Interceptor {} failed for entity type: {}";

  // Error message templates
  private static final String ENTITY_INTERCEPTOR_FAILED_MSG = "%s failed for entity type '%s'";

  /**
   * Logs the execution of an interceptor for a specific entity type.
   *
   * @param interceptorName the name of the interceptor being executed
   * @param entityType the entity type being processed
   */
  public static void logExecutingInterceptor(String interceptorName, String entityType) {
    LOGGER.debug(EXECUTING_INTERCEPTOR_LOG, interceptorName, entityType);
  }

  /**
   * Logs an error when an interceptor fails.
   *
   * @param interceptorName the name of the failed interceptor
   * @param entityType the entity type being processed
   */
  public static void logInterceptorError(String interceptorName, String entityType) {
    LOGGER.error(INTERCEPTOR_ERROR_LOG, interceptorName, entityType);
  }

  /**
   * Creates a formatted error message for entity interceptor failure.
   *
   * @param interceptorName the interceptor class name
   * @param entityType the entity type
   * @return formatted error message
   */
  public static String formatInterceptorError(String interceptorName, String entityType) {
    return String.format(ENTITY_INTERCEPTOR_FAILED_MSG, interceptorName, entityType);
  }
}

