/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime;

import static io.camunda.migrator.MigratorMode.MIGRATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.qa.AbstractMigratorTest;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.qa.util.WithSharedCamunda8;
import io.camunda.process.test.api.CamundaSpringProcessTest;

import java.util.List;

import java.util.Optional;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
@WithSharedCamunda8
public abstract class RuntimeMigrationAbstractTest extends AbstractMigratorTest {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMigrationAbstractTest.class);

  // Migrator ---------------------------------------

  @Autowired
  protected RuntimeMigrator runtimeMigrator;

  @Autowired
  private IdKeyMapper idKeyMapper;

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

    // C8 - Cancel all active process instances first
    List<ProcessInstance> items = camundaClient.newProcessInstanceSearchRequest().execute().items();
    for (ProcessInstance i : items) {
      try {
        camundaClient.newCancelInstanceCommand(i.getProcessInstanceKey()).execute();
      } catch (Exception e) {
        // Ignore errors as the instance might already be completed/cancelled
        LOGGER.debug("Failed to cancel process instance {}: {}", i.getProcessInstanceKey(), e.getMessage());
      }
    }

    // C8 - Delete all process definitions (resources)
    // Note: This can only succeed if all process instances are cancelled/completed
    try {
      List<ProcessDefinition> definitions = camundaClient.newProcessDefinitionSearchRequest().execute().items();
      for (ProcessDefinition def : definitions) {
        try {
          camundaClient.newDeleteResourceCommand(def.getProcessDefinitionKey()).send().join();
        } catch (Exception e) {
          // Log but don't fail - shared test environment may have constraints
          LOGGER.debug("Failed to delete process definition {}: {}", def.getProcessDefinitionKey(), e.getMessage());
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to cleanup C8 deployments: {}", e.getMessage());
    }

    // Migrator
    dbClient.deleteAllMappings();
    runtimeMigrator.setMode(MIGRATE);
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
      assertThat(camundaClient.newProcessInstanceSearchRequest().execute().items().size()).isEqualTo(expected);
    });
  }

  public List<IdKeyDbModel> findSkippedRuntimeProcessInstances() {
    return idKeyMapper.findSkippedByType(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE, 0, Integer.MAX_VALUE);
  }

}