/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.INVALID_PARTITION_COUNT;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.NO_PARTITIONS_AVAILABLE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.PARTITION_COUNT_PROPERTY;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logFetchedPartitionsFromTopology;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logUsingConfiguredPartitionCount;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Supplies Zeebe partition IDs for history migration.
 *
 * <p>On initialization, fetches the available partitions from the Zeebe broker topology,
 * unless a partition count is explicitly configured via {@code camunda.migrator.history.partition-count}.
 * When a partition count is configured, that value takes precedence and the topology is not queried.
 *
 * <p>Migrators use this service to randomly assign history records to one of the available
 * partitions so that data is evenly distributed.
 *
 * <p>The assigned partition for each process instance is persisted in the {@code MIGRATION_MAPPING}
 * table so that child entity migrators (flow nodes, variables, tasks, etc.) can inherit the same
 * partition without holding an unbounded in-memory cache.
 */
@Component
public class PartitionSupplier {


  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected MigratorProperties migratorProperties;

  protected final SecureRandom random = new SecureRandom();

  protected volatile List<Integer> partitionIds;

  /**
   * Initializes the partition IDs for history migration.
   *
   * <p>If a partition count is configured via {@code camunda.migrator.history.partition-count},
   * partition IDs are generated as a sequence from 1 to the configured count.
   * Otherwise, the topology is queried from the Camunda REST API.
   *
   * <p>This method should be called before starting history migration to ensure
   * partition information is available. If topology cannot be queried and no
   * partition count is configured, this method throws an exception with guidance
   * on how to configure offline mode.
   *
   * @throws IllegalArgumentException if the configured partition count is less than 1
   * @throws IllegalStateException if no partitions are available from the topology
   */
  public void initialize() {
    Integer configuredPartitionCount = getConfiguredPartitionCount();
    if (configuredPartitionCount != null) {
      if (configuredPartitionCount < 1) {
        throw new IllegalArgumentException(
            String.format(INVALID_PARTITION_COUNT, configuredPartitionCount, PARTITION_COUNT_PROPERTY));
      }
      partitionIds = IntStream.rangeClosed(1, configuredPartitionCount)
          .boxed()
          .toList();
      logUsingConfiguredPartitionCount(configuredPartitionCount, partitionIds);
    } else {
      partitionIds = c8Client.fetchPartitionIds();
      if (partitionIds.isEmpty()) {
        throw new IllegalStateException(String.format(NO_PARTITIONS_AVAILABLE, PARTITION_COUNT_PROPERTY));
      }
      logFetchedPartitionsFromTopology(partitionIds.size(), partitionIds);
    }
  }

  /**
   * Returns a random partition ID from the available Zeebe partitions.
   *
   * <p>Partition IDs are fetched lazily from the topology on first use,
   * unless a partition count is configured.
   *
   * @return a randomly selected partition ID
   */
  public int getRandomPartitionId() {
    List<Integer> ids = getPartitionIds();
    return ids.get(random.nextInt(ids.size()));
  }

  /**
   * Returns the partition ID assigned to the given C7 root process instance.
   *
   * <p>Looks up the partition ID stored in the {@code MIGRATION_MAPPING} table when the root
   * process instance was migrated. All entities in a process hierarchy (sub-processes, call
   * activities) should use the same partition as their root process instance.
   *
   * <p>If {@code c7RootProcessInstanceId} is {@code null} (e.g. a standalone decision or audit log
   * not associated with any process instance), a random partition is returned instead.
   *
   * @param c7RootProcessInstanceId the Camunda 7 root process instance ID, or {@code null}
   * @return the assigned partition ID, or a random partition ID when no root process instance is given
   * @throws IllegalStateException if no partition ID is recorded for the given root process instance
   */
  public Integer getPartitionIdByRootProcessInstance(String c7RootProcessInstanceId) {
    if (c7RootProcessInstanceId == null) {
      return getRandomPartitionId();
    }
    return dbClient.findPartitionIdByC7IdAndType(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE);
  }

  /**
   * Returns the list of available Zeebe partition IDs.
   *
   * <p>If already initialized, returns the cached partition IDs.
   * Otherwise, initializes partition IDs (either from configuration or topology).
   *
   * @return unmodifiable list of partition IDs, never empty
   * @throws IllegalStateException if the topology cannot be fetched and no partition count is configured
   */
  public List<Integer> getPartitionIds() {
    if (partitionIds == null) {
      synchronized (this) {
        if (partitionIds == null) {
          initialize();
        }
      }
    }
    return partitionIds;
  }

  protected Integer getConfiguredPartitionCount() {
    if (migratorProperties.getHistory() != null) {
      return migratorProperties.getHistory().getPartitionCount();
    }
    return null;
  }

}
