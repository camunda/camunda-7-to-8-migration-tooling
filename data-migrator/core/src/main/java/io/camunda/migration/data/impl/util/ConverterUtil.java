/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.util;

import io.camunda.zeebe.protocol.Protocol;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.constants.MigratorConstants.C7_MULTI_INSTANCE_BODY_SUFFIX;
import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.constants.MigratorConstants.DEFAULT_LEGACY_ID_PREFIX;
import static io.camunda.zeebe.protocol.Protocol.KEY_BITS;

public class ConverterUtil {

  public static Long getNextKey() {
    SecureRandom secureRandom = new SecureRandom();
    return Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, secureRandom.nextLong(getUpperBound() + 1));
  }

  public static long getUpperBound() {
    return (1L << KEY_BITS) - 1;
  }

  public static OffsetDateTime convertDate(Date date) {
    if (date == null) return null;
    return date.toInstant().atOffset(ZoneOffset.UTC);
  }

  public static String getTenantId(String c7TenantId) {
    return StringUtils.isEmpty(c7TenantId) ? C8_DEFAULT_TENANT : c7TenantId;
  }

  /**
   * Prefixes a Camunda 7 definition ID with the {@link io.camunda.migration.data.constants.MigratorConstants#DEFAULT_LEGACY_ID_PREFIX default}
   * legacy prefix.
   * <p>
   * Production code migrates definition IDs through
   * {@link io.camunda.migration.data.impl.util.LegacyIdPrefixResolver}, which honours the
   * configurable {@code camunda.migrator.history.legacy-id-prefix} property. This helper always
   * uses the default prefix and is retained for building expected values in tests running with the
   * default configuration.
   *
   * @param definitionId the original Camunda 7 definition ID, may be null
   * @return the prefixed definition ID, or null if the input was null
   */
  public static String prefixDefinitionId(String definitionId) {
    if (definitionId == null) {
      return null;
    }
    return DEFAULT_LEGACY_ID_PREFIX + definitionId;
  }

  /**
   * Removes the multi-instance body suffix from the activity ID if present, as C8 does not use this convention for multi-instance activities.
   */
  public static String sanitizeFlowNodeId(String activityId) {
    return activityId.replace(C7_MULTI_INSTANCE_BODY_SUFFIX, "");
  }

}
