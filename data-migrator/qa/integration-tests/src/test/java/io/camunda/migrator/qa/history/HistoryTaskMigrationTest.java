/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryTaskMigrationTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldHistoricUserTasksBeMigrated() {
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    // given state in c7
    for(int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();

    // when history is migrated
    historyMigrator.migrate();

    // then expected number of historic process instances
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances.size()).isEqualTo(5);
    for (ProcessInstanceEntity processInstance : processInstances) {
      // and process instance has expected state
      assertThat(processInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.COMPLETED);
      Long processInstanceKey = processInstance.processInstanceKey();
      List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstanceKey);
      assertThat(userTasks.size()).isEqualTo(1);
      assertThat(userTasks.getFirst().state()).isEqualTo(UserTaskEntity.UserTaskState.COMPLETED);
      assertThat(searchHistoricFlowNodesForType(processInstanceKey, START_EVENT)).size().isEqualTo(1);
      assertThat(searchHistoricFlowNodesForType(processInstanceKey, END_EVENT)).size().isEqualTo(1);
    }
  }
}
