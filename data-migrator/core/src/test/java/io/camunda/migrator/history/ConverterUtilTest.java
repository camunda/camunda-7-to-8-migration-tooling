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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

  /*@Test
  public void shouldDemonstrateWhyPartitionIdMustNotExceedLimit() {
      // given a value exceeding the upper bound
      long rawInvalidKey = getUpperBound() + 1;

      // when
      long encodedInvalidKey = Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, rawInvalidKey);

      // then both the key and partition ID are corrupted during encoding/decoding
      long decodedKey = Protocol.decodeKeyInPartition(encodedInvalidKey);
      int decodedPartitionId = Protocol.decodePartitionId(encodedInvalidKey);

      assertNotEquals(rawInvalidKey, decodedKey,
          "Key should not be preserved when exceeding upper bound");
      assertNotEquals(C7_HISTORY_PARTITION_ID, decodedPartitionId,
          "Partition ID should not be preserved when key exceeds upper bound");
  }*/
}
