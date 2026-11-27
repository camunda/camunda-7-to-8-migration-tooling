/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static io.camunda.client.api.response.PartitionBrokerHealth.HEALTHY;
import static io.camunda.client.api.response.PartitionBrokerHealth.UNHEALTHY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class CamundaClusterHealthCheckTest {

  @Test
  public void shouldReportHealthyWhenEveryPartitionHasAHealthyLeader() {
    assertThat(CamundaClusterHealthCheck.isHealthy(2,
        List.of(partition(1, true, HEALTHY), partition(2, true, HEALTHY))))
        .isTrue();
  }

  @Test
  public void shouldReportUnhealthyWhenAnyPartitionLeaderIsUnhealthy() {
    assertThat(CamundaClusterHealthCheck.isHealthy(2,
        List.of(partition(1, true, HEALTHY), partition(2, true, UNHEALTHY))))
        .isFalse();
  }

  @Test
  public void shouldReportUnhealthyWhenAnyPartitionHasNoLeader() {
    assertThat(CamundaClusterHealthCheck.isHealthy(1, List.of(partition(1, false, HEALTHY))))
        .isFalse();
  }

  @Test
  public void shouldRetryUntilEveryPartitionLeaderIsHealthy() {
    AtomicInteger checks = new AtomicInteger();

    CamundaClusterHealthCheck.awaitHealthy(() -> checks.getAndIncrement() > 0);

    assertThat(checks.get()).isEqualTo(2);
  }

  protected PartitionInfo partition(int id, boolean leader, PartitionBrokerHealth health) {
    return new TestPartition(id, leader, health);
  }

  protected static class TestPartition implements PartitionInfo {

    protected final int id;
    protected final boolean leader;
    protected final PartitionBrokerHealth health;

    protected TestPartition(int id, boolean leader, PartitionBrokerHealth health) {
      this.id = id;
      this.leader = leader;
      this.health = health;
    }

    @Override
    public int getPartitionId() {
      return id;
    }

    @Override
    public PartitionBrokerRole getRole() {
      return leader ? PartitionBrokerRole.LEADER : PartitionBrokerRole.FOLLOWER;
    }

    @Override
    public boolean isLeader() {
      return leader;
    }

    @Override
    public PartitionBrokerHealth getHealth() {
      return health;
    }
  }
}
