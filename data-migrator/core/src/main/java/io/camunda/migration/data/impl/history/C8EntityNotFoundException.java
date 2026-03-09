/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_DELETED_IN_C8;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_DELETED_IN_C8_ASSOCIATED;

import io.camunda.migration.data.impl.persistence.IdKeyMapper;

/**
 * Exception thrown when a C8 entity was expected to exist (based on existing mapping)
 * but could not be found via the C8 API. This typically happens when history cleanup
 * in C8 runs before the migration is completed.
 */
public class C8EntityNotFoundException extends RuntimeException {


  public C8EntityNotFoundException(IdKeyMapper.TYPE entityType, Long c8Key) {
    super(String.format(SKIP_REASON_DELETED_IN_C8, entityType.getDisplayName(), c8Key));
  }

  public C8EntityNotFoundException(IdKeyMapper.TYPE entityType, String c7ProcessInstanceId, String flowNodeId) {
    super(String.format(SKIP_REASON_DELETED_IN_C8_ASSOCIATED, entityType.getDisplayName(), c7ProcessInstanceId, flowNodeId));
  }
}

