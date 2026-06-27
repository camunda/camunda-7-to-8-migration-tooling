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
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest extends AbstractMigratorTest {

  private static final long CAMUNDA_CLIENT_REQUEST_TIMEOUT_SECONDS = 10;

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
    List<ProcessInstance> items = searchProcessInstancesForCleanup();
    for (ProcessInstance processInstance : items) {
      try {
        camundaClient.newDeleteResourceCommand(processInstance.getProcessInstanceKey()).execute();
      } catch (ClientStatusException | ProblemException e) {
        if (!e.getMessage().contains("NOT_FOUND")) {
          throw e;
        }
        // Ignore NOT_FOUND errors as the instance might have been deleted already
      }
    }

    // Migrator
    dbClient.deleteAllMappings();
    runtimeMigrator.setMode(MIGRATE);
  }

  protected Optional<Variable> getVariableByScope(Long processInstanceKey, Long scopeKey, String variableName) {
    List<Variable> variables = searchVariables();

    return variables.stream()
        .filter(v -> v.getProcessInstanceKey().equals(processInstanceKey))
        .filter(v -> v.getScopeKey().equals(scopeKey))
        .filter(v -> v.getName().equals(variableName))
        .findFirst();
  }

  protected void assertThatProcessInstanceCountIsEqualTo(int expected) {
    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .ignoreException(ClientException.class)
        .untilAsserted(() -> {
          assertThat(searchProcessInstances()).hasSize(expected);
        });
  }

  private List<ProcessInstance> searchProcessInstances() {
    return camundaClient.newProcessInstanceSearchRequest()
        .send()
        .join(CAMUNDA_CLIENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .items();
  }

  private List<ProcessInstance> searchProcessInstancesForCleanup() {
    try {
      return searchProcessInstances();
    } catch (ClientException e) {
      if (isCausedByTimeout(e)) {
        return List.of();
      }
      throw e;
    }
  }

  private List<Variable> searchVariables() {
    return camundaClient.newVariableSearchRequest()
        .send()
        .join(CAMUNDA_CLIENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .items();
  }

  private boolean isCausedByTimeout(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

}