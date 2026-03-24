/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

/**
 * Result of a successful entity migration, containing the C8 key and optional metadata.
 *
 * @param c8Key the generated Camunda 8 key for the migrated entity
 * @param partitionId the partition ID assigned to the entity (only relevant for process instances)
 */
public record MigrationResult(String c8Key, Integer partitionId) {

  /**
   * Creates a migration result with only a key (no partition ID).
   */
  public static MigrationResult of(Long c8Key) {
    return new MigrationResult(c8Key != null ? c8Key.toString() : null, null);
  }

  /**
   * Creates a migration result with only a key (no partition ID).
   */
  public static MigrationResult of(String c8Key) {
    return new MigrationResult(c8Key, null);
  }

  /**
   * Creates a migration result with a key and partition ID.
   */
  public static MigrationResult of(Long c8Key, int partitionId) {
    return new MigrationResult(c8Key != null ? c8Key.toString() : null, partitionId);
  }
}

