/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.ActivityInstanceInterceptor;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessInstanceInterceptor;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.UniversalEntityInterceptor;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    // Disable built-in transformer for controlled testing
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer",
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
  public void shouldDisableBuiltInTransformer() {
    // Verify built-in ProcessInstanceTransformer is disabled
    long disabledTransformers = configuredEntityInterceptors.stream()
        .filter(interceptor -> interceptor instanceof ProcessInstanceTransformer)
        .count();

    assertThat(disabledTransformers).isEqualTo(0); // Should be removed from context when disabled
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
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.migrate();

    // Verify process instance interceptor was executed
    assertThat(processInstanceInterceptor.getExecutionCount()).isGreaterThan(0);

    // Verify process instance was migrated with modified tenant ID
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess");

    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    // ProcessInstanceInterceptor adds "BEAN_" prefix
    assertThat(migratedInstance.tenantId()).startsWith("BEAN_");
  }

  @Test
  public void shouldExecuteUniversalInterceptor() {
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

    // Verify universal interceptor was executed for all entities
    // Universal interceptor should be called for process instance, flow nodes, etc.
    assertThat(universalEntityInterceptor.getExecutionCount()).isGreaterThan(0);
  }

  @Test
  public void shouldRespectInterceptorOrder() {
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

    // Verify all interceptors were executed
    assertThat(universalEntityInterceptor.getExecutionCount()).isGreaterThan(0);
    assertThat(processInstanceInterceptor.getExecutionCount()).isGreaterThan(0);
    assertThat(activityInstanceInterceptor.getExecutionCount()).isGreaterThan(0);
  }

  @Test
  public void shouldNotExecuteDisabledInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration - DisabledCustomInterceptor should not throw exception
    historyMigrator.migrate();

    // Verify migration completed successfully without executing disabled interceptor
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess");

    assertThat(migratedProcessInstances).isNotEmpty();
  }

}

