/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.converter.FlowNodeConverter;
import io.camunda.migration.data.converter.ProcessInstanceConverter;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@WithSpringProfile("entity-interceptor")
public class HistoryDeclarativeConfigurationTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected List<EntityInterceptor> configuredEntityInterceptors;

  @Test
  public void shouldLoadEntityInterceptorFromConfiguration() {
    // Verify that the configuration is loaded correctly
    assertThat(migratorProperties.getInterceptors()).isNotNull();
    assertThat(migratorProperties.getInterceptors()).hasSize(8);

    var interceptor = migratorProperties.getInterceptors().get(7); // Last one is ComplexEntityInterceptor
    assertThat(interceptor.getClassName()).isEqualTo("io.camunda.migration.data.qa.history.entity.interceptor.pojo.ComplexEntityInterceptor");
    assertThat(interceptor.getProperties()).containsEntry("targetTenantId", "complex-tenant");
  }

  @Test
  public void shouldDisableBuiltInInterceptorsViaConfig() {
    // Verify that built-in interceptors specified as disabled are not in the context
    long disabledProcessInstanceConverters = configuredEntityInterceptors.stream()
        .filter(interceptor -> interceptor instanceof ProcessInstanceConverter)
        .count();

    long disabledFlowNodeConverters = configuredEntityInterceptors.stream()
        .filter(interceptor -> interceptor instanceof FlowNodeConverter)
        .count();

    assertThat(disabledProcessInstanceConverters).isEqualTo(0);
    assertThat(disabledFlowNodeConverters).isEqualTo(0);
  }

  @Test
  public void shouldDisableCustomInterceptorViaConfig() {
    // Verify that custom interceptor specified as disabled is not in the context
    long disabledCustomInterceptors = configuredEntityInterceptors.stream()
        .filter(interceptor -> interceptor.getClass().getSimpleName().equals("DisabledEntityInterceptor"))
        .count();

    assertThat(disabledCustomInterceptors).isEqualTo(0);
  }

  @Test
  public void shouldApplyCustomPropertiesFromConfig() {
    // Find the CustomProcessInstanceInterceptor
    var customInterceptor = configuredEntityInterceptors.stream()
        .filter(interceptor -> interceptor.getClass().getSimpleName().equals("CustomProcessInstanceInterceptor"))
        .findFirst();

    assertThat(customInterceptor).isPresent();
  }

  @Test
  public void shouldExecuteInterceptorsInOrder() {
    // Deploy and migrate a simple process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    repositoryService.activateProcessDefinitionByKey("simpleProcess", false, null);

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.start();

    // Verify process instance was migrated
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());

    assertThat(migratedProcessInstances).isNotEmpty();

    // The ComplexEntityInterceptor should be the last one to execute and set targetTenantId
    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    assertThat(migratedInstance.tenantId()).isEqualTo("complex-tenant");
  }

  @Test
  public void shouldApplyCustomProcessInstanceInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    repositoryService.activateProcessDefinitionByKey("simpleProcess", false, null);

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Temporarily disable ComplexEntityInterceptor to test CustomProcessInstanceInterceptor alone
    configuredEntityInterceptors.removeIf(interceptor ->
        interceptor.getClass().getSimpleName().equals("ComplexEntityInterceptor"));

    // Run history migration
    historyMigrator.start();

    // Verify process instance was migrated with custom tenant ID suffix
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());

    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    // The CustomProcessInstanceInterceptor should append "-custom" to tenant ID
    assertThat(migratedInstance.tenantId()).endsWith("-custom");
  }

  @Test
  public void shouldApplyCustomActivityInstanceInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    repositoryService.activateProcessDefinitionByKey("simpleProcess", false, null);

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Temporarily disable ComplexEntityInterceptor to test CustomActivityInstanceInterceptor alone
    configuredEntityInterceptors.removeIf(interceptor ->
        interceptor.getClass().getSimpleName().equals("ComplexEntityInterceptor"));

    // Run history migration
    historyMigrator.start();

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());
    assertThat(migratedProcessInstances).isNotEmpty();

    Long processInstanceKey = migratedProcessInstances.getFirst().processInstanceKey();

    // Verify flow nodes were migrated with custom tenant ID prefix
    List<FlowNodeInstanceEntity> migratedFlowNodes =
        rdbmsService.getFlowNodeInstanceReader()
            .search(io.camunda.search.query.FlowNodeInstanceQuery.of(queryBuilder ->
                queryBuilder.filter(filterBuilder ->
                    filterBuilder.processInstanceKeys(processInstanceKey))))
            .items();

    assertThat(migratedFlowNodes).isNotEmpty();

    // The CustomActivityInstanceInterceptor should prepend "PREFIX_" to tenant ID
    for (FlowNodeInstanceEntity flowNode : migratedFlowNodes) {
      assertThat(flowNode.tenantId()).startsWith("PREFIX_");
    }
  }

  @Test
  public void shouldHandleMultipleEntityTypes() {
    // Deploy and migrate a simple process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    repositoryService.activateProcessDefinitionByKey("simpleProcess", false, null);

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.start();

    // Verify both process instances and flow nodes were migrated
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());

    assertThat(migratedProcessInstances).isNotEmpty();

    Long processInstanceKey = migratedProcessInstances.getFirst().processInstanceKey();

    List<FlowNodeInstanceEntity> migratedFlowNodes =
        rdbmsService.getFlowNodeInstanceReader()
            .search(io.camunda.search.query.FlowNodeInstanceQuery.of(queryBuilder ->
                queryBuilder.filter(filterBuilder ->
                    filterBuilder.processInstanceKeys(processInstanceKey))))
            .items();

    assertThat(migratedFlowNodes).isNotEmpty();

    // ComplexEntityInterceptor handles both types and should set tenant ID to "complex-tenant"
    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    assertThat(migratedInstance.tenantId()).isEqualTo("complex-tenant");

    for (FlowNodeInstanceEntity flowNode : migratedFlowNodes) {
      assertThat(flowNode.tenantId()).isEqualTo("complex-tenant");
    }
  }
}

