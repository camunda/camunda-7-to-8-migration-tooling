/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.standalone;

import static org.junit.Assert.assertNull;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

public class StandaloneTaskTest {

  @Rule public ProcessEngineRule processEngineRule = new ProcessEngineRule();

  @Test
  public void completesStandaloneTaskWithoutProcessInstance() {
    TaskService taskService = processEngineRule.getProcessEngine().getTaskService();
    Task task = taskService.newTask();

    assertNull(task.getProcessInstanceId());
    taskService.saveTask(task);
    taskService.complete(task.getId());
  }
}
