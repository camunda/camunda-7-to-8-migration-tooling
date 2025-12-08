/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.extension;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit extension that provides history migration testing capabilities.
 * Manages cleanup of C7, C8, and migrator state after each test.
 *
 * This extension is a Spring component that gets injected with required beans.
 * It can be used as a field in test classes with {@literal @}RegisterExtension.
 *
 * Usage:
 * <pre>
 * {@literal @}RegisterExtension
 * protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();
 * </pre>
 */
@Component
public class HistoryMigrationExtension implements AfterEachCallback, ApplicationContextAware {

  public static final String USER_TASK_ID = "userTaskId";

  private static ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    HistoryMigrationExtension.applicationContext = context;
  }

  private HistoryMigrator getHistoryMigratorBean() {
    return applicationContext != null ? applicationContext.getBean(HistoryMigrator.class) : null;
  }

  private RdbmsPurger getRdbmsPurgerBean() {
    return applicationContext != null ? applicationContext.getBean(RdbmsPurger.class) : null;
  }

  private RdbmsService getRdbmsServiceBean() {
    return applicationContext != null ? applicationContext.getBean(RdbmsService.class) : null;
  }

  private HistoryService getHistoryServiceBean() {
    return applicationContext != null ? applicationContext.getBean(HistoryService.class) : null;
  }

  private RepositoryService getRepositoryServiceBean() {
    return applicationContext != null ? applicationContext.getBean(RepositoryService.class) : null;
  }

  private TaskService getTaskServiceBean() {
    return applicationContext != null ? applicationContext.getBean(TaskService.class) : null;
  }

  private DbClient getDbClientBean() {
    return applicationContext != null ? applicationContext.getBean(DbClient.class) : null;
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

    DbClient dbClient = getDbClientBean();
    if (dbClient != null) {
      // Migrator
      dbClient.deleteAllMappings();
    }

    HistoryMigrator historyMigrator = getHistoryMigratorBean();
    if (historyMigrator != null) {
      historyMigrator.setMode(MigratorMode.MIGRATE);
      historyMigrator.setRequestedEntityTypes(null);
    }

    RdbmsPurger rdbmsPurger = getRdbmsPurgerBean();
    if (rdbmsPurger != null) {
      // C8
      rdbmsPurger.purgeRdbms();
    }
  }


  // Helper methods

  public HistoryMigrator getMigrator() {
    return getHistoryMigratorBean();
  }

  public RdbmsService getRdbmsService() {
    return getRdbmsServiceBean();
  }

  public HistoryService getHistoryService() {
    return getHistoryServiceBean();
  }

  public DbClient getDbClient() {
    return getDbClientBean();
  }

  public List<ProcessDefinitionEntity> searchHistoricProcessDefinitions(String processDefinitionId) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getProcessDefinitionReader()
        .search(ProcessDefinitionQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<DecisionDefinitionEntity> searchHistoricDecisionDefinitions(String decisionDefinitionId) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getDecisionDefinitionReader()
        .search(DecisionDefinitionQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.decisionDefinitionIds(decisionDefinitionId))))
        .items();
  }

  public List<DecisionRequirementsEntity> searchHistoricDecisionRequirementsDefinition(String decisionRequirementsId) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getDecisionRequirementsReader()
        .search(DecisionRequirementsQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.decisionRequirementsIds(decisionRequirementsId))))
        .items();
  }

  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getProcessInstanceReader()
        .search(ProcessInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<DecisionInstanceEntity> searchHistoricDecisionInstances(String decisionDefinitionId) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getDecisionInstanceReader()
        .search(DecisionInstanceQuery.of(queryBuilder -> queryBuilder.filter(
                filterBuilder -> filterBuilder.decisionDefinitionIds(decisionDefinitionId))
            .resultConfig(DecisionInstanceQueryResultConfig.of(DecisionInstanceQueryResultConfig.Builder::includeAll))))
        .items();
  }

  public List<UserTaskEntity> searchHistoricUserTasks(long processInstanceKey) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getUserTaskReader()
        .search(UserTaskQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey))))
        .items();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesForType(long processInstanceKey, FlowNodeInstanceEntity.FlowNodeType type) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getFlowNodeInstanceReader()
        .search(FlowNodeInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey).types(type))))
        .items();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesById(String... flowNodeIds) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getFlowNodeInstanceReader()
        .search(FlowNodeInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.flowNodeIds(flowNodeIds))))
        .items();
  }

  public List<IncidentEntity> searchHistoricIncidents(String processDefinitionId) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getIncidentReader()
        .search(IncidentQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<VariableEntity> searchHistoricVariables(String... varName) {
    RdbmsService rdbmsService = getRdbmsServiceBean();
    if (rdbmsService == null) {
      throw new IllegalStateException("RdbmsService is not available in the Spring context");
    }
    return rdbmsService.getVariableReader()
        .search(VariableQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.names(Arrays.stream(varName).toList()))))
        .items();
  }

  public void completeAllUserTasksWithDefaultUserTaskId() {
    TaskService taskService = getTaskServiceBean();
    if (taskService == null) {
      throw new IllegalStateException("TaskService is not available in the Spring context");
    }
    for (Task task : taskService.createTaskQuery().taskDefinitionKey(USER_TASK_ID).list()) {
      taskService.complete(task.getId());
    }
  }
}

