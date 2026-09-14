/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.response.ProcessInstance;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessInstanceCleanupTest {

  @Test
  void shouldWaitForConsecutiveEmptySearchesAfterDeletingInstances() {
    List<ProcessInstance> processInstances = Collections.singletonList(null);
    Deque<List<ProcessInstance>> searchResults = new ArrayDeque<>();
    searchResults.add(processInstances);
    searchResults.add(processInstances);
    searchResults.add(List.of());
    searchResults.add(List.of());
    searchResults.add(List.of());

    ProcessInstanceCleanup cleanup = new ProcessInstanceCleanup(null) {
      @Override
      protected List<ProcessInstance> findAllProcessInstances() {
        return searchResults.removeFirst();
      }

      @Override
      protected void deleteProcessInstances(List<ProcessInstance> items) {
      }
    };
    AtomicInteger consecutiveEmptySearches = new AtomicInteger();

    assertThat(cleanup.cleanupPoll(consecutiveEmptySearches)).isFalse();
    assertThat(consecutiveEmptySearches).hasValue(0);
    assertThat(cleanup.cleanupPoll(consecutiveEmptySearches)).isFalse();
    assertThat(consecutiveEmptySearches).hasValue(0);
    assertThat(cleanup.cleanupPoll(consecutiveEmptySearches)).isFalse();
    assertThat(consecutiveEmptySearches).hasValue(1);
    assertThat(cleanup.cleanupPoll(consecutiveEmptySearches)).isFalse();
    assertThat(consecutiveEmptySearches).hasValue(2);
    assertThat(cleanup.cleanupPoll(consecutiveEmptySearches)).isTrue();
    assertThat(consecutiveEmptySearches).hasValue(3);
  }
}
