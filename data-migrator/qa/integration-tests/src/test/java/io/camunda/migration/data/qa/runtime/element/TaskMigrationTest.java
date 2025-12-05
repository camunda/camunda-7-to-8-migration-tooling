/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.runtime.element;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaAssert;

@SpringBootTest
public class TaskMigrationTest extends AbstractElementMigrationTest {

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected CamundaClient camundaClient;

  @Test
  public void shouldMigrateUserTaskInstance() {
    // given
    deployer.deployProcessInC7AndC8("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when
    runtimeMigrator.start();

    // then
    assertThat(byTaskName("UserTaskName")).isCreated().hasElementId("userTaskId");
  }

  @Test
  public void shouldMigrateSecondUserTask() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var simpleProcess = runtimeService.startProcessInstanceByKey("simpleProcess");
    Task task1 = taskService.createTaskQuery().taskDefinitionKey("userTask1").singleResult();
    taskService.complete(task1.getId());
    Task task2 = taskService.createTaskQuery().taskDefinitionKey("userTask2").singleResult();
    Assertions.assertThat(task2)
        .as("Unexpected process state: userTask2 should exist")
        .isNotNull();
    Assertions.assertThat(task2.getTaskState())
        .as("Unexpected process state: userTask2 should be 'created'")
        .isEqualToIgnoringCase("created");

    // when
    runtimeMigrator.start();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertEquals(1, processInstances.size());
    ProcessInstance processInstance = processInstances.getFirst();
    assertEquals(simpleProcess.getProcessDefinitionKey(), processInstance.getProcessDefinitionId());

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .isActive()
        .hasActiveElements(byId("userTask2"))
        .hasVariable("legacyId", simpleProcess.getProcessInstanceId());

    CamundaAssert.assertThat(byTaskName("User Task 2"))
        .isCreated()
        .hasElementId("userTask2")
        .hasAssignee(null);
  }

  @Override
  protected Stream<Arguments> elementScenarios_activeElementPostMigration() {
    return Stream.of(Arguments.of("sendTaskProcess.bpmn", "sendTaskProcessId", "sendTaskId"),
        Arguments.of("receiveTaskProcess.bpmn", "receiveTaskProcessId", "receiveTaskId"),
        Arguments.of("userTaskProcess.bpmn", "userTaskProcessId", "userTaskId"),
        Arguments.of("serviceTaskProcess.bpmn", "serviceTaskProcessId", "serviceTaskId"));
  }
}
