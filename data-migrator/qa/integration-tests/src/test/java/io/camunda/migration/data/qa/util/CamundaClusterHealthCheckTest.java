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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

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

  @Test
  public void shouldCheckHealthyClusterImmediately() {
    long startedAt = System.nanoTime();

    CamundaClusterHealthCheck.awaitHealthy(() -> true);

    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
        .isLessThan(CamundaClusterHealthCheck.HEALTH_CHECK_INTERVAL);
  }

  @Test
  public void shouldRetryWhenClientExceptionSubclassIsThrown() {
    AtomicInteger checks = new AtomicInteger();

    CamundaClusterHealthCheck.awaitHealthy(() -> {
      if (checks.getAndIncrement() == 0) {
        throw new InternalClientException("Transient topology request failure");
      }
      return true;
    });

    assertThat(checks.get()).isEqualTo(2);
  }

  @Test
  public void shouldWaitForClientCreatedEventUntilRuntimeRecoversAfterTenSeconds() {
    CamundaClient client = mock(CamundaClient.class);
    TopologyRequestStep1 topologyRequest = mock(TopologyRequestStep1.class);
    Topology unhealthyTopology = topology(UNHEALTHY);
    Topology healthyTopology = topology(HEALTHY);
    AtomicInteger checks = new AtomicInteger();
    when(client.newTopologyRequest()).thenReturn(topologyRequest);
    when(topologyRequest.execute())
        .thenAnswer(
            invocation ->
                checks.getAndIncrement() < 6 ? unhealthyTopology : healthyTopology);

    SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
    eventMulticaster.addApplicationListener(new CamundaClusterHealthCheckListener());
    long startedAt = System.nanoTime();

    eventMulticaster.multicastEvent(new CamundaClientCreatedSpringEvent(this, client));

    assertThat(checks.get()).isEqualTo(7);
    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
        .isGreaterThan(Duration.ofSeconds(10));
  }

  @Test
  public void shouldRegisterListenerForRuntimeTestLifecycle() {
    Import importAnnotation = RuntimeMigrationAbstractTest.class.getAnnotation(Import.class);

    assertThat(importAnnotation).isNotNull();
    assertThat(importAnnotation.value()).contains(CamundaClusterHealthCheckListener.class);
  }

  protected Topology topology(PartitionBrokerHealth health) {
    BrokerInfo broker = mock(BrokerInfo.class);
    when(broker.getPartitions()).thenReturn(List.of(partition(1, true, health)));
    Topology topology = mock(Topology.class);
    when(topology.getBrokers()).thenReturn(List.of(broker));
    when(topology.getPartitionsCount()).thenReturn(1);
    return topology;
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
