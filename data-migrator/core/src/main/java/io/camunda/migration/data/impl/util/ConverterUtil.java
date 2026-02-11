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
import java.time.ZoneId;
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
   * Converts a {@link Date} to an {@link OffsetDateTime} using the system's default time zone.
   * <p>
   * In C8 RDBMS dates are stored with timezone information {@code TIMESTAMP WITH TIME ZONE}.
   * This conversion ensures, no matter in which timezone the data migrator reads the date, it always
   * ensures the timezone information is passed and stores the correct timestamp with timezone.
   *
   * @param date the date to convert, may be null
   * @return the converted {@link OffsetDateTime}, or null if the input date is null
   */
  public static OffsetDateTime convertDate(Date date) {
    if (date == null) return null;
    return OffsetDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
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
