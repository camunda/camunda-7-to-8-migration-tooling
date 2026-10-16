/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.tests;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JobWorkerUserTaskTest {

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Test
  @Deployment(resources = "com/camunda/fixture/tests/job-worker-user-task.bpmn")
  public void searchesProcessTask() {
    ProcessInstance process =
        processEngineRule.getRuntimeService().startProcessInstanceByKey("job-worker-process");
    Task task =
        processEngineRule
            .getTaskService()
            .createTaskQuery()
            .processInstanceId(process.getId())
            .singleResult();

    assertNotNull(task);
    assertEquals("Task_Review", task.getTaskDefinitionKey());
  }

  @Test
  @Deployment(resources = "com/camunda/fixture/tests/job-worker-user-task.bpmn")
  public void assignsProcessTask() {
    ProcessInstance process =
        processEngineRule.getRuntimeService().startProcessInstanceByKey("job-worker-process");
    TaskService taskService = processEngineRule.getTaskService();
    Task task = taskService.createTaskQuery().processInstanceId(process.getId()).singleResult();

    assertNotNull(task);
    taskService.claim(task.getId(), "reviewer");
    assertEquals(
        "reviewer", taskService.createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
  }

  @Test
  @Deployment(resources = "com/camunda/fixture/tests/job-worker-user-task.bpmn")
  public void completesProcessTask() {
    ProcessInstance process =
        processEngineRule.getRuntimeService().startProcessInstanceByKey("job-worker-process");
    TaskService taskService = processEngineRule.getTaskService();
    Task task =
        taskService.createTaskQuery().processInstanceId(process.getId()).singleResult();

    assertNotNull(task);
    taskService.complete(task.getId());
    assertNull(
        processEngineRule
            .getRuntimeService()
            .createProcessInstanceQuery()
            .processInstanceId(process.getId())
            .singleResult());
  }
}
