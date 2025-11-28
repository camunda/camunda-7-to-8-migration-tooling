/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.history;

import io.camunda.migrator.impl.util.ConverterUtil;
import io.camunda.zeebe.protocol.Protocol;
import org.junit.jupiter.api.Test;

import static io.camunda.migrator.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migrator.impl.util.ConverterUtil.getUpperBound;
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
}
