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
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;

public class ProcessInstanceCleanup {

  protected static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(120);
  protected static final Duration CLEANUP_POLL_INTERVAL = Duration.ofSeconds(2);
  protected static final int REQUIRED_CONSECUTIVE_EMPTY_SEARCHES = 3;
  protected final CamundaClient camundaClient;

  public ProcessInstanceCleanup(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void awaitCompletion() {
    AtomicInteger consecutiveEmptySearches = new AtomicInteger();
    Awaitility.await()
        .atMost(CLEANUP_TIMEOUT)
        .pollInterval(CLEANUP_POLL_INTERVAL)
        .ignoreException(ClientException.class)
        .until(() -> cleanupPoll(consecutiveEmptySearches));
  }

  protected boolean cleanupPoll(AtomicInteger consecutiveEmptySearches) {
    List<ProcessInstance> items = findAllProcessInstances();
    deleteProcessInstances(items);

    if (items.isEmpty()) {
      return consecutiveEmptySearches.incrementAndGet() >= REQUIRED_CONSECUTIVE_EMPTY_SEARCHES;
    }

    consecutiveEmptySearches.set(0);
    return false;
  }

  protected void deleteProcessInstances(List<ProcessInstance> items) {
    for (ProcessInstance processInstance : items) {
      try {
        if (processInstance.getState() == ProcessInstanceState.ACTIVE
            || processInstance.getState() == ProcessInstanceState.SUSPENDED) {
          camundaClient.newCancelInstanceCommand(processInstance.getProcessInstanceKey()).execute();
        } else {
          camundaClient.newDeleteResourceCommand(processInstance.getProcessInstanceKey()).execute();
        }
      } catch (ClientStatusException | ProblemException e) {
        if (e.getMessage() == null || !e.getMessage().contains("NOT_FOUND")) {
          throw e;
        }
        // The search result is stale; the instance is already gone.
      }
    }
  }

  protected List<ProcessInstance> findAllProcessInstances() {
    List<ProcessInstance> items = new ArrayList<>();
    String cursor = null;

    while (true) {
      final var request = camundaClient.newProcessInstanceSearchRequest();
      if (cursor != null) {
        final String pageCursor = cursor;
        request.page(page -> page.after(pageCursor));
      }

      final var response = request.execute();
      items.addAll(response.items());

      final String nextCursor = response.page().endCursor();
      if (response.items().isEmpty() || nextCursor == null || nextCursor.equals(cursor)) {
        return items;
      }
      cursor = nextCursor;
    }
  }
}
