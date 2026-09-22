/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.RuntimeMigratorException;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.util.ProcessInstanceCleanup;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest extends AbstractMigratorTest {

  protected static final String AUTHORIZATION_PROBE_JOB_TYPE = "__migrator_authorization_probe__";
  protected static final Duration AUTHORIZATION_PROBE_TIMEOUT = Duration.ofSeconds(1);

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

  @Autowired
  protected MigratorProperties migratorProperties;


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
    new ProcessInstanceCleanup(camundaClient).awaitCompletion();
  }

  protected void awaitTenantVisible(String tenantId) {
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() ->
        assertThat(camundaClient.newTenantsSearchRequest().filter(filter -> filter.tenantId(tenantId)).execute().items())
            .extracting(Tenant::getTenantId)
            .contains(tenantId));
  }

  protected void awaitRuntimeMigratorStart() {
    Set<String> tenantIds = new HashSet<>();
    if (migratorProperties.getTenantIds() != null) {
      tenantIds.addAll(migratorProperties.getTenantIds());
    }
    tenantIds.add(C8_DEFAULT_TENANT);

    Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> {
      try {
        camundaClient.newActivateJobsCommand()
            .jobType(AUTHORIZATION_PROBE_JOB_TYPE)
            .maxJobsToActivate(1)
            .timeout(AUTHORIZATION_PROBE_TIMEOUT)
            .workerName(AUTHORIZATION_PROBE_JOB_TYPE)
            .tenantIds(List.copyOf(tenantIds))
            .requestTimeout(AUTHORIZATION_PROBE_TIMEOUT)
            .execute();
        runtimeMigrator.start();
        return true;
      } catch (ClientException | RuntimeMigratorException e) {
        if (isAuthorizationPropagationFailure(e)) {
          return false;
        }
        throw e;
      }
    });
  }

  protected boolean isAuthorizationPropagationFailure(Throwable exception) {
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
      assertThat(
              camundaClient.newProcessInstanceSearchRequest()
                  .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                  .execute()
                  .items()
                  .size())
          .isEqualTo(expected);
    });
  }

}