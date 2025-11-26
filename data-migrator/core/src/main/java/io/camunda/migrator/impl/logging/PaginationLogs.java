/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for Pagination.
 * Contains all log messages and string constants used in Pagination.
 */
public class PaginationLogs {
  protected static final Logger LOGGER = LoggerFactory.getLogger(Pagination.class);

  // Pagination Messages
  public static final String PAGINATION_DEBUG_INFO = "Method: #{}, max count: {}, offset: {}, page size: {}";

  // Pagination Error Messages
  public static final String ERROR_QUERY_AND_PAGE_NULL = "Query and page cannot be null";

  public static void paginationDebugInfo(String methodName, Long maxCount, int offset, int pageSize) {
    LOGGER.debug(PAGINATION_DEBUG_INFO, methodName, maxCount, offset, pageSize);
  }

  public static void errorQueryAndPageNull() {
    LOGGER.error(ERROR_QUERY_AND_PAGE_NULL);
  }
}
