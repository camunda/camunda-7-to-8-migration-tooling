/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.cleanup;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.CleanupExtension;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.migration.data.qa.extension.RdbmsQueryExtension;
import io.camunda.migration.data.qa.util.ProcessDefinitionDeployer;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for disabled auto-cancel cleanup.
 * When auto-cancel cleanup is disabled, history cleanup dates should be null.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "camunda.migrator.history.auto-cancel.cleanup.enabled=false",
    "logging.level.io.camunda.migration.data.HistoryMigrator=DEBUG"
})
public class HistoryCleanupDisabledMigrationTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @RegisterExtension
  RdbmsQueryExtension rdbmsQuery = new RdbmsQueryExtension();

  @RegisterExtension
  CleanupExtension cleanup = new CleanupExtension(rdbmsQuery);

  @Autowired
  protected ProcessDefinitionDeployer deployer;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  @Test
  public void shouldSetNullCleanupDateWhenAutoCancelDisabled() {
    // given - deploy and start a process instance that remains active
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history with auto-cancel TTL disabled
    historyMigration.getMigrator().migrate();

    // then - process instance should be auto-canceled but with null history cleanup date
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    assertThat(migratedInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
    assertThat(migratedInstance.endDate()).isNotNull();

    // Verify cleanup date is null using direct SQL query
    OffsetDateTime cleanupDate = cleanup.getProcessInstanceCleanupDate(migratedInstance.processInstanceKey());
    assertThat(cleanupDate).isNull();
  }

  @Test
  public void shouldSetNullCleanupDateForFlowNodesWhenAutoCancelDisabled() {
    // given - deploy and start a process instance with flow nodes
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history with auto-cancel TTL disabled
    historyMigration.getMigrator().migrate();

    // then - flow nodes should have null cleanup dates
    var processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    var flowNodes = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(flowNodes).isNotEmpty();

    for (FlowNodeInstanceEntity flowNode : flowNodes) {
      OffsetDateTime cleanupDate = cleanup.getFlowNodeCleanupDate(flowNode.flowNodeInstanceKey());
      assertThat(cleanupDate).isNull();
    }
  }

  @Test
  public void shouldSetNullCleanupDateForUserTasksWhenAutoCancelDisabled() {
    // given - deploy and start a process instance with a user task
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history with auto-cancel TTL disabled
    historyMigration.getMigrator().migrate();

    // then - user tasks should have null cleanup dates
    var processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    Long processInstanceKey = processInstances.getFirst().processInstanceKey();

    var userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).isNotEmpty();

    for (UserTaskEntity userTask : userTasks) {
      OffsetDateTime cleanupDate = cleanup.getUserTaskCleanupDate(userTask.userTaskKey());
      assertThat(cleanupDate).isNull();
    }
  }

  @Test
  public void shouldSetNullCleanupDateForVariablesWhenAutoCancelDisabled() {
    // given - deploy and start a process instance with variables
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    String processInstanceId = runtimeService.startProcessInstanceByKey("userTaskProcessId").getId();
    runtimeService.setVariable(processInstanceId, "testVar", "testValue");

    // when - migrate history with auto-cancel TTL disabled
    historyMigration.getMigrator().migrate();

    // then - variables should have null cleanup dates
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    Long processInstanceKey = processInstances.getFirst().processInstanceKey();
    List<OffsetDateTime> variableCleanupDates = cleanup.getVariableCleanupDates(processInstanceKey);

    assertThat(variableCleanupDates).isNotEmpty();
    for (OffsetDateTime cleanupDate : variableCleanupDates) {
      assertThat(cleanupDate).isNull();
    }
  }
}

