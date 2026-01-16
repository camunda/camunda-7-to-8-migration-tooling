/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState.EVALUATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.util.ConverterUtil;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.util.WithSpringProfile;
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
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
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

  @Autowired
  protected FlowNodeInstanceMapper flowNodeInstanceMapper;

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
                filterBuilder.processDefinitionIds(prefixDefinitionId(processDefinitionId)))))
        .items();
  }

  public List<DecisionDefinitionEntity> searchHistoricDecisionDefinitions(String decisionDefinitionId) {
    return rdbmsService.getDecisionDefinitionReader()
        .search(DecisionDefinitionQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.decisionDefinitionIds(prefixDefinitionId(decisionDefinitionId)))))
        .items();
  }

  public List<DecisionRequirementsEntity> searchHistoricDecisionRequirementsDefinition(String decisionRequirementsId) {
    return rdbmsService.getDecisionRequirementsReader()
        .search(DecisionRequirementsQuery.of(queryBuilder -> queryBuilder.filter(
                filterBuilder -> filterBuilder.decisionRequirementsIds(prefixDefinitionId(decisionRequirementsId)))
            .resultConfig(
                DecisionRequirementsQueryResultConfig.of(builder -> builder.includeXml(true)))))
        .items();
  }

  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId) {
    return searchHistoricProcessInstances(processDefinitionId, false);
  }

  /**
   * When the built-in ProcessInstanceTransformer is disabled, the processDefinitionId
   * is NOT prefixed during migration. This method allows searching with or without prefixing.
   */
  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId,
                                                                    boolean builtInTransformerDisabled) {
    String finalProcessDefinitionId = builtInTransformerDisabled ?
        processDefinitionId :
        prefixDefinitionId(processDefinitionId);
    return rdbmsService.getProcessInstanceReader()
        .search(ProcessInstanceQuery.of(queryBuilder -> queryBuilder.filter(
            filterBuilder -> filterBuilder.processDefinitionIds(finalProcessDefinitionId))))
        .items();
  }

  public List<DecisionInstanceEntity> searchHistoricDecisionInstances(String... decisionDefinitionIds) {
    return rdbmsService.getDecisionInstanceReader()
        .search(DecisionInstanceQuery.of(queryBuilder -> queryBuilder.filter(
                filterBuilder -> filterBuilder.decisionDefinitionIds(
                    Arrays.stream(decisionDefinitionIds).map(ConverterUtil::prefixDefinitionId).toArray(String[]::new)))
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

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodes(long processInstanceKey) {
    return rdbmsService.getFlowNodeInstanceReader()
        .search(FlowNodeInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey))))
        .items();
  }

  public List<FlowNodeInstanceDbModel> searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(Long processInstanceKey) {
    return flowNodeInstanceMapper.search(
        FlowNodeInstanceDbQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  public List<IncidentEntity> searchHistoricIncidents(String processDefinitionId) {
    return rdbmsService.getIncidentReader()
        .search(IncidentQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(prefixDefinitionId(processDefinitionId)))))
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

  protected void assertDecisionInstance(
      DecisionInstanceEntity instance,
      String decisionDefinitionId,
      Date evaluationDate,
      Long flowNodeInstanceKey,
      Long processInstanceKey,
      Long processDefinitionKey,
      Long decisionDefinitionKey,
      DecisionInstanceEntity.DecisionDefinitionType decisionDefinitionType,
      String result,
      String inputName,
      String inputValue,
      String outputName,
      String outputValue) {
    assertThat(instance.decisionInstanceId()).isNotNull();
    assertThat(instance.decisionInstanceKey()).isNotNull();
    assertThat(instance.state()).isEqualTo(EVALUATED);
    assertThat(instance.evaluationDate()).isEqualTo(convertDate(evaluationDate));
    assertThat(instance.evaluationFailure()).isNull();
    assertThat(instance.evaluationFailureMessage()).isNull();
    assertThat(instance.flowNodeInstanceKey()).isEqualTo(flowNodeInstanceKey);
    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(instance.decisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(instance.decisionDefinitionId()).isEqualTo(prefixDefinitionId(decisionDefinitionId));
    assertThat(instance.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(instance.decisionDefinitionType()).isEqualTo(decisionDefinitionType);
    assertThat(instance.result()).isEqualTo(result);
    assertThat(instance.rootDecisionDefinitionKey()).isNull();
    assertThat(instance.evaluatedInputs()).singleElement().satisfies(input -> {
      assertThat(input.inputId()).isNotNull();
      assertThat(input.inputName()).isEqualTo(inputName);
      assertThat(input.inputValue()).isEqualTo(inputValue);
    });
    assertThat(instance.evaluatedOutputs()).singleElement().satisfies(output -> {
      assertThat(output.outputId()).isNotNull();
      assertThat(output.outputName()).isEqualTo(outputName);
      assertThat(output.outputValue()).isEqualTo(outputValue);
    });
  }
}