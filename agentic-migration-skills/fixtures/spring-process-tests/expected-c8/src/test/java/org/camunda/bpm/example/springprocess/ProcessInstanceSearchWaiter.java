/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

final class ProcessInstanceSearchWaiter {

  private ProcessInstanceSearchWaiter() {}

  static void awaitCompleted(
      long processInstanceKey, Supplier<List<ProcessInstance>> search, Duration timeout) {
    await()
        .atMost(timeout)
        .pollInterval(Duration.ofMillis(25))
        .untilAsserted(
            () -> {
              List<ProcessInstance> matching =
                  search.get().stream()
                      .filter(
                          instance ->
                              Long.valueOf(processInstanceKey)
                                  .equals(instance.getProcessInstanceKey()))
                      .toList();
              assertThat(matching)
                  .as("process instance %s must be visible and completed", processInstanceKey)
                  .singleElement()
                  .satisfies(
                      instance ->
                          assertThat(instance.getState()).isEqualTo(ProcessInstanceState.COMPLETED));
            });
  }
}
