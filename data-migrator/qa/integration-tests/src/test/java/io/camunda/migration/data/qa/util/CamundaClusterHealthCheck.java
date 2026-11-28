/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.awaitility.Awaitility;

public final class CamundaClusterHealthCheck {

  protected static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(60);
  protected static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(2);

  protected CamundaClusterHealthCheck() {
  }

  public static void awaitHealthy(BooleanSupplier healthCheck) {
    Awaitility.await()
        .atMost(HEALTH_CHECK_TIMEOUT)
        .pollDelay(Duration.ZERO)
        .pollInterval(HEALTH_CHECK_INTERVAL)
        .ignoreExceptionsInstanceOf(ClientException.class)
        .until(healthCheck::getAsBoolean);
  }

  public static boolean isHealthy(Topology topology) {
    List<PartitionInfo> partitions = topology.getBrokers().stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .toList();
    return isHealthy(topology.getPartitionsCount(), partitions);
  }

  protected static boolean isHealthy(int partitionCount, List<PartitionInfo> partitions) {
    List<PartitionInfo> leaders = partitions.stream().filter(PartitionInfo::isLeader).toList();
    return partitionCount > 0
        && leaders.size() == partitionCount
        && leaders.stream()
            .allMatch(partition -> partition.getHealth() == PartitionBrokerHealth.HEALTHY);
  }
}
