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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.DeleteResourceCommandStep1;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
  void shouldResetConsecutiveEmptySearchesAfterClientException() {
    AtomicInteger consecutiveEmptySearches = new AtomicInteger(2);
    ProcessInstanceCleanup cleanup = new ProcessInstanceCleanup(null) {
      private boolean firstSearch = true;

      @Override
      protected List<ProcessInstance> findAllProcessInstances() {
        if (firstSearch) {
          firstSearch = false;
          throw new ClientException("transient failure");
        }
        return List.of();
      }
    };

    assertThatThrownBy(() -> cleanup.cleanupPoll(consecutiveEmptySearches))
        .isInstanceOf(ClientException.class)
        .hasMessage("transient failure");
    assertThat(consecutiveEmptySearches).hasValue(0);

    assertThat(cleanup.cleanupPoll(consecutiveEmptySearches)).isFalse();
    assertThat(consecutiveEmptySearches).hasValue(1);
  }

  @Test
  void shouldFindProcessInstancesAcrossSearchPages() {
    List<String> pageCursors = new ArrayList<>();
    Deque<SearchResponse<ProcessInstance>> responses = new ArrayDeque<>();
    responses.add(searchResponse(List.of(processInstance(1L, ProcessInstanceState.ACTIVE)), "cursor-1"));
    responses.add(searchResponse(List.of(processInstance(2L, ProcessInstanceState.COMPLETED)), null));

    ProcessInstanceSearchRequest searchRequest = proxy(ProcessInstanceSearchRequest.class,
        (requestProxy, method, arguments) -> {
          if (method.getName().equals("page")) {
            @SuppressWarnings("unchecked")
            Consumer<SearchRequestPage> pageConsumer = (Consumer<SearchRequestPage>) arguments[0];
            pageConsumer.accept(proxy(SearchRequestPage.class, (pageProxy, pageMethod, pageArguments) -> {
              if (pageMethod.getName().equals("after")) {
                pageCursors.add((String) pageArguments[0]);
              }
              return pageProxy;
            }));
            return requestProxy;
          }
          if (method.getName().equals("execute")) {
            return responses.removeFirst();
          }
          return requestProxy;
        });
    CamundaClient camundaClient = proxy(CamundaClient.class,
        (clientProxy, method, arguments) -> method.getName().equals("newProcessInstanceSearchRequest")
            ? searchRequest
            : null);

    List<ProcessInstance> items = new ProcessInstanceCleanup(camundaClient).findAllProcessInstances();

    assertThat(items).extracting(ProcessInstance::getProcessInstanceKey).containsExactly(1L, 2L);
    assertThat(pageCursors).containsExactly("cursor-1");
  }

  @Test
  void shouldUseCancelForActiveInstancesAndDeleteResourceForTerminalInstances() {
    List<String> commands = new ArrayList<>();
    ProblemException notFound = new ProblemException(404, "NOT_FOUND", null);
    CamundaClient camundaClient = proxy(CamundaClient.class,
        (clientProxy, method, arguments) -> {
          long processInstanceKey = (long) arguments[0];
          if (method.getName().equals("newCancelInstanceCommand")) {
            commands.add("cancel:" + processInstanceKey);
            return command(CancelProcessInstanceCommandStep1.class, notFound);
          }
          if (method.getName().equals("newDeleteResourceCommand")) {
            commands.add("delete:" + processInstanceKey);
            return command(DeleteResourceCommandStep1.class, notFound);
          }
          return null;
        });

    new ProcessInstanceCleanup(camundaClient).deleteProcessInstances(List.of(
        processInstance(1L, ProcessInstanceState.ACTIVE),
        processInstance(2L, ProcessInstanceState.COMPLETED)));

    assertThat(commands).containsExactly("cancel:1", "delete:2");
  }

  private static ProcessInstance processInstance(long key, ProcessInstanceState state) {
    return proxy(ProcessInstance.class, (processInstanceProxy, method, arguments) -> {
      if (method.getName().equals("getProcessInstanceKey")) {
        return key;
      }
      if (method.getName().equals("getState")) {
        return state;
      }
      return null;
    });
  }

  private static SearchResponse<ProcessInstance> searchResponse(
      List<ProcessInstance> items, String endCursor) {
    SearchResponsePage page = proxy(SearchResponsePage.class, (pageProxy, method, arguments) ->
        method.getName().equals("endCursor") ? endCursor : null);
    return proxy(SearchResponse.class, (responseProxy, method, arguments) -> {
      if (method.getName().equals("items")) {
        return items;
      }
      if (method.getName().equals("page")) {
        return page;
      }
      return null;
    });
  }

  private static <T> T command(Class<T> type, RuntimeException failure) {
    return proxy(type, (commandProxy, method, arguments) -> {
      if (method.getName().equals("execute")) {
        throw failure;
      }
      return null;
    });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<?> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        (proxy, method, arguments) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "equals" -> proxy == arguments[0];
              case "hashCode" -> System.identityHashCode(proxy);
              case "toString" -> type.getSimpleName() + " proxy";
              default -> null;
            };
          }
          return handler.invoke(proxy, method, arguments);
        });
  }
}
