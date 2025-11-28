package org.camunda.migration.rewrite.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.client.MigrateUserTaskMethodsRecipe;
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

import java.time.Instant;
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
        Date dueDate = Date.from(Instant.parse((firstTask.getDueDate())));
    }
}
"""));
  }
}
