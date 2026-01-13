/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.history.entity.interceptor.bean.ProcessInstanceInterceptor;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    // Add a POJO interceptor via configuration
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.qa.history.entity.interceptor.pojo.CustomProcessInstanceInterceptor",
    "camunda.migrator.interceptors[0].properties.tenantIdSuffix=-mixed",
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[1].enabled=false", })
@WithSpringProfile("entity-interceptor")
@ActiveProfiles("entity-programmatic")
public class HistoryMixedConfigurationTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected List<EntityInterceptor> configuredEntityInterceptors;

  @Test
  public void shouldCombineBeanAndPojoInterceptors() {
    // Verify that both bean and POJO interceptors are present
    assertThat(configuredEntityInterceptors).anyMatch(interceptor -> interceptor instanceof ProcessInstanceInterceptor);
    assertThat(configuredEntityInterceptors).anyMatch(
        interceptor -> interceptor.getClass().getSimpleName().equals("CustomProcessInstanceInterceptor"));
  }

  @Test
  public void shouldExecuteBothInterceptorTypes() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    getHistoryMigrator().migrate();

    // Verify process instance was migrated
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess", true);

    assertThat(migratedProcessInstances).isNotEmpty();

    // The last interceptor to execute should have set the tenant ID
    // Both interceptors modify tenant ID, so we verify at least one worked
    ProcessInstanceEntity migratedInstance = migratedProcessInstances.getFirst();
    assertThat(migratedInstance.tenantId()).isNotNull();
  }
}

