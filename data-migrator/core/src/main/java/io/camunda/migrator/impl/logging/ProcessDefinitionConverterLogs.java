/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

import io.camunda.migrator.converter.ProcessDefinitionConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for ProcessDefinitionConverter.
 * Contains all log messages and string constants used in ProcessDefinitionConverter.
 */
public class ProcessDefinitionConverterLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ProcessDefinitionConverter.class);

  // ProcessDefinitionConverter Error Messages
  public static final String FAILED_FETCHING_RESOURCE_STREAM = "Error while fetching resource stream for process definition with C7 ID [{}] due to: {}";

  public static void failedFetchingResourceStream(String c7Id, String errorMessage) {
    LOGGER.error(FAILED_FETCHING_RESOURCE_STREAM, c7Id, errorMessage);
  }
}
