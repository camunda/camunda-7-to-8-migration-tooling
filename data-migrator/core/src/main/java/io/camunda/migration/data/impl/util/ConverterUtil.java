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
import static io.camunda.migration.data.constants.MigratorConstants.C7_LEGACY_PREFIX;
import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.zeebe.protocol.Protocol.KEY_BITS;

public class ConverterUtil {

  public static Long getNextKey() {
    SecureRandom secureRandom = new SecureRandom();
    return Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, secureRandom.nextLong(getUpperBound() + 1));
  }

  public static long getUpperBound() {
    return (1L << KEY_BITS) - 1;
  }

  /**
   * Converts C7 Date to C8 OffsetDateTime in UTC.
   * C7 stores timestamps without timezone. JDBC interprets them based on JVM timezone when reading.
   * This method preserves the instant that JDBC provides, marking it explicitly as UTC.
   * <p>
   * Note: If migrator is not run with the same timezone as C7, timezone must be set before running the migrator to
   * match the original C7 timezone and ensure correct timestamp migration.
   * <p>
   * Example:
   * Original C7 timezone was Asia/Bangkok, and the migrator is now run in Europe/Berlin.
   * To ensure correct timestamp migration, set the timezone to Asia/Bangkok before running the migrator:
   * export TZ=Asia/Bangkok
   * ./start.sh [--options]
   */
  public static OffsetDateTime convertDate(Date date) {
    return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
  }

  public static String getTenantId(String c7TenantId) {
    return StringUtils.isEmpty(c7TenantId) ? C8_DEFAULT_TENANT : c7TenantId;
  }

  public static String prefixDefinitionId(String definitionId) {
    if (definitionId == null) {
      return null;
    }
    return String.format("%s-%s", C7_LEGACY_PREFIX, definitionId);
  }

}
