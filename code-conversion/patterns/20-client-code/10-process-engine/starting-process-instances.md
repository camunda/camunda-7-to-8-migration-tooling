# Starting Process Instances

The following patterns focus on various methods to start process instances in Camunda 7 and how they convert to Camunda 8.

## Parameter Mappings

| Description                                     | Camunda 7            | Camunda 8                           |
| ----------------------------------------------- | -------------------- | ----------------------------------- |
| The BPMN Model Identifier maintained in the xml | processDefinitionKey | processDefinitionId (bpmnProcessId) |
| A unique key returned on deployment             | processDefinitionId  | processDefinitionKey                |

## By BPMN Model Identifier (latest version)

### ProcessEngine (Camunda 7)

```java
    public ProcessInstance startProcessByBPMNModelIdentifier(String processDefinitionKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, variableMap);
    }
```

```java
    public ProcessInstance startProcessByBPMNModelIdentifierViaBuilder(String processInstanceKey, String businessKey, String tenantId, VariableMap variableMap) {
        return engine.getRuntimeService().createProcessInstanceByKey(processInstanceKey)
                .businessKey(businessKey)
                .processDefinitionTenantId(tenantId)
                .setVariables(variableMap)
                .execute();
    }
```

-   tenantId only possible via builder pattern
-   also possible to execute with variables in return (for synchronous part of process instance)

### CamundaClient (Camunda 8)

```java
    public ProcessInstanceEvent startProcessByBPMNModelIdentifier(String processDefinitionId, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion()
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   no business key in Camunda 8.8
-   _.join()_ can be specified with a timeout to wait for the process instance to complete

## By Key Assigned on Deployment (specific version)

### ProcessEngine (Camunda 7)

```java
    public ProcessInstance startProcessByKeyAssignedOnDeployment(String processDefinitionId, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceById(processDefinitionId, businessKey, variableMap);
    }
```

```java
    public ProcessInstance startProcessByKeyAssignedOnDeploymentViaBuilder(String processDefinitionId, String businessKey, String tenantId, VariableMap variableMap) {
        return engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                .businessKey(businessKey)
                .processDefinitionTenantId(tenantId)
                .setVariables(variableMap)
                .execute();
    }
```

-   tenantId only possible via builder pattern
-   also possible to execute with variables in return (for synchronous part of process instance)

### CamundaClient (Camunda 8)

```java
    public ProcessInstanceEvent startProcessByKeyAssignedOnDeployment(Long processDefinitionKey, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCreateInstanceCommand()
                .processDefinitionKey(processDefinitionKey)
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   no business key in Camunda 8.8
-   _.join()_ can be specified with a timeout to wait for the process instance to complete

## By Message (And ProcessDefinitionId)

### ProcessEngine (Camunda 7)

```java
    public ProcessInstance startProcessByMessage(String messageName, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByMessage(messageName, businessKey, variableMap);
    }
```

```java
    public ProcessInstance startProcessByMessageAndProcessDefinitionId(String messageName, String processDefinitionId, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId(messageName, processDefinitionId, businessKey, variableMap);
    }
```

### CamundaClient (Camunda 8)

```java
    public CorrelateMessageResponse startProcessByMessage(String messageName, String correlationKey, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   no specific method to start a process instance by message
-   no method to target a specific process definition
-   if the message is received by a message start event of a deployed process definition (latest version), a process instance is created
-   for more information, see [the docs on messages](https://docs.camunda.io/docs/next/components/concepts/messages/#message-correlation-overview)
-   no business key in Camunda 8.8
-   it is also possible to publish a message with a time to live
