/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

import io.camunda.migrator.converter.VariableConverter;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for VariableConverter.
 * Contains all log messages and string constants used in VariableConverter.
 */
public class VariableConverterLogs {
  private static final Logger LOGGER = LoggerFactory.getLogger(VariableConverter.class);

  // VariableConverter Messages
  public static final String CONVERTING_OF_TYPE = "Converting variable with C7 ID [{}] of type: {}";

  // VariableConverter Error Messages
  public static final String WARN_NO_HANDLING_AVAILABLE = "No existing handling for variable with id= {}, type: {}, returning null.";
  public static final String ERROR_CONVERTING_JSON = "Error converting typed value to json: {}, exception: {}. Mapped to null";

  public static void convertingOfType(String c7Id, String type) {
    LOGGER.info(CONVERTING_OF_TYPE, c7Id, type);
  }

  public static void warnNoHandlingAvailable(String c7Id, String type) {
    LOGGER.warn(WARN_NO_HANDLING_AVAILABLE, c7Id, type);
  }

  public static void failedConvertingJson(ObjectValueImpl typedValue, String message) {
    LOGGER.error(ERROR_CONVERTING_JSON, typedValue, message);
  }
}
