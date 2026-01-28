/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.cleanup;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.START_EVENT;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.CleanupExtension;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.migration.data.qa.extension.RdbmsQueryExtension;
import io.camunda.migration.data.qa.util.ProcessDefinitionDeployer;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for history cleanup date calculation and cascading end dates.
 *
 * <p>Uses whitebox testing approach to verify history cleanup behavior:</p>
 * <ul>
 *   <li>Direct SQL queries to C8 database tables using {@link RdbmsQueryExtension}</li>
 *   <li>Verification of HISTORY_CLEANUP_DATE column values</li>
 *   <li>State conversions (ACTIVE -> CANCELED)</li>
 *   <li>End date cascading from process instance to child entities</li>
 * </ul>
 *
 * <p>The historyCleanupDate is not exposed via public API, so these tests query
 * the database directly to verify correct cleanup date calculation (endDate + TTL).</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
    "camunda.migrator.history.auto-cancel.cleanup.ttl=P30D",
    "logging.level.io.camunda.migration.data.HistoryMigrator=DEBUG"
})
public class HistoryCleanupMigrationTest extends AbstractMigratorTest {

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

  @AfterEach
  public void resetClock() {
    ClockUtil.reset();
  }

  @Test
  public void shouldAutoCancelActiveProcessInstanceWithEndDate() {
    // given - deploy and start a process instance that remains active
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Date startTime = new Date();
    ClockUtil.setCurrentTime(startTime);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    Date migrationTime = new Date(startTime.getTime() + 5_000); // +5 seconds
    ClockUtil.setCurrentTime(migrationTime);

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - process instance should be auto-canceled with endDate set to "now"
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    // Verify state conversion: ACTIVE -> CANCELED
    assertThat(migratedInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
    // Verify endDate was set to "startTime" during migration
    assertThat(migratedInstance.endDate())
        .isEqualTo(convertDate(migrationTime));

    // Whitebox test: Query database directly to verify history cleanup date
     Long processInstanceKey = migratedInstance.processInstanceKey();
     OffsetDateTime cleanupDate = cleanup.getProcessInstanceCleanupDate(processInstanceKey);
    // Verify cleanup date is calculated as endDate + 30 days (from test property)
    assertThat(cleanupDate).isEqualTo(migratedInstance.endDate().plus(Duration.ofDays(30)));
  }

  @Test
  public void shouldKeepOriginalEndDateForCompletedProcessInstance() {
    // given - deploy and complete a process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Date startTime = new Date();
    ClockUtil.setCurrentTime(startTime);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // Complete the user task at a later time
    Date completionTime = new Date(startTime.getTime() + 5_000); // +5 seconds
    ClockUtil.setCurrentTime(completionTime);
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - completed process instance should keep its original end date
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    // Verify state remains COMPLETED
    assertThat(migratedInstance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.COMPLETED);
    // Verify endDate exists and matches the completion time (from C7)
    assertThat(migratedInstance.endDate())
        .isEqualTo(convertDate(completionTime));
  }

  @Test
  public void shouldCascadeEndDateToFlowNodesForActiveProcessInstance() {
    // given - deploy and start a process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - only cancelled/terminated flow nodes should inherit the process instance's end date
    var processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);

    ProcessInstanceEntity migratedInstance = processInstances.getFirst();
    Long processInstanceKey = migratedInstance.processInstanceKey();
    OffsetDateTime processInstanceEndDate = migratedInstance.endDate();

    assertThat(processInstanceEndDate).isNotNull();

    // Check start event - should be COMPLETED with its own end date (not cascaded)
    var startEvents = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, START_EVENT);
    assertThat(startEvents).hasSize(1);
    FlowNodeInstanceEntity startEvent = startEvents.getFirst();
    assertThat(startEvent.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.COMPLETED);
    assertThat(startEvent.endDate()).isNotEqualTo(processInstanceEndDate);


    // Check user task flow node - should be TERMINATED and inherit process instance end date
    var userTaskFlowNodes = historyMigration.searchHistoricFlowNodesForType(processInstanceKey, USER_TASK);
    assertThat(userTaskFlowNodes).hasSize(1);
    FlowNodeInstanceEntity userTaskFlowNode = userTaskFlowNodes.getFirst();
    assertThat(userTaskFlowNode.state()).isEqualTo(FlowNodeInstanceEntity.FlowNodeState.TERMINATED);
    assertThat(userTaskFlowNode.endDate()).isEqualTo(processInstanceEndDate);

  }

  @Test
  public void shouldHandleMultipleActiveProcessInstancesConsistently() {
    // given - deploy and start multiple active process instances
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    for (int i = 0; i < 3; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }

    // when - migrate history
    historyMigration.getMigrator().migrate();

    // then - all process instances should be migrated with consistent behavior
    List<ProcessInstanceEntity> processInstances = historyMigration.searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(3);

    for (ProcessInstanceEntity instance : processInstances) {
      // All should be auto-canceled with endDate set
      assertThat(instance.state()).isEqualTo(ProcessInstanceEntity.ProcessInstanceState.CANCELED);
      assertThat(instance.endDate()).isNotNull();
       OffsetDateTime cleanupDate = cleanup.getProcessInstanceCleanupDate(instance.processInstanceKey());
       assertThat(cleanupDate).isNotNull();
    }
  }

}