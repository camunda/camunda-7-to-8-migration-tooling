# Handle User Tasks

The following patterns focus on methods how to handle user tasks in Camunda 7 and how they convert to Camunda 8.

## Search User Task by BPMN Model Identifier

### ProcessEngine (Camunda 7)

```java
    public List<Task> searchUserTasksByBPMNModelIdentifier(String processDefinitionId) {
        return engine.getTaskService().createTaskQuery()
                .processDefinitionId(processDefinitionId)
                .list();
    }
```

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

## Claim/Assign User Task

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

## Get Variable from User Task

### ProcessEngine (Camunda 7)

```java
    public Object getVariableFromTaskJavaObjectAPI(String taskId, String variableName) {
        return engine.getTaskService().getVariable(taskId, variableName);
    }
```

```java
    public TypedValue getVariableFromTaskTypedValueApi(String taskId, String variableName) {
        return engine.getTaskService().getVariableTyped(taskId, variableName);
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
