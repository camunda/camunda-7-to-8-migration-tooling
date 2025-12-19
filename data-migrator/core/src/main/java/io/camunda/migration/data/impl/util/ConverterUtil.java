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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
   *
   * C7 stores timestamps WITHOUT timezone (wall-clock time only).
   * When JDBC reads these timestamps, it incorrectly interprets them as being in the JVM's timezone
   * and converts them to UTC. We need to undo this interpretation to preserve the original wall-clock time.
   * Example: C7 has "2024-01-15 10:30:00" (no timezone)
   * - JDBC in Europe/Berlin (UTC+1) reads this as "2024-01-15 10:30:00+01:00"
   * - Converts to Date representing instant "2024-01-15 09:30:00Z"
   * - We need to get back to "2024-01-15 10:30:00" and store it as UTC in C8
   * Solution: Extract the wall-clock time in JVM timezone, then treat it as UTC.
   */
  public static OffsetDateTime convertDate(Date date) {
    if (date == null) return null;
    LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    return localDateTime.atOffset(ZoneOffset.UTC);
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
