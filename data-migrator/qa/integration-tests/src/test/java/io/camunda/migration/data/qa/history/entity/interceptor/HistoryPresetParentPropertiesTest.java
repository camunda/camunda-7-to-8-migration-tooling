/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.qa.history.entity.interceptor.bean.PresetProcessInstanceInterceptor",
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.impl.interceptor.history.entity.ProcessInstanceTransformer",
    "camunda.migrator.interceptors[1].enabled=false",
    "camunda.migrator.interceptors[2].className=io.camunda.migration.data.qa.history.entity.interceptor.bean"
        + ".PresetDecisionInstanceInterceptor",
})
public class HistoryPresetParentPropertiesTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldExecuteProcessInstancePresetInterceptor() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Complete the process
    var task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // Run history migration
    historyMigrator.migrateProcessInstances();

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances =
        searchHistoricProcessInstances("simpleProcess", true);
    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity processInstanceEntity = migratedProcessInstances.getFirst();
    assertThat(processInstanceEntity.processInstanceKey()).isEqualTo(88888L);
    assertThat(processInstanceEntity.processDefinitionKey()).isEqualTo(12345L);
  }

  @Test
    public void shouldExecuteProcessInstancePresetInterceptorWithoutMigratedParent() {
    // Deploy and migrate a simple process
    deployer.deployCamunda7Process("callActivityProcess.bpmn");
    deployer.deployCamunda7Process("calledActivitySubprocess.bpmn");

    var processInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    // Complete the process
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }
    // Delete the historic calling process instance to simulate missing parent
    historyService.deleteHistoricProcessInstance(processInstance.getId());


    // Run history migration
    historyMigrator.migrateProcessInstances();

    // Get the migrated process instance to get the key
    List<ProcessInstanceEntity> migratedProcessInstances = searchHistoricProcessInstances("calledProcessInstanceId", true);
    assertThat(migratedProcessInstances).isNotEmpty();

    ProcessInstanceEntity processInstanceEntity = migratedProcessInstances.getFirst();
    assertThat(processInstanceEntity.processInstanceKey()).isEqualTo(88888L);
    assertThat(processInstanceEntity.processDefinitionKey()).isEqualTo(12345L);
    assertThat(processInstanceEntity.parentProcessInstanceKey()).isEqualTo(67890L);
  }

  @Test
  public void shouldSkipDecisionInstanceWhenDecisionDefinitionIsSkipped() {
    Date now = ClockUtil.now();
    ClockUtil.setCurrentTime(now);
    // given: DMN and process with business rule task in C7
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().singleResult().getId();

    // when: Migrate decision instances WITHOUT decision definitions (creates real-world skip)
    historyMigrator.migrateDecisionInstances();

    // then: decision instance is migrated
    List<DecisionInstanceEntity> migratedInstances = searchHistoricDecisionInstances("simpleDecisionId");

    assertThat(migratedInstances).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDecisionId",
            now,
            7L,
            4L,
            1L,
            2L,
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "\"B\"",
            "inputA", "\"A\"",
            "outputB", "\"B\""));
  }
}
