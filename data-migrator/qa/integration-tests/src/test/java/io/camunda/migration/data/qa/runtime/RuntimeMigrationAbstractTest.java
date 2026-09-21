/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.exception.RuntimeMigratorException;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest extends AbstractMigratorTest {

  /**
    * Set generous Awaitility defaults so that this module's own {@code await()} calls without
    * an explicit {@code .atMost()} inherit a CI-safe timeout after the Spring/CPT context has
    * started.
   *
   * <p>These defaults do not override CPT's explicit cluster readiness timeout, but they keep
   * post-startup assertions from relying on Awaitility's short default timeout.
   */
  static {
    Awaitility.setDefaultTimeout(Duration.ofSeconds(120));
    Awaitility.setDefaultPollInterval(Duration.ofSeconds(2));
  }

  // Migrator ---------------------------------------

  @Autowired
  protected RuntimeMigrator runtimeMigrator;

  @Autowired
  protected DbClient dbClient;


  // C7 ---------------------------------------

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  // C8 ---------------------------------------

  @Autowired
  protected CamundaClient camundaClient;

  @AfterEach
  public void cleanup() {
    // C7
    ClockUtil.reset();
    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

    // C8
    awaitProcessInstanceCleanup();

    // Migrator
    dbClient.deleteAllMappings();
    runtimeMigrator.setMode(MIGRATE);
  }

  protected void awaitProcessInstanceCleanup() {
    AtomicInteger consecutiveEmptySearches = new AtomicInteger();
    Awaitility.await().ignoreException(ClientException.class).until(() -> {
      List<ProcessInstance> items = findAllProcessInstances();
      boolean allProcessInstancesCleared = deleteProcessInstances(items);
      if (items.isEmpty() || allProcessInstancesCleared) {
        return consecutiveEmptySearches.incrementAndGet() >= 3;
      }
      consecutiveEmptySearches.set(0);
      return false;
    });
  }

  protected boolean deleteProcessInstances(List<ProcessInstance> items) {
    boolean allProcessInstancesCleared = true;
    for (ProcessInstance i : items) {
      try {
        if (i.getState() == ProcessInstanceState.ACTIVE) {
          camundaClient.newCancelInstanceCommand(i.getProcessInstanceKey()).execute();
          allProcessInstancesCleared = false;
        } else {
          camundaClient.newDeleteProcessInstanceCommand(i.getProcessInstanceKey()).execute();
        }
      } catch (ClientStatusException | ProblemException e) {
        if (!e.getMessage().contains("NOT_FOUND")) {
          throw e;
        }
        // The search result is stale; the instance is already gone.
      }
    }
    return allProcessInstancesCleared;
  }

  protected List<ProcessInstance> findAllProcessInstances() {
    final List<ProcessInstance> items = new ArrayList<>();
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

  protected void awaitTenantVisible(String tenantId) {
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() ->
        assertThat(camundaClient.newTenantsSearchRequest().filter(filter -> filter.tenantId(tenantId)).execute().items())
            .extracting(Tenant::getTenantId)
            .contains(tenantId));
  }

  protected void awaitUserTenantMembership(String username, String tenantId) {
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() ->
        assertThat(camundaClient.newUsersByTenantSearchRequest(tenantId).execute().items())
            .extracting(TenantUser::getUsername)
            .contains(username));
  }

  protected void awaitRuntimeMigratorStart() {
    Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> {
      try {
        runtimeMigrator.start();
        return true;
      } catch (RuntimeMigratorException e) {
        if (isAuthorizationPropagationFailure(e)) {
          return false;
        }
        throw e;
      }
    });
  }

  protected boolean isAuthorizationPropagationFailure(RuntimeMigratorException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause.getMessage() != null && cause.getMessage().contains("user is not authorized")) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  protected Optional<Variable> getVariableByScope(Long processInstanceKey, Long scopeKey, String variableName) {
    List<Variable> variables = camundaClient.newVariableSearchRequest().execute().items();

    return variables.stream()
        .filter(v -> v.getProcessInstanceKey().equals(processInstanceKey))
        .filter(v -> v.getScopeKey().equals(scopeKey))
        .filter(v -> v.getName().equals(variableName))
        .findFirst();
  }

  protected void assertThatProcessInstanceCountIsEqualTo(int expected) {
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      assertThat(camundaClient.newProcessInstanceSearchRequest().execute().items()).hasSize(expected);
    });
  }

}
