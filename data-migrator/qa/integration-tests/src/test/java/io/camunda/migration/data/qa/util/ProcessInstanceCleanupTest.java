/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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

  @Test
  void shouldResetConsecutiveEmptySearchesWhenPollFails() {
    ClientException clientException = mock(ClientException.class);
    ProcessInstanceCleanup cleanup = new ProcessInstanceCleanup(null) {
      @Override
      protected List<ProcessInstance> findAllProcessInstances() {
        throw clientException;
      }
    };
    AtomicInteger consecutiveEmptySearches = new AtomicInteger(2);

    assertThatThrownBy(() -> cleanup.cleanupPoll(consecutiveEmptySearches))
        .isSameAs(clientException);
    assertThat(consecutiveEmptySearches).hasValue(0);
  }

  @Test
  void shouldKeepUsingTheCleanupPollIntervalDuringConfirmationSearches() {
    ProcessInstanceCleanup cleanup = new ProcessInstanceCleanup(null);

    assertThat(cleanup.cleanupPollInterval(new AtomicInteger()))
        .isEqualTo(ProcessInstanceCleanup.CLEANUP_POLL_INTERVAL);
    assertThat(cleanup.cleanupPollInterval(new AtomicInteger(1)))
        .isEqualTo(ProcessInstanceCleanup.CLEANUP_POLL_INTERVAL);
  }

  @Test
  void shouldFindProcessInstancesAcrossAllStatesAndPages() {
    CamundaClient camundaClient = mock(CamundaClient.class);
    ProcessInstanceSearchRequest searchRequest = mock(ProcessInstanceSearchRequest.class);
    ProcessInstance activeInstance = processInstance(ProcessInstanceState.ACTIVE, 1L);
    ProcessInstance completedInstance = processInstance(ProcessInstanceState.COMPLETED, 2L);
    ProcessInstance terminatedInstance = processInstance(ProcessInstanceState.TERMINATED, 3L);
    SearchResponse<ProcessInstance> firstResponse =
        searchResponse(List.of(activeInstance, completedInstance), "first-page");
    SearchResponse<ProcessInstance> secondResponse =
        searchResponse(List.of(terminatedInstance), null);
    when(camundaClient.newProcessInstanceSearchRequest()).thenReturn(searchRequest);
    when(searchRequest.filter(org.mockito.ArgumentMatchers.<Consumer<ProcessInstanceFilter>>any()))
        .thenThrow(new AssertionError("cleanup must not filter process instances by state"));
    var responses = List.of(firstResponse, secondResponse).iterator();
    when(searchRequest.execute()).thenAnswer(invocation -> responses.next());

    assertThat(new ProcessInstanceCleanup(camundaClient).findAllProcessInstances())
        .containsExactly(activeInstance, completedInstance, terminatedInstance);

    verify(camundaClient, times(2)).newProcessInstanceSearchRequest();
    verify(searchRequest).page(org.mockito.ArgumentMatchers.<Consumer<SearchRequestPage>>any());
    verify(searchRequest, never())
        .filter(org.mockito.ArgumentMatchers.<Consumer<ProcessInstanceFilter>>any());
  }

  @Test
  void shouldCancelActiveAndDeleteTerminalProcessInstances() {
    CamundaClient camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    ProcessInstance active = processInstance(ProcessInstanceState.ACTIVE, 1L);
    ProcessInstance completed = processInstance(ProcessInstanceState.COMPLETED, 2L);
    ProcessInstance terminated = processInstance(ProcessInstanceState.TERMINATED, 3L);
    List<Long> terminalProcessInstanceKeys = new ArrayList<>();
    ProcessInstanceCleanup cleanup =
        new ProcessInstanceCleanup(camundaClient) {
          @Override
          protected void deleteTerminalProcessInstance(long processInstanceKey) {
            terminalProcessInstanceKeys.add(processInstanceKey);
          }
        };
    clearInvocations(camundaClient);

    cleanup.deleteProcessInstances(List.of(active, completed, terminated));

    verify(camundaClient).newCancelInstanceCommand(1L);
    assertThat(terminalProcessInstanceKeys).containsExactly(2L, 3L);
    verify(camundaClient, never()).newDeleteResourceCommand(1L);
    verify(camundaClient, never()).newDeleteResourceCommand(2L);
    verify(camundaClient, never()).newDeleteResourceCommand(3L);
  }

  @Test
  void shouldDeleteTerminalProcessInstanceThroughOperateApi() throws IOException {
    CamundaClient camundaClient = mock(CamundaClient.class);
    CamundaClientConfiguration configuration = mock(CamundaClientConfiguration.class);
    HttpResponse<Void> response = mock(HttpResponse.class);
    CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);
    AtomicReference<HttpRequest> request = new AtomicReference<>();
    when(camundaClient.getConfiguration()).thenReturn(configuration);
    when(configuration.getRestAddress()).thenReturn(URI.create("http://localhost:8080"));
    when(configuration.getDefaultRequestTimeout()).thenReturn(Duration.ofSeconds(5));
    when(configuration.getCredentialsProvider()).thenReturn(credentialsProvider);
    doAnswer(
            invocation -> {
              final CredentialsProvider.CredentialsApplier credentialsApplier =
                  invocation.getArgument(0);
              credentialsApplier.put("X-Test-Header", "test-value");
              return null;
            })
        .when(credentialsProvider)
        .applyCredentials(any());
    when(response.statusCode()).thenReturn(204);
    ProcessInstanceCleanup cleanup =
        new ProcessInstanceCleanup(camundaClient) {
          @Override
          protected HttpResponse<Void> executeDelete(HttpRequest deleteRequest) {
            request.set(deleteRequest);
            return response;
          }
        };

    cleanup.deleteTerminalProcessInstance(42L);

    assertThat(request.get().method()).isEqualTo("DELETE");
    assertThat(request.get().uri())
        .isEqualTo(URI.create("http://localhost:8080/v1/process-instances/42"));
    assertThat(request.get().headers().firstValue("X-Test-Header")).contains("test-value");
  }

  @Test
  void shouldIgnoreMissingTerminalProcessInstance() {
    CamundaClient camundaClient = mock(CamundaClient.class);
    CamundaClientConfiguration configuration = mock(CamundaClientConfiguration.class);
    HttpResponse<Void> response = mock(HttpResponse.class);
    CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);
    when(camundaClient.getConfiguration()).thenReturn(configuration);
    when(configuration.getRestAddress()).thenReturn(URI.create("http://localhost:8080"));
    when(configuration.getDefaultRequestTimeout()).thenReturn(Duration.ofSeconds(5));
    when(configuration.getCredentialsProvider()).thenReturn(credentialsProvider);
    when(response.statusCode()).thenReturn(404);
    ProcessInstanceCleanup cleanup =
        new ProcessInstanceCleanup(camundaClient) {
          @Override
          protected HttpResponse<Void> executeDelete(HttpRequest request) {
            return response;
          }
        };

    cleanup.deleteTerminalProcessInstance(42L);
  }

  protected ProcessInstance processInstance(ProcessInstanceState state, long key) {
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getState()).thenReturn(state);
    when(processInstance.getProcessInstanceKey()).thenReturn(key);
    return processInstance;
  }

  protected SearchResponse<ProcessInstance> searchResponse(
      List<ProcessInstance> items, String endCursor) {
    SearchResponsePage page = new SearchResponsePage() {
      @Override
      public Long totalItems() {
        return (long) items.size();
      }

      @Override
      public String startCursor() {
        return null;
      }

      @Override
      public String endCursor() {
        return endCursor;
      }
    };
    return new SearchResponse<>() {
      @Override
      public List<ProcessInstance> items() {
        return items;
      }

      @Override
      public SearchResponsePage page() {
        return page;
      }
    };
  }
}
