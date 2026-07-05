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
   * Partition ID used for history data migration from Camunda 7 to Camunda 8.
   * Set to 4095 (maximum possible partition value) to ensure generated keys don't
   * collide with actual Zeebe partition keys during migration.
   */
  public static int C7_HISTORY_PARTITION_ID = 4095;

  public static final String LEGACY_ID_VAR_NAME = "legacyId";
  public static final String C8_DEFAULT_TENANT = "<default>";
  public static final String C7_LEGACY_PREFIX = "c7-legacy";

  /**
   * Default prefix prepended to Camunda 7 historical definition IDs (process, decision and form
   * definitions) during migration to Camunda 8. Prefixing avoids collisions between migrated
   * history definitions and native Camunda 8 definitions sharing the same ID. Configurable via
   * {@code camunda.migrator.history.legacy-id-prefix}.
   */
  public static final String DEFAULT_LEGACY_ID_PREFIX = C7_LEGACY_PREFIX + "-";
  public static final String C7_MULTI_INSTANCE_BODY_SUFFIX = "#multiInstanceBody";

  /**
   * Substituted for {@code null} on free-form identifier columns whose C8 contract is non-null
   * (e.g. {@code AuditLogEntity.entityKey} for entity-less audit log rows). Applies wherever the
   * migrator would otherwise emit {@code null}
   */
  public static final String C7_NULL_PLACEHOLDER = "C7_MIGRATED";

  /**
   * Sentinel value for IncidentEntity.errorMessage when the C7 incident message was null.
   * C8 enforces non-null on this field, so we substitute this placeholder to avoid NPEs.
   */
  public static final String C7_NO_MESSAGE = "C7_NO_MESSAGE";
}
