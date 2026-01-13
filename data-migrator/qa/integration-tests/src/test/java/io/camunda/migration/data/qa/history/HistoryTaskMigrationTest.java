/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;

@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryTaskMigrationTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldHistoricUserTasksBeMigrated() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    for(int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    historyMigration.completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(5);
    for (ProcessInstanceEntity processInstance : processInstances) {
      // and process instance has expected state
      assertThat(processInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.COMPLETED);
      Long processInstanceKey = processInstance.processInstanceKey();
      List<UserTaskEntity> userTasks = historyMigration.searchHistoricUserTasks(processInstanceKey);
      assertThat(userTasks).hasSize(1);
      assertThat(userTasks.getFirst().state()).isEqualTo(UserTaskEntity.UserTaskState.COMPLETED);
      assertThat(historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT)).size().isEqualTo(1);
      assertThat(historyMigration.searchHistoricFlowNodesForType(processInstanceKey, END_EVENT)).size().isEqualTo(1);
    }
  }
}
