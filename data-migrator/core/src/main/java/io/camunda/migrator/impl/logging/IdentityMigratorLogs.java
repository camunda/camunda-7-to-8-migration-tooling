/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.IdentityMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentityMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(IdentityMigrator.class);

  public static final String MIGRATING_TENANT = "Migrating tenant [{}]";
  public static final String SUCCESSFULLY_MIGRATED_TENANT = "Successfully migrated tenant [{}]";
  public static final String SKIPPED_TENANT = "Tenant with ID [{}] was skipped";

  public static void logMigratingTenant(String tenantId) {
    LOGGER.debug(MIGRATING_TENANT, tenantId);
  }

  public static void logMigratedTenant(String tenantId) {
    LOGGER.info(SUCCESSFULLY_MIGRATED_TENANT, tenantId);
  }

  public static void logSkippedTenant(String tenantId) {
    LOGGER.warn(SKIPPED_TENANT, tenantId);
  }
}
