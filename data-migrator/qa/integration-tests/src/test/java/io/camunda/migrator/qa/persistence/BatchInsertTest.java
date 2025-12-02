/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.persistence;

import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BatchInsertTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Autowired
  private DbClient dbClient;

  @Autowired
  private MigratorProperties migratorProperties;

  @Test
  public void shouldBatchInsertRecordsWhenBatchSizeIsReached() {
    // given
    int batchSize = 5;
    migratorProperties.setBatchSize(batchSize);

    // when - insert records one by one
    for (int i = 0; i < batchSize - 1; i++) {
      dbClient.insert("c7-id-" + i, (long) i, new Date(), TYPE.RUNTIME_PROCESS_INSTANCE);
    }

    // then - records should not be flushed yet
    List<IdKeyDbModel> records = idKeyMapper.findMigratedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(records).isEmpty();
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(batchSize - 1);

    // when - insert one more record to reach batch size
    dbClient.insert("c7-id-" + (batchSize - 1), (long) (batchSize - 1), new Date(), TYPE.RUNTIME_PROCESS_INSTANCE);

    // then - all records should be flushed automatically
    records = idKeyMapper.findMigratedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(records).hasSize(batchSize);
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(0);
  }

  @Test
  public void shouldFlushRemainingRecordsOnManualFlush() {
    // given
    int batchSize = 10;
    migratorProperties.setBatchSize(batchSize);

    // when - insert fewer records than batch size
    int recordCount = 3;
    for (int i = 0; i < recordCount; i++) {
      dbClient.insert("c7-id-manual-" + i, (long) i, new Date(), TYPE.RUNTIME_PROCESS_INSTANCE);
    }

    // then - records should not be flushed yet
    List<IdKeyDbModel> records = idKeyMapper.findMigratedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(records).isEmpty();
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(recordCount);

    // when - manually flush
    dbClient.flushBatch();

    // then - all records should be flushed
    records = idKeyMapper.findMigratedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(records).hasSize(recordCount);
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(0);
  }

  @Test
  public void shouldHandleMultipleBatchFlushes() {
    // given
    int batchSize = 3;
    migratorProperties.setBatchSize(batchSize);

    // when - insert multiple batches worth of records
    int totalRecords = 10;
    for (int i = 0; i < totalRecords; i++) {
      dbClient.insert("c7-id-multi-" + i, (long) i, new Date(), TYPE.RUNTIME_PROCESS_INSTANCE);
    }

    // then - some batches should have been flushed automatically
    List<IdKeyDbModel> records = idKeyMapper.findMigratedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    int expectedFlushed = (totalRecords / batchSize) * batchSize;
    assertThat(records).hasSize(expectedFlushed);

    int expectedRemaining = totalRecords % batchSize;
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(expectedRemaining);

    // when - manually flush remaining
    dbClient.flushBatch();

    // then - all records should be persisted
    records = idKeyMapper.findMigratedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(records).hasSize(totalRecords);
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(0);
  }

  @Test
  public void shouldHandleEmptyBatchFlush() {
    // when - flush empty batch
    dbClient.flushBatch();

    // then - no error should occur
    assertThat(dbClient.getBatchBufferSize()).isEqualTo(0);
  }
}
