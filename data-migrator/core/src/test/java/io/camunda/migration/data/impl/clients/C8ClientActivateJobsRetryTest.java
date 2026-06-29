/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.MigratorException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class C8ClientActivateJobsRetryTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock
  private MigratorProperties properties;

  @InjectMocks
  private C8Client c8Client;

  @BeforeEach
  void setUp() {
    c8Client.activateJobsRetryDelayMs = 0L;
    when(properties.getPageSize()).thenReturn(10);
    when(properties.getTenantIds()).thenReturn(null);
  }

  @Test
  void retriesOnTimeoutAndSucceedsOnSecondAttempt() {
    var cmd = camundaClient.newActivateJobsCommand().jobType("migrator").maxJobsToActivate(10);
    var response = Mockito.mock(ActivateJobsResponse.class);
    when(response.getJobs()).thenReturn(List.of());
    when(cmd.execute())
        .thenThrow(new ClientException("timeout", new SocketTimeoutException()))
        .thenReturn(response);

    List<?> result = c8Client.activateJobs("migrator");

    assertThat(result).isEmpty();
    verify(cmd, times(2)).execute();
  }

  @Test
  void throwsAfterAllRetriesExhausted() {
    var cmd = camundaClient.newActivateJobsCommand().jobType("migrator").maxJobsToActivate(10);
    when(cmd.execute())
        .thenThrow(new ClientException("timeout", new SocketTimeoutException()));

    assertThrows(MigratorException.class, () -> c8Client.activateJobs("migrator"));
    verify(cmd, times(3)).execute();
  }

  @Test
  void doesNotRetryOnNonTimeoutException() {
    var cmd = camundaClient.newActivateJobsCommand().jobType("migrator").maxJobsToActivate(10);
    when(cmd.execute())
        .thenThrow(new ClientException("bad request", new RuntimeException("bad")));

    assertThrows(MigratorException.class, () -> c8Client.activateJobs("migrator"));
    verify(cmd, times(1)).execute();
  }

  @Test
  void isCausedByTimeout_trueForSocketTimeoutInCauseChain() {
    var e = new MigratorException("msg", new ClientException("msg", new SocketTimeoutException()));
    assertTrue(C8Client.isCausedByTimeout(e));
  }

  @Test
  void isCausedByTimeout_trueForTimeoutException() {
    var e = new MigratorException("msg", new ClientException("msg", new TimeoutException()));
    assertTrue(C8Client.isCausedByTimeout(e));
  }

  @Test
  void isCausedByTimeout_falseForOtherException() {
    var e = new MigratorException("msg", new ClientException("msg", new RuntimeException("other")));
    assertFalse(C8Client.isCausedByTimeout(e));
  }
}
