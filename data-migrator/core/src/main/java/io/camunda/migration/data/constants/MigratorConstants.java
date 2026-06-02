/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.constants;

public final class MigratorConstants {

  protected MigratorConstants() {}

  /**
   * Partition ID encoded in generated C8 keys for C7 history data.
   * Set to 4095 (maximum possible partition value) to ensure generated keys don't
   * collide with actual Zeebe partition keys during migration.
   */
  public static int C7_HISTORY_PARTITION_ID = 4095;

  public static int C7_AUDIT_LOG_ENTITY_VERSION = -4095;

  public static final String LEGACY_ID_VAR_NAME = "legacyId";
  public static final String C8_DEFAULT_TENANT = "<default>";
  public static final String C7_LEGACY_PREFIX = "c7-legacy";
  public static final String C7_MULTI_INSTANCE_BODY_SUFFIX = "#multiInstanceBody";

  /**
   * Placeholder written to non-null C8 columns when the corresponding C7 source value is missing.
   * Used to satisfy non-null contracts (e.g. {@code AuditLogEntity.entityKey}) for rows that have
   * no natural target value in C7.
   */
  public static final String NO_C7_VALUE_MIGRATED_PLACEHOLDER = "C7_MIGRATED";
}
