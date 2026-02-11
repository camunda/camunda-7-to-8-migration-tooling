/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.history;

import io.camunda.migration.data.impl.util.ConverterUtil;
import io.camunda.zeebe.protocol.Protocol;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.getUpperBound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConverterUtilTest {

  @Test
  public void shouldGenerateMaximumPossibleKeyForPartition() {
      // given
      long rawMaxKey = ConverterUtil.getUpperBound();

      // when
      long encodedMaxKey = Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, rawMaxKey);

      // then 
      assertEquals(Long.MAX_VALUE, encodedMaxKey);
      assertEquals(rawMaxKey, Protocol.decodeKeyInPartition(encodedMaxKey));
      assertEquals(C7_HISTORY_PARTITION_ID, Protocol.decodePartitionId(encodedMaxKey));
  }

  @Test
  public void shouldGenerateMinimumPossibleKeyForPartition() {
      // given
      long rawMinKey = 0;

      // when
      long encodedMinKey = Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, rawMinKey);

      // then
      assertEquals(rawMinKey, Protocol.decodeKeyInPartition(encodedMinKey));
      assertEquals(C7_HISTORY_PARTITION_ID, Protocol.decodePartitionId(encodedMinKey));
  }

  @Test
  public void shouldThrowExceptionWhenKeyExceedsUpperBound() {
      // given a value exceeding the upper bound
      long rawInvalidKey = getUpperBound() + 1;

      // when/then
      assertThat(rawInvalidKey)
          .withFailMessage("Expected key to exceed upper bound")
          .isGreaterThan(getUpperBound());

      assertThat(
          assertThrows(
              IllegalArgumentException.class,
              () -> Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, rawInvalidKey)
          )
      ).hasMessageContaining("Expected that the provided value is smaller");
  }

  @ParameterizedTest
  @CsvSource(value = {"Europe/Berlin,2024-01-15T13:00Z", "Asia/Bangkok,2024-01-15T07:00Z"}, nullValues = "null")
  public void shouldConvertDateToUtc(String timezone, String expectedUtc) {
    // given
    LocalDateTime wallClock = LocalDateTime.of(2024, 1, 15, 14, 0, 0);
    Date berlinTimezoneDate = Date.from(wallClock.atZone(ZoneId.of(timezone)).toInstant());

    // when
    OffsetDateTime result = ConverterUtil.convertDate(berlinTimezoneDate);

    // then
    assertThat(result.toString()).isEqualTo(expectedUtc);
  }

  @Test
  public void shouldReturnNullForNullDateConversion() {
    assertThat(ConverterUtil.convertDate(null)).isNull();
  }

  @ParameterizedTest
  @CsvSource(value = {"null,<default>", "tenant1,tenant1"}, nullValues = "null")
  public void shouldConvertC7Tenants(String c7Tenant, String c8Tenant) {
    assertThat(ConverterUtil.getTenantId(c7Tenant)).isEqualTo(c8Tenant);
  }

  @ParameterizedTest
  @CsvSource(value = {"null,null", "defId,c7-legacy-defId"}, nullValues = "null")
  public void shouldPrefixDefinitionIds(String c7Tenant, String c8Tenant) {
    assertThat(ConverterUtil.prefixDefinitionId(c7Tenant)).isEqualTo(c8Tenant);
  }
}
