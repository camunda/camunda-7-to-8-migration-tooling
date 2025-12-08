/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaShutdownCleanerLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(SchemaShutdownCleanerLogs.class);

  public static final String SKIPPED_ENTITIES_COUNT = "[{}] entities were skipped during migration";
  public static final String SKIPPING_DROP = "Some entities were skipped during migration, enable `--force` to drop the migration schema";
  public static final String PERFORMING_DROP_ON_FORCE_ENABLED = "Some entities were skipped during migration but `--force` is enabled, dropping migration schema";
  public static final String PERFORMING_DROP_ON_SUCCESSFUL_MIGRATION = "Migration was completed without skipped entities, dropping migration schema";

  public static void logSkippedEntitiesCount(Long skipped) {
    LOGGER.info(SKIPPED_ENTITIES_COUNT, skipped);
  }

  public static void logSkippingDrop() {
    LOGGER.warn(SKIPPING_DROP);
  }

  public static void logForceDrop() {
    LOGGER.warn(PERFORMING_DROP_ON_FORCE_ENABLED);
  }

  public static void logSuccessfulMigrationDrop() {
    LOGGER.info(PERFORMING_DROP_ON_SUCCESSFUL_MIGRATION);
  }
}
