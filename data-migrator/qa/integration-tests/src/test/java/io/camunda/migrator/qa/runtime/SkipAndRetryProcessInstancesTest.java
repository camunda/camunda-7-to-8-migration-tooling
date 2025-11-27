/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime;

import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.PROCESS_INSTANCE_NOT_EXISTS;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureTrue;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
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
  private RuntimeService runtimeService;

  @Autowired
  private TaskService taskService;

  @Autowired
  private HistoryMigrator historyMigrator;

  @Test
  public void shouldSkipMultiInstanceProcessMigration() {
    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    List<IdKeyDbModel> skippedProcessInstanceIds = findSkippedRuntimeProcessInstances().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().getC7Id()).isEqualTo(process.getId());

    logs.assertContains(String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(),
        String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask")));
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

    List<IdKeyDbModel> skippedProcessInstanceIds = dbClient.findSkippedProcessInstances().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().getC7Id()).isEqualTo(process.getId());

    // Logs should contains the activityId without the multi-instance body suffix
    String activityIdWithoutMultiInstanceBody = "ServiceTask_1";
    logs.assertContains(
        String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(),
            String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, activityIdWithoutMultiInstanceBody)));
  }

  @Test
  public void shouldSkipMultiLevelMultiInstanceProcessMigration() {
    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    deployer.deployProcessInC7AndC8("callMultiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("callMultiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when running runtime migration
    runtimeMigrator.start();

    // then the instance was not migrated and marked as skipped
    assertThatProcessInstanceCountIsEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = findSkippedRuntimeProcessInstances().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().getC7Id()).isEqualTo(process.getId());

    logs.assertContains(String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), process.getId(),
        String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask")));
  }

  @Test
  public void shouldSkipAgainAProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();
    ensureTrue("Unexpected state: one process instance should be skipped",
        findSkippedRuntimeProcessInstances().size() == 1);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was not migrated and still marked as skipped
    assertThatProcessInstanceCountIsEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = findSkippedRuntimeProcessInstances().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().getC7Id()).isEqualTo(process.getId());

    var events = logs.getEvents();
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
    ensureTrue("Unexpected process state: two tasks should be active", taskService.createTaskQuery().count() == 2);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was migrated
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(processInstances.size()).isEqualTo(1);
    ProcessInstance processInstance = processInstances.getFirst();
    assertThat(processInstance.getProcessDefinitionId()).isEqualTo(process.getProcessDefinitionKey());

    // and the key updated
    assertThat(dbClient.findC8KeyByC7IdAndType(process.getId(), IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE)).isNotNull();

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
    ensureTrue("Unexpected state: one process instance should be skipped",
        dbClient.findSkippedProcessInstances().size() == 1);

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
    ensureTrue("Unexpected state: 10 process instances should be skipped",
        dbClient.countSkippedByType(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE) == 10);
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

    // and skipped instances were not migrated
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE)).isEqualTo(10);
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

    // and no migration was done
    assertThat(dbClient.findAllC7Ids().size()).isEqualTo(0);
  }

  @Test
  public void shouldMigrateRuntimeProcessInstanceAfterHistoryMigrationWithSameId() {
    // given a process instance in C7 that will create both history and runtime entries with same ID
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var completedProcess = runtimeService.startProcessInstanceByKey("simpleProcess");
    String processInstanceId = completedProcess.getId();

    // when running history migration first
    historyMigrator.start();

    // then verify history process instance was migrated
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(processInstanceId, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isTrue();
    // then verify runtime process instance was not yet migrated
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(processInstanceId, IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE)).isFalse();

    // when running runtime migration afterwards
    runtimeMigrator.start();

    // then verify runtime process instance was also migrated successfully
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(processInstanceId, IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE)).isTrue();

    // Verify C8 process instance was created
    List<ProcessInstance> c8ProcessInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(c8ProcessInstances.size()).isEqualTo(1);
  }

}