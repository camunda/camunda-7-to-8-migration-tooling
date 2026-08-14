/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property.history;

/**
 * Configuration properties for history migration.
 */
public class HistoryProperties {

  protected AutoCancelProperties autoCancel;

  /**
   * The number of partitions to use for distributing history data
   * When set, this value takes precedence over querying the topology from the Camunda REST API.
   * This enables "offline mode" where the migrator can run without connectivity to Camunda 8.
   */
  protected Integer partitionCount;

  /**
   * Prefix prepended to Camunda 7 historical definition IDs (process, decision and form
   * definitions) when they are migrated to Camunda 8.
   * <p>
   * Prefixing avoids collisions between migrated history definitions and native Camunda 8
   * definitions that share the same ID. When {@code null} (not configured) the default
   * {@code c7-legacy-} is used. When explicitly configured the value is validated: it must not be
   * blank, must not exceed the maximum length and may only contain characters that keep the
   * resulting definition IDs valid. Resolution and validation are handled by
   * {@code io.camunda.migration.data.impl.util.LegacyIdPrefixResolver}.
   */
  protected String legacyIdPrefix;

  public AutoCancelProperties getAutoCancel() {
    return autoCancel;
  }

  public void setAutoCancel(AutoCancelProperties autoCancel) {
    this.autoCancel = autoCancel;
  }

  public Integer getPartitionCount() {
    return partitionCount;
  }

  public void setPartitionCount(Integer partitionCount) {
    this.partitionCount = partitionCount;
  }

  public String getLegacyIdPrefix() {
    return legacyIdPrefix;
  }

  public void setLegacyIdPrefix(String legacyIdPrefix) {
    this.legacyIdPrefix = legacyIdPrefix;
  }

}
