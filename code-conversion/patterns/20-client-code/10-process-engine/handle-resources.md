# Handle Resources

The following patterns focus on methods how to handle resources in Camunda 7 and how they convert to Camunda 8.

## Deploy BPMN Model

### ProcessEngine (Camunda 7)

```java
    public Deployment deployBPMNModel(String tenantId, String bpmnModelName, BpmnModelInstance bpmnModelInstance) {
        return engine.getRepositoryService().createDeployment()
                .tenantId(tenantId)
                .addModelInstance(bpmnModelName, bpmnModelInstance)
                .deploy();
    }
```

### CamundaClient (Camunda 8)

```java
    public DeploymentEvent deployBPMNModel(String tenantId, String bpmnModelName, BpmnModelInstance bpmnModelInstance) {
        return camundaClient.newDeployResourceCommand()
                .addProcessModel(bpmnModelInstance, bpmnModelName)
                .send()
                .join();
    }
```

## Deploy Multiple Resources by Filename

### ProcessEngine (Camunda 7)

```java
    public Deployment deployMultipleResourcesByFileName(String fileName1, String fileName2) {
        return engine.getRepositoryService().createDeployment()
                .addClasspathResource(fileName1)
                .addClasspathResource(fileName2)
                .deploy();
    }
```

### CamundaClient (Camunda 8)

```java
    public DeploymentEvent deployMultipleResourcesByFileName(String fileName1, String fileName2) {
        return camundaClient.newDeployResourceCommand()
                .addResourceFromClasspath(fileName1)
                .addResourceFromClasspath(fileName2)
                .send()
                .join();
    }
```

## Delete Process Definition

### ProcessEngine (Camunda 7)

```java
    public void deleteProcessDefinition(String processDefinitionId) {
        engine.getRepositoryService().deleteProcessDefinition(processDefinitionId);
    }
```

### CamundaClient (Camunda 8)

```java
    public DeleteResourceResponse deleteProcessDefinition(Long processDefinitionKey) {
        return camundaClient.newDeleteResourceCommand(processDefinitionKey)
                .send()
                .join();
    }
```

## Get Decision Definition

### ProcessEngine (Camunda 7)

```java
    public DecisionDefinition getDecisionDefinition(String decisionDefinitionId) {
        return engine.getRepositoryService().getDecisionDefinition(decisionDefinitionId);
    }
```

### CamundaClient (Camunda 8)

```java
    public DecisionDefinition getDecisionDefinition(Long decisionDefinitionKey) {
        return camundaClient.newDecisionDefinitionGetRequest(decisionDefinitionKey)
                .send()
                .join();
    }
```
