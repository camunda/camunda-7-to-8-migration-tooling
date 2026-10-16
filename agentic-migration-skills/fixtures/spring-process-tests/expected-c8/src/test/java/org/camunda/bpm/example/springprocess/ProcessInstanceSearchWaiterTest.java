/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

class ProcessInstanceSearchWaiterTest {

  @Test
  void waitsForEmptyWrongInstanceAndActiveBeforeTheSameInstanceCompletes() {
    AtomicInteger searchCalls = new AtomicInteger();
    Supplier<List<ProcessInstance>> search =
        () ->
            switch (searchCalls.getAndIncrement()) {
              case 0 -> List.of();
              case 1 -> List.of(instance(999, ProcessInstanceState.COMPLETED));
              case 2 -> List.of(instance(123, ProcessInstanceState.ACTIVE));
              default -> List.of(instance(123, ProcessInstanceState.COMPLETED));
            };

    ProcessInstanceSearchWaiter.awaitCompleted(123, search, Duration.ofSeconds(2));

    assertThat(searchCalls).hasValue(4);
  }

  @Test
  void neverTreatsAnEmptyOrUnrelatedSearchAsCompletion() {
    assertThatThrownBy(
            () -> ProcessInstanceSearchWaiter.awaitCompleted(123, List::of, Duration.ofMillis(150)))
        .isInstanceOf(ConditionTimeoutException.class);
    assertThatThrownBy(
            () ->
                ProcessInstanceSearchWaiter.awaitCompleted(
                    123,
                    () -> List.of(instance(999, ProcessInstanceState.COMPLETED)),
                    Duration.ofMillis(150)))
        .isInstanceOf(ConditionTimeoutException.class);
  }

  private static ProcessInstance instance(long key, ProcessInstanceState state) {
    ProcessInstance instance = mock(ProcessInstance.class);
    when(instance.getProcessInstanceKey()).thenReturn(key);
    when(instance.getState()).thenReturn(state);
    return instance;
  }
}
