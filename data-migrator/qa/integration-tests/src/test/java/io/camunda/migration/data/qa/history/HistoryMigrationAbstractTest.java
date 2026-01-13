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
import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
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
import io.camunda.search.query.ProcessInstanceQuery;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.HistoryService;
import org.junit.jupiter.api.extension.RegisterExtension;
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

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  // Autowired fields specific to history tests
  @Autowired
  protected FlowNodeInstanceMapper flowNodeInstanceMapper;

  // Convenience accessors for commonly used beans

  protected HistoryMigrator getHistoryMigrator() {
    return historyMigration.getMigrator();
  }

  protected RdbmsService getRdbmsService() {
    return historyMigration.getRdbmsService();
  }

  protected HistoryService getHistoryService() {
    return historyMigration.getHistoryService();
  }

  protected DbClient getDbClient() {
    return historyMigration.getDbClient();
  }

  public List<ProcessDefinitionEntity> searchHistoricProcessDefinitions(String processDefinitionId) {
    return historyMigration.searchHistoricProcessDefinitions(processDefinitionId);
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(prefixDefinitionId(processDefinitionId)))))
        .items();
  }

  public List<DecisionDefinitionEntity> searchHistoricDecisionDefinitions(String decisionDefinitionId) {
    return historyMigration.searchHistoricDecisionDefinitions(decisionDefinitionId);
  }

  public List<DecisionRequirementsEntity> searchHistoricDecisionRequirementsDefinition(String decisionRequirementsId) {
    return historyMigration.searchHistoricDecisionRequirementsDefinition(decisionRequirementsId);
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
    return getRdbmsService().getProcessInstanceReader()
        .search(ProcessInstanceQuery.of(queryBuilder -> queryBuilder.filter(
            filterBuilder -> filterBuilder.processDefinitionIds(finalProcessDefinitionId))))
        .items();
  }

  public List<DecisionInstanceEntity> searchHistoricDecisionInstances(String decisionDefinitionId) {
    return historyMigration.searchHistoricDecisionInstances(decisionDefinitionId);
  }

  public List<UserTaskEntity> searchHistoricUserTasks(long processInstanceKey) {
    return historyMigration.searchHistoricUserTasks(processInstanceKey);
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesForType(long processInstanceKey, FlowNodeInstanceEntity.FlowNodeType type) {
    return historyMigration.searchHistoricFlowNodesForType(processInstanceKey, type);
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodes(long processInstanceKey) {
    return getRdbmsService().getFlowNodeInstanceReader()
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
    return historyMigration.searchHistoricIncidents(processDefinitionId);
  }

  public List<VariableEntity> searchHistoricVariables(String varName) {
    return historyMigration.searchHistoricVariables(varName);
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
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();
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