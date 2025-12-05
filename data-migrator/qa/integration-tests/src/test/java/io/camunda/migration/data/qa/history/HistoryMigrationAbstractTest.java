/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.constants.MigratorConstants.USER_TASK_ID;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.date.util.WithSpringProfile;
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
import java.util.List;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import({
  HistoryMigrationAbstractTest.HistoryCustomConfiguration.class,
  TestProcessEngineConfiguration.class,
  MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public abstract class HistoryMigrationAbstractTest extends AbstractMigratorTest {

  // Migrator ---------------------------------------

  @Autowired
  protected HistoryMigrator historyMigrator;

  // C8 ---------------------------------------

  @Autowired
  protected RdbmsPurger rdbmsPurger;

  @Autowired
  protected RdbmsService rdbmsService;

  // C7 ---------------------------------------

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected DbClient dbClient;

  @AfterEach
  public void cleanup() {
    // C7
    ClockUtil.reset();
    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

    // Migrator
    dbClient.deleteAllMappings();
    historyMigrator.setMode(MigratorMode.MIGRATE);
    historyMigrator.setRequestedEntityTypes(null);

    // C8
    rdbmsPurger.purgeRdbms();
  }

  public List<ProcessDefinitionEntity> searchHistoricProcessDefinitions(String processDefinitionId) {
    return rdbmsService.getProcessDefinitionReader()
        .search(ProcessDefinitionQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<DecisionDefinitionEntity> searchHistoricDecisionDefinitions(String decisionDefinitionId) {
    return rdbmsService.getDecisionDefinitionReader()
        .search(DecisionDefinitionQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.decisionDefinitionIds(decisionDefinitionId))))
        .items();
  }

  public List<DecisionRequirementsEntity> searchHistoricDecisionRequirementsDefinition(String decisionRequirementsId) {
    return rdbmsService.getDecisionRequirementsReader()
        .search(DecisionRequirementsQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.decisionRequirementsIds(decisionRequirementsId))))
        .items();
  }

  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId) {
    return rdbmsService.getProcessInstanceReader()
        .search(ProcessInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<DecisionInstanceEntity> searchHistoricDecisionInstances(String decisionDefinitionId) {
    return rdbmsService.getDecisionInstanceReader()
        .search(DecisionInstanceQuery.of(queryBuilder -> queryBuilder.filter(
                filterBuilder -> filterBuilder.decisionDefinitionIds(decisionDefinitionId))
            .resultConfig(DecisionInstanceQueryResultConfig.of(DecisionInstanceQueryResultConfig.Builder::includeAll))))
        .items();
  }

  public List<UserTaskEntity> searchHistoricUserTasks(long processInstanceKey) {
    return rdbmsService.getUserTaskReader()
        .search(UserTaskQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey))))
        .items();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesForType(long processInstanceKey, FlowNodeInstanceEntity.FlowNodeType type) {
    return rdbmsService.getFlowNodeInstanceReader()
        .search(FlowNodeInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey).types(type))))
        .items();
  }

  public List<IncidentEntity> searchHistoricIncidents(String processDefinitionId) {
    return rdbmsService.getIncidentReader()
        .search(IncidentQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<VariableEntity> searchHistoricVariables(String varName) {
    return rdbmsService.getVariableReader()
        .search(VariableQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.names(varName))))
        .items();
  }

  @Configuration
  public static class HistoryCustomConfiguration {

    @Bean
    @Conditional(C8DataSourceConfigured.class)
    public RdbmsPurger rdbmsPurger(
        PurgeMapper purgeMapper,
        VendorDatabaseProperties vendorDatabaseProperties) {
      return new RdbmsPurger(purgeMapper, vendorDatabaseProperties);
    }

  }

  protected void completeAllUserTasksWithDefaultUserTaskId() {
    for (Task task : taskService.createTaskQuery().taskDefinitionKey(USER_TASK_ID).list()) {
      taskService.complete(task.getId());
    }
  }
}