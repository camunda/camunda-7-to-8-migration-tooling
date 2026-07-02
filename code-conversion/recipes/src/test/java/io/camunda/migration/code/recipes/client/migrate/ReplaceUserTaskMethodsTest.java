/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.MigrateUserTaskMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceUserTaskMethodsTest implements RewriteTest {

  @Test
  void replaceUserTaskMethodsTest() {  // new line after packages vanishes...
    rewriteRun(
        spec -> spec.recipe(new MigrateUserTaskMethodsRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.List;

@Component
public class HandleUserTasksTestClass {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private CamundaClient camundaClient;

    public void handleUserTasks(String processDefinitionKey, String taskId, String userId, Map<String, Object> variableMap, String variableName) {
        List<Task> userTasks = engine.getTaskService().createTaskQuery()
                .processDefinitionKey(processDefinitionKey)
                .list();

        Task firstTask = userTasks.get(0);
        String taskName = firstTask.getName();
        String processInstanceId = firstTask.getProcessInstanceId();
        String tenantId = firstTask.getTenantId();
        String taskId = firstTask.getId();
        String assignee = firstTask.getAssignee();
        Date dueDate = firstTask.getDueDate();
    }
    
    public String getTaskDefinitionKeyByTask(String taskId) {
        return engine.getTaskService().createTaskQuery()
               .taskId(taskId)
               .singleResult()
               .getTaskDefinitionKey();
    }
}
                                """,
"""
package org.camunda.community.migration.example;
import io.camunda.client.api.search.response.UserTask;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.List;

@Component
public class HandleUserTasksTestClass {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private CamundaClient camundaClient;

    public void handleUserTasks(String processDefinitionKey, String taskId, String userId, Map<String, Object> variableMap, String variableName) {
        List<UserTask> userTasks = camundaClient
                .newUserTaskSearchRequest()
                .filter(filter -> filter.bpmnProcessId(processDefinitionKey))
                .send()
                .join()
                .items();

        UserTask firstTask = userTasks.get(0);
        String taskName = firstTask.getName();
        String processInstanceId = String.valueOf(firstTask.getProcessInstanceKey());
        String tenantId = firstTask.getTenantId();
        String taskId = String.valueOf(firstTask.getUserTaskKey());
        String assignee = firstTask.getAssignee();
        Date dueDate = Date.from(firstTask.getDueDate().toInstant());
    }
    
    public String getTaskDefinitionKeyByTask(String taskId) {
        return camundaClient
                .newUserTaskGetRequest(Long.parseLong(taskId))
                .send()
                .join()
                .getElementId();
    }
}
"""));
  }

  @Test
  void replaceClaim() {
    rewriteRun(
        spec -> spec.recipe(new MigrateUserTaskMethodsRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleClaim {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void claimTask(String taskId, String userId) {
        taskService.claim(taskId, userId);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleClaim {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void claimTask(String taskId, String userId) {
        camundaClient
                .newAssignUserTaskCommand(Long.valueOf(taskId))
                .assignee(userId)
                .send()
                .join();
    }
}
"""));
  }

  @Test
  void replaceSetAssignee() {
    rewriteRun(
        spec -> spec.recipe(new MigrateUserTaskMethodsRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleAssign {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void assignTask(String taskId, String userId) {
        taskService.setAssignee(taskId, userId);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleAssign {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void assignTask(String taskId, String userId) {
        // TODO: if the original assignee was null (unclaim), use camundaClient.newUnassignUserTaskCommand(taskKey) instead.
        camundaClient
                .newAssignUserTaskCommand(Long.valueOf(taskId))
                .assignee(userId)
                .send()
                .join();
    }
}
"""));
  }

  @Test
  void replaceComplete() {
    rewriteRun(
        spec -> spec.recipe(new MigrateUserTaskMethodsRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleComplete {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void completeTask(String taskId) {
        taskService.complete(taskId);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleComplete {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void completeTask(String taskId) {
        // TODO: the Camunda user task API requires the BPMN user task element to declare <zeebe:userTask />, otherwise this command fails with a 404. Run the Diagram Converter to add it automatically.
        camundaClient
                .newCompleteUserTaskCommand(Long.valueOf(taskId))
                .send()
                .join();
    }
}
"""));
  }

  @Test
  void replaceCompleteWithVariables() {
    rewriteRun(
        spec -> spec.recipe(new MigrateUserTaskMethodsRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

import java.util.Map;

public class HandleCompleteVariables {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

import java.util.Map;

public class HandleCompleteVariables {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void completeTask(String taskId, Map<String, Object> variables) {
        // TODO: the Camunda user task API requires the BPMN user task element to declare <zeebe:userTask />, otherwise this command fails with a 404. Run the Diagram Converter to add it automatically.
        camundaClient
                .newCompleteUserTaskCommand(Long.valueOf(taskId))
                .variables(variables)
                .send()
                .join();
    }
}
"""));
  }

  @Test
  void replaceSetVariable() {
    rewriteRun(
        spec -> spec.recipe(new MigrateUserTaskMethodsRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleSetVariable {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void setVariable(String taskId, String variableName, Object value) {
        taskService.setVariable(taskId, variableName, value);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.TaskService;

public class HandleSetVariable {

    private CamundaClient camundaClient;
    private TaskService taskService;

    public void setVariable(String taskId, String variableName, Object value) {
        // TODO: Camunda 8 has no task-scoped variables. newSetVariablesCommand expects the element instance key (not the task key); set the variable on the process/element instance scope instead.
        camundaClient
                .newSetVariablesCommand(Long.valueOf(taskId))
                .variables(java.util.Collections.singletonMap(variableName, value))
                .send()
                .join();
    }
}
"""));
  }
}
