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

    var complexInterceptor = migratorProperties.getInterceptors().get(7);
    assertThat(complexInterceptor.getClassName()).isEqualTo("io.camunda.migration.data.qa.history.entity.interceptor.pojo.ComplexEntityInterceptor");
    assertThat(complexInterceptor.getProperties()).containsEntry("targetTenantId", "complex-tenant");
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
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.migrate();

    // Verify process instance was migrated
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess");

    assertThat(migratedProcessInstances).isNotEmpty();

    // The ComplexEntityInterceptor should be the last one to execute and set targetTenantId
    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    assertThat(migratedInstance.tenantId()).isEqualTo("complex-tenant");
  }

  @Test
  public void shouldHandleMultipleEntityTypes() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.migrate();

    // Verify both process instances and flow nodes were migrated
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess");

    assertThat(migratedProcessInstances).isNotEmpty();

    Long processInstanceKey = migratedProcessInstances.getFirst().processInstanceKey();

    List<FlowNodeInstanceEntity> migratedFlowNodes =
        rdbmsService.getFlowNodeInstanceReader()
            .search(io.camunda.search.query.FlowNodeInstanceQuery.of(queryBuilder ->
                queryBuilder.filter(filterBuilder ->
                    filterBuilder.tenantIds("complex-tenant"))))
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

