/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.extension;

import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit extension that provides runtime migration testing capabilities.
 * Manages cleanup of C7, C8, and migrator state after each test.
 *
 * This extension is a Spring component that gets injected with required beans.
 * It can be used as a field in test classes with {@literal @}RegisterExtension.
 *
 * Usage:
 * <pre>
 * {@literal @}RegisterExtension
 * {@literal @}Autowired
 * RuntimeMigrationExtension runtimeMigration;
 * </pre>
 */
@Component
public class RuntimeMigrationExtension implements AfterEachCallback, ApplicationContextAware {

  private static ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    RuntimeMigrationExtension.applicationContext = context;
  }

  private RuntimeMigrator getRuntimeMigratorBean() {
    return applicationContext != null ? applicationContext.getBean(RuntimeMigrator.class) : null;
  }

  private DbClient getDbClientBean() {
    return applicationContext != null ? applicationContext.getBean(DbClient.class) : null;
  }

  private RepositoryService getRepositoryServiceBean() {
    return applicationContext != null ? applicationContext.getBean(RepositoryService.class) : null;
  }

  private RuntimeService getRuntimeServiceBean() {
    return applicationContext != null ? applicationContext.getBean(RuntimeService.class) : null;
  }

  private TaskService getTaskServiceBean() {
    return applicationContext != null ? applicationContext.getBean(TaskService.class) : null;
  }

  private CamundaClient getCamundaClientBean() {
    if (applicationContext == null) {
      return null;
    }
    try {
      return applicationContext.getBean(CamundaClient.class);
    } catch (BeansException e) {
      // CamundaClient might not be available in all test contexts
      return null;
    }
  }


  @Override
  public void afterEach(ExtensionContext context) {
    // Get ApplicationContext from Spring's ExtensionContext store if not already set
    if (applicationContext == null) {
      applicationContext = SpringExtension.getApplicationContext(context);
    }

    RepositoryService repositoryService = getRepositoryServiceBean();
    if (repositoryService != null) {
      // C7
      ClockUtil.reset();
      repositoryService.createDeploymentQuery().list()
          .forEach(d -> repositoryService.deleteDeployment(d.getId(), true));
    }

    CamundaClient camundaClient = getCamundaClientBean();
    if (camundaClient != null) {
      // C8
      List<ProcessInstance> items = camundaClient.newProcessInstanceSearchRequest().execute().items();
      for (ProcessInstance i : items) {
        try {
          camundaClient.newDeleteResourceCommand(i.getProcessInstanceKey()).execute();
        } catch (io.camunda.client.api.command.ClientStatusException e) {
          if (!e.getMessage().contains("NOT_FOUND")) {
            throw e;
          }
          // Ignore NOT_FOUND errors as the instance might have been deleted already
        }
      }
    }

    DbClient dbClient = getDbClientBean();
    if (dbClient != null) {
      // Migrator
      dbClient.deleteAllMappings();
    }

    RuntimeMigrator runtimeMigrator = getRuntimeMigratorBean();
    if (runtimeMigrator != null) {
      runtimeMigrator.setMode(MIGRATE);
    }
  }


  // Helper methods

  public RuntimeMigrator getMigrator() {
    return getRuntimeMigratorBean();
  }

  public DbClient getDbClient() {
    return getDbClientBean();
  }

  public RepositoryService getRepositoryService() {
    return getRepositoryServiceBean();
  }

  public RuntimeService getRuntimeService() {
    return getRuntimeServiceBean();
  }

  public TaskService getTaskService() {
    return getTaskServiceBean();
  }

  public CamundaClient getCamundaClient() {
    return getCamundaClientBean();
  }

  public Optional<Variable> getVariableByScope(Long processInstanceKey, Long scopeKey, String variableName) {
    CamundaClient camundaClient = getCamundaClientBean();
    if (camundaClient == null) {
      return Optional.empty();
    }
    List<Variable> variables = camundaClient.newVariableSearchRequest().execute().items();

    return variables.stream()
        .filter(v -> v.getProcessInstanceKey().equals(processInstanceKey))
        .filter(v -> v.getScopeKey().equals(scopeKey))
        .filter(v -> v.getName().equals(variableName))
        .findFirst();
  }

  public void assertThatProcessInstanceCountIsEqualTo(int expected) {
    CamundaClient camundaClient = getCamundaClientBean();
    if (camundaClient == null) {
      throw new IllegalStateException("CamundaClient is not available in the Spring context");
    }
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      assertThat(camundaClient.newProcessInstanceSearchRequest().execute().items().size()).isEqualTo(expected);
    });
  }
}

