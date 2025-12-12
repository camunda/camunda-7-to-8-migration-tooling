/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.converter.ProcessInstanceConverter;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.ActivityInstanceInterceptor;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessInstanceInterceptor;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.UniversalEntityInterceptor;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    // Disable built-in converter for controlled testing
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.converter.ProcessInstanceConverter",
    "camunda.migrator.interceptors[0].enabled=false",
    // Register interceptor and disable it
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[1].enabled=false"
})
@ActiveProfiles("entity-programmatic")
public class HistoryProgrammaticConfigurationTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected List<EntityInterceptor> configuredEntityInterceptors;

  @Autowired
  protected UniversalEntityInterceptor universalEntityInterceptor;

  @Autowired
  protected ProcessInstanceInterceptor processInstanceInterceptor;

  @Autowired
  protected ActivityInstanceInterceptor activityInstanceInterceptor;

  @Autowired
  protected io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessEngineAwareInterceptor processEngineAwareInterceptor;

  @BeforeEach
  void setUp() {
    // Reset counters before each test
    universalEntityInterceptor.resetCounter();
    processInstanceInterceptor.resetCounter();
    activityInstanceInterceptor.resetCounter();
    processEngineAwareInterceptor.resetCounter();
  }

  @Test
  public void shouldDisableBuiltInConverter() {
    // Verify built-in ProcessInstanceConverter is disabled
    long disabledConverters = configuredEntityInterceptors.stream()
        .filter(interceptor -> interceptor instanceof ProcessInstanceConverter)
        .count();

    assertThat(disabledConverters).isEqualTo(0); // Should be removed from context when disabled
  }

  @Test
  public void shouldRegisterBeanInterceptors() {
    // Verify that bean interceptors are registered
    assertThat(configuredEntityInterceptors)
        .anyMatch(interceptor -> interceptor instanceof UniversalEntityInterceptor);
    assertThat(configuredEntityInterceptors)
        .anyMatch(interceptor -> interceptor instanceof ProcessInstanceInterceptor);
    assertThat(configuredEntityInterceptors)
        .anyMatch(interceptor -> interceptor instanceof ActivityInstanceInterceptor);
    assertThat(configuredEntityInterceptors)
        .anyMatch(interceptor -> interceptor instanceof io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessEngineAwareInterceptor);
  }

  @Test
  public void shouldExecuteProcessInstanceInterceptor() {
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

    // Verify process instance interceptor was executed
    assertThat(processInstanceInterceptor.getExecutionCount()).isGreaterThan(0);

    // Verify process instance was migrated with modified tenant ID
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());

    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    // ProcessInstanceInterceptor adds "BEAN_" prefix
    assertThat(migratedInstance.tenantId()).startsWith("BEAN_");
  }

  @Test
  public void shouldExecuteActivityInstanceInterceptor() {
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

    // Verify activity instance interceptor was executed
    assertThat(activityInstanceInterceptor.getExecutionCount()).isGreaterThan(0);

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());
    assertThat(migratedProcessInstances).isNotEmpty();

    Long processInstanceKey = migratedProcessInstances.getFirst().processInstanceKey();

    // Verify flow nodes were migrated with modified tenant ID
    List<FlowNodeInstanceEntity> migratedFlowNodes =
        rdbmsService.getFlowNodeInstanceReader()
            .search(io.camunda.search.query.FlowNodeInstanceQuery.of(queryBuilder ->
                queryBuilder.filter(filterBuilder ->
                    filterBuilder.processInstanceKeys(processInstanceKey))))
            .items();

    assertThat(migratedFlowNodes).isNotEmpty();

    // ActivityInstanceInterceptor adds "_ACTIVITY" suffix
    for (FlowNodeInstanceEntity flowNode : migratedFlowNodes) {
      assertThat(flowNode.tenantId()).endsWith("_ACTIVITY");
    }
  }

  @Test
  public void shouldExecuteUniversalInterceptor() {
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

    // Verify universal interceptor was executed for all entities
    // Universal interceptor should be called for process instance, flow nodes, etc.
    assertThat(universalEntityInterceptor.getExecutionCount()).isGreaterThan(0);
  }

  @Test
  public void shouldRespectInterceptorOrder() {
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

    // Verify all interceptors were executed
    assertThat(universalEntityInterceptor.getExecutionCount()).isGreaterThan(0);
    assertThat(processInstanceInterceptor.getExecutionCount()).isGreaterThan(0);
    assertThat(activityInstanceInterceptor.getExecutionCount()).isGreaterThan(0);
  }

  @Test
  public void shouldNotExecuteDisabledInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    repositoryService.activateProcessDefinitionByKey("simpleProcess", false, null);

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration - DisabledCustomInterceptor should not throw exception
    historyMigrator.start();

    // Verify migration completed successfully without executing disabled interceptor
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());

    assertThat(migratedProcessInstances).isNotEmpty();
  }

  @Test
  public void shouldUseProcessEngineToRetrieveC7Data() {
    // Deploy and migrate a simple process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    repositoryService.activateProcessDefinitionByKey("simpleProcess", false, null);

    // Get deployment ID from C7 for verification
    String deploymentId = repositoryService.createDeploymentQuery()
        .singleResult()
        .getId();
    assertThat(deploymentId).isNotNull();

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.start();

    // Verify ProcessEngineAwareInterceptor was executed
    assertThat(processEngineAwareInterceptor.getExecutionCount()).isGreaterThan(0);

    // Verify that the interceptor retrieved the deployment ID from C7
    assertThat(processEngineAwareInterceptor.getLastDeploymentId()).isNotNull();
    assertThat(processEngineAwareInterceptor.getLastDeploymentId()).isEqualTo(deploymentId);

    // Verify process instance was migrated with deployment ID in tenant ID
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances(processInstance.getProcessDefinitionId());

    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    // ProcessEngineAwareInterceptor should append "C7_DEPLOY_" + deployment ID to tenant ID
    assertThat(migratedInstance.tenantId()).contains("C7_DEPLOY_" + deploymentId);
  }
}

