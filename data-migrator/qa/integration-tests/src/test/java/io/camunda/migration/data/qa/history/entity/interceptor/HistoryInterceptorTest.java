/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.ActivityInstanceInterceptor;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    // Disable built-in trasformer for controlled testing
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer",
    "camunda.migrator.interceptors[0].enabled=false",
    // Register interceptor and disable it
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[1].enabled=false",
    "camunda.migrator.interceptors[2].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessInstanceInterceptor",
    "camunda.migrator.interceptors[2].enabled=false" })
@ActiveProfiles("entity-programmatic")
public class HistoryInterceptorTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected ActivityInstanceInterceptor activityInstanceInterceptor;

  @Autowired
  protected io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessEngineAwareInterceptor processEngineAwareInterceptor;

  @BeforeEach
  void setUp() {
    // Reset counters before each test
    activityInstanceInterceptor.resetCounter();
    processEngineAwareInterceptor.resetCounter();
  }

  @Test
  public void shouldExecuteActivityInstanceInterceptor() {
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

    // Verify activity instance interceptor was executed
    assertThat(activityInstanceInterceptor.getExecutionCount()).isGreaterThan(0);

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess", true);
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

    // ActivityInstanceInterceptor adds "_DEFAULT" suffix
    for (FlowNodeInstanceEntity flowNode : migratedFlowNodes) {
      assertThat(flowNode.tenantId()).contains("BEAN_DEFAULT");
    }
  }

  @Test
  public void shouldUseProcessEngineToRetrieveC7Data() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

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
    historyMigrator.migrate();

    // Verify ProcessEngineAwareInterceptor was executed
    assertThat(processEngineAwareInterceptor.getExecutionCount()).isGreaterThan(0);

    // Verify that the interceptor retrieved the deployment ID from C7
    assertThat(processEngineAwareInterceptor.getLastDeploymentId()).isNotNull();
    assertThat(processEngineAwareInterceptor.getLastDeploymentId()).isEqualTo(deploymentId);

    // Verify process instance was migrated with deployment ID in tenant ID
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess", true);

    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    // ProcessEngineAwareInterceptor should append "C7_DEPLOY_" + deployment ID to tenant ID
    assertThat(migratedInstance.tenantId()).contains("C7_DEPLOY_" + deploymentId);
  }
}

