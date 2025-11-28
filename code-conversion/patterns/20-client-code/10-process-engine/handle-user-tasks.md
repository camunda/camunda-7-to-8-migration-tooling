# Handle User Tasks

The following patterns focus on handling user tasks in Camunda 7 vs. Camunda 8.

## Searching User Tasks

### ProcessEngine (Camunda 7)

```java
    public List<Task> searchUserTasksByBPMNModelIdentifier(String processDefinitionKey) {
        return engine.getTaskService().createTaskQuery()
                .processDefinitionKey(processDefinitionKey)
                .list();
    }
```

-   the taskQuery can be extended by various information to filter more precisely
-   using the processDefinitionKey gets you all tasks related to a specific process definition

### CamundaClient (Camunda 8)

```java
    public List<UserTask> searchUserTasksByBPMNModelIdentifier(String processDefinitionKey) {
        return camundaClient.newUserTaskSearchRequest()
                .filter(userTaskFilter -> userTaskFilter.bpmnProcessId(processDefinitionKey))
                .send()
                .join()
                .items();
    }
```

-   in place of the taskQuery, various filters can be used

## Claim User Task

### ProcessEngine (Camunda 7)

```java
    public void claimUserTask(String taskId, String userId) {
        engine.getTaskService().claim(taskId, userId);
    }
```

### CamundaClient (Camunda 8)

```java
    public AssignUserTaskResponse claimUserTask(Long userTaskKey, String assignee) {
        return camundaClient.newUserTaskAssignCommand(userTaskKey)
                .assignee(assignee)
                .send()
                .join();
    }
```

## Complete User Task

### ProcessEngine (Camunda 7)

```java
    public void completeUserTask(String taskId, Map<String, Object> variableMap) {
        engine.getTaskService().complete(taskId, variableMap);
    }
```

### CamundaClient (Camunda 8)

```java
    public CompleteUserTaskResponse completeUserTask(Long userTaskKey, Map<String, Object> variableMap) {
        return camundaClient.newUserTaskCompleteCommand(userTaskKey)
                .variables(variableMap)
                .send()
                .join();
    }
```

## Get Variables from Task

### ProcessEngine (Camunda 7)

```java
    public Object getVariableFromTaskJavaObjectAPI(String taskId, String variableName) {
        return engine.getTaskService().getVariable(taskId, variableName);
    }
```

### CamundaClient (Camunda 8)

```java
    public Variable getVariableFromTask(Long userTaskKey, String variableName) {
        return camundaClient.newUserTaskVariableSearchRequest(userTaskKey)
                .filter(userTaskVariableFilter -> userTaskVariableFilter.name(variableName))
                .send()
                .join()
                .items()
                .get(0);
    }
```

-   Variable is a type which carries information about the variable
-   use .getValue() to access its String-value, cast as necessary
