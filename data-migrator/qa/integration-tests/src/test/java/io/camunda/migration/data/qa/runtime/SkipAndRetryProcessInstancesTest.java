/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.runtime;

import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.PROCESS_INSTANCE_NOT_EXISTS;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static io.camunda.migration.date.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(locations = "classpath:application-warn.properties")
class SkipAndRetryProcessInstancesTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  @Autowired
  protected HistoryMigrator historyMigrator;

  @Autowired
  protected RdbmsService rdbmsService;

  @Test
  public void shouldSkipMultiInstanceProcessMigration() {
    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    assertThat(taskCount)
        .as("Unexpected process state: one task and three parallel tasks should be created")
        .isEqualTo(4L);

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    // Assert via logs instead of querying database
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, process.getId(),
        formatMessage(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask")));
  }


  @Test
  public void shouldSkipProcessWithMultiInstanceServiceTask() {
    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceServiceTaskProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceServiceTaskProcess");

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);


    // Logs should contains the activityId without the multi-instance body suffix
    String activityIdWithoutMultiInstanceBody = "ServiceTask_1";
    logs.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, process.getId(),
            formatMessage(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, activityIdWithoutMultiInstanceBody)));
  }

  @Test
  public void shouldSkipMultiLevelMultiInstanceProcessMigration() {
    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    deployer.deployProcessInC7AndC8("callMultiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("callMultiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    assertThat(taskCount)
        .as("Unexpected process state: one task and three parallel tasks should be created")
        .isEqualTo(4L);

    // when running runtime migration
    runtimeMigrator.start();

    // then the instance was not migrated and marked as skipped
    assertThatProcessInstanceCountIsEqualTo(0);

    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, process.getId(),
        formatMessage(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask")));
  }

  @Test
  public void shouldSkipAgainAProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();

    // Verify the instance was skipped via logs
    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
            .filter(event -> event.getMessage()
                .contains(
                    String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(), ""))))
        .hasSize(1);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was not migrated and still marked as skipped
    assertThatProcessInstanceCountIsEqualTo(0);

    events = logs.getEvents();
    Assertions.assertThat(events.stream()
            .filter(event -> event.getMessage()
                .contains(
                    String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(), ""))))
        .hasSize(2);
  }

  @Test
  public void shouldMigrateFixedProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();

    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
            .filter(event -> event.getMessage()
                .contains(
                    String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(), ""))))
        .hasSize(1);

    // and given the process state changed after skipping and is now eligible for migration
    for (Task task : taskService.createTaskQuery().taskDefinitionKey("multiUserTask").list()) {
      taskService.complete(task.getId());
    }
    assertThat(taskService.createTaskQuery().count())
        .as("Unexpected process state: two tasks should be active")
        .isEqualTo(2L);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was migrated
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(processInstances.size()).isEqualTo(1);
    ProcessInstance processInstance = processInstances.getFirst();
    assertThat(processInstance.getProcessDefinitionId()).isEqualTo(process.getProcessDefinitionKey());


    events = logs.getEvents();
    // and no additional skipping logs (still 1, not 2 matches)
    Assertions.assertThat(events.stream()
            .filter(event -> event.getMessage()
                .contains(
                    String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(), ""))))
        .hasSize(1);
  }

  @Test
  public void shouldLogWarningWhenProcessInstanceHasBeenCompleted(CapturedOutput output) {
    // given skipped process instance
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();

    // Verify via logs that instance was skipped
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, process.getId(), ""));

    runtimeService.deleteProcessInstance(process.getId(), "State cannot be fixed!");

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    assertThat(output.getOut()).containsPattern(
        "WARN(.*)" + PROCESS_INSTANCE_NOT_EXISTS.replace("{}", "[a-f0-9-]+").replace("?", "\\?"));
  }

  @Test
  public void shouldListSkippedProcessInstances(CapturedOutput output) {
    // given skipped process instance
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    List<String> processInstancesIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      processInstancesIds.add(runtimeService.startProcessInstanceByKey("multiInstanceProcess").getProcessInstanceId());
    }
    runtimeMigrator.start();

    // Verify via logs that 10 instances were skipped
    for (String processInstanceId : processInstancesIds) {
      logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, processInstanceId, ""));
    }

    // when running migration with list skipped mode
    runtimeMigrator.setMode(LIST_SKIPPED);
    runtimeMigrator.start();

    // then all skipped process instances were listed
    String expectedHeader = "Previously skipped \\[" + IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE.getDisplayName() + "s\\]:";
    String regex = expectedHeader + "\\R((?:.+\\R){9}.+)";
    assertThat(output.getOut()).containsPattern(regex);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output.getOut());

    final String capturedIds = matcher.find() ? matcher.group(1) : "";
    processInstancesIds.forEach(processInstanceId -> assertThat(capturedIds).contains(processInstanceId));
  }

  @Test
  public void shouldDisplayNoSkippedInstances(CapturedOutput output) {
    // given no skipped instances

    // when running migration with list skipped mode
    runtimeMigrator.setMode(LIST_SKIPPED);
    runtimeMigrator.start();

    // then expected message is printed
    String expectedMessage = "No entities of type [" + IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE.getDisplayName() + "] were skipped during previous migration";
    assertThat(output.getOut().trim()).endsWith(expectedMessage);

    // and no migration was done (still 0 instances in C8)
    assertThatProcessInstanceCountIsEqualTo(0);
  }

  @Test
  public void shouldMigrateRuntimeProcessInstanceAfterHistoryMigrationWithSameId() {
    // given a process instance in C7 that will create both history and runtime entries with same ID
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var c7ProcDefKey = runtimeService.startProcessInstanceByKey("simpleProcess").getProcessDefinitionKey();

    // when running history migration first
    historyMigrator.start();

    // then verify history migration completed (we can't easily query history from runtime test)
    // History migration should not interfere with runtime migration

    // when running runtime migration afterwards
    runtimeMigrator.start();

    // then verify runtime process instance was migrated successfully
    List<ProcessInstance> c8ProcessInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(c8ProcessInstances.size()).isEqualTo(1);

    // and verify historic process instance exists in RDBMS
    List<ProcessInstanceEntity> historicProcessInstances = rdbmsService.getProcessInstanceReader()
        .search(ProcessInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(c7ProcDefKey)))).items();
    assertThat(historicProcessInstances.size()).isEqualTo(1);
  }

}