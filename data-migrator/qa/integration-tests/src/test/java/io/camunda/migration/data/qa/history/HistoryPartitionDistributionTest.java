/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests verifying that history migration distributes migrated entities across
 * multiple Zeebe partitions when {@code camunda.migrator.history.partition-count} is configured
 * to a value greater than 1.
 */
@TestPropertySource(properties = {
    "camunda.migrator.history.partition-count=3"
})
public class HistoryPartitionDistributionTest extends HistoryMigrationAbstractTest {

  private static final int PARTITION_COUNT = 3;
  private static final int PROCESS_INSTANCE_COUNT = 20;

  @Test
  public void shouldDistributeProcessInstancesAcrossPartitionsWhenMultiplePartitionsConfigured() {
    // given – create multiple process instances so random assignment spans multiple partitions
    deployer.deployCamunda7Process("simpleStartEndProcess.bpmn");
    for (int i = 0; i < PROCESS_INSTANCE_COUNT; i++) {
      runtimeService.startProcessInstanceByKey("simpleStartEndProcessId");
    }

    // when
    historyMigrator.migrate();

    // then – all process instances should be migrated
    List<ProcessInstanceEntity> migratedInstances =
        searchHistoricProcessInstances("simpleStartEndProcessId");
    assertThat(migratedInstances).hasSize(PROCESS_INSTANCE_COUNT);

    // collect the partition IDs assigned to each process instance
    Set<Integer> usedPartitions = new HashSet<>();
    for (ProcessInstanceEntity instance : migratedInstances) {
      int partitionId = searchProcessInstancePartitionId(instance.processInstanceKey());
      assertThat(partitionId).isBetween(1, PARTITION_COUNT);
      usedPartitions.add(partitionId);
    }

    // with 20 instances across 3 partitions, the probability of all landing on one partition is
    // negligible (< 0.01%); assert that at least 2 distinct partitions were used
    assertThat(usedPartitions)
        .as("Expected process instances to be distributed across multiple partitions")
        .hasSizeGreaterThan(1);
  }
}
