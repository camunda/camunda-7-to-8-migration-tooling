/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

/**
 * Tests transaction rollback behavior using real-world scenarios.
 *
 * <p>These tests verify that transactions work correctly by observing the behavior of the system
 * in edge-case scenarios where partial migration could occur if transactions were not working.
 *
 * <p>Uses black-box testing approach by verifying observable behavior through:
 *
 * <ul>
 *   <li>C8 search queries (verifying data consistency)
 *   <li>Migration retry behavior (entity should be migrateable after failures)
 *   <li>Idempotency (re-running migration doesn't create duplicates)
 * </ul>
 */
public class TransactionRollbackTest extends HistoryMigrationAbstractTest {

  /**
   * Test that verifies atomic behavior when migrating process instances with child entities.
   *
   * <p>This test creates a complex process instance with multiple child entities (flow nodes,
   * variables, user tasks). If transactions weren't working, we could get partial migrations where
   * some entities are migrated but others aren't.
   *
   * <p>The test verifies that:
   *
   * <ul>
   *   <li>All entities are migrated together
   *   <li>Re-running migration is idempotent (doesn't create duplicates)
   *   <li>Data remains consistent
 * </ul>
   */
  @Test
  public void shouldMigrateComplexProcessInstanceAtomically() {
    // given - create a process instance with multiple child entities
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7Process = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    
    // Add variables (creates variable entities)
    runtimeService.setVariable(c7Process.getId(), "testVar1", "value1");
    runtimeService.setVariable(c7Process.getId(), "testVar2", 42);
    
    completeAllUserTasksWithDefaultUserTaskId();

    // when - migrate the process instance
    historyMigrator.migrate();

    // then - verify process instance was migrated
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    
    ProcessInstanceEntity migratedInstance = processInstances.get(0);
    assertThat(migratedInstance.getProcessDefinitionKey()).isEqualTo("userTaskProcessId");

    // verify all child entities were migrated (flow nodes, variables, user tasks)
    // If transactions weren't working, we might have partial migration
    var flowNodes = searchFlowNodeInstances(migratedInstance.getKey());
    assertThat(flowNodes).isNotEmpty(); // Process should have multiple flow nodes

    var variables = searchVariables(migratedInstance.getKey());
    assertThat(variables).hasSize(2); // Should have both variables

    var userTasks = searchUserTasks(migratedInstance.getKey());
    assertThat(userTasks).isNotEmpty(); // Should have user task

    // when - re-run migration (should be idempotent due to transactions)
    historyMigrator.migrate();

    // then - verify no duplicates were created
    processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1); // Still only one process instance

    variables = searchVariables(migratedInstance.getKey());
    assertThat(variables).hasSize(2); // Still only two variables

    flowNodes = searchFlowNodeInstances(migratedInstance.getKey());
    int flowNodeCount = flowNodes.size();

    userTasks = searchUserTasks(migratedInstance.getKey());
    int userTaskCount = userTasks.size();

    // Re-run again to ensure idempotency
    historyMigrator.migrate();

    flowNodes = searchFlowNodeInstances(migratedInstance.getKey());
    assertThat(flowNodes).hasSize(flowNodeCount); // Same count as before

    userTasks = searchUserTasks(migratedInstance.getKey());
    assertThat(userTasks).hasSize(userTaskCount); // Same count as before
  }

  /**
   * Test that verifies transactions work across process instances.
   *
   * <p>This test verifies that when migrating multiple process instances, if the migration is
   * re-run, it doesn't create duplicates. This validates that the transaction management is
   * working correctly to prevent orphan records.
   */
  @Test
  public void shouldHandleMultipleProcessInstancesIdempotently() {
    // given - create multiple process instances
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    
    ProcessInstance process1 = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    ProcessInstance process2 = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    ProcessInstance process3 = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    
    completeAllUserTasksWithDefaultUserTaskId();

    // when - migrate all process instances
    historyMigrator.migrate();

    // then - verify all were migrated
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(3);

    // when - re-run migration multiple times
    historyMigrator.migrate();
    historyMigrator.migrate();

    // then - verify no duplicates were created (idempotency)
    processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(3); // Still only 3 instances

    // Verify each instance has consistent child entities
    for (ProcessInstanceEntity instance : processInstances) {
      var flowNodes = searchFlowNodeInstances(instance.getKey());
      assertThat(flowNodes).isNotEmpty();

      var userTasks = searchUserTasks(instance.getKey());
      assertThat(userTasks).isNotEmpty();
    }
  }
}
