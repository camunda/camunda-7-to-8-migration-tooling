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

```java
    public ProcessInstanceEvent startProcessByBPMNModelIdentifierWithBusinessId(String processDefinitionId, String businessId, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion()
                .businessId(businessId)
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   C7 `businessKey` maps to C8 `businessId` (available since Camunda 8.9) — set via `.businessId()` on the create instance command
-   `businessId` is immutable after creation and propagates to child instances created through call activities
-   uniqueness enforcement is optional and configurable per cluster; when enabled, duplicate businessId for the same process definition is rejected with a conflict error
-   on Camunda 8.8 (no businessId) use tags or a process variable instead — see the [Business Key pattern](business-key-and-tags.md)
-   if you need a bounded wait for the command response, apply a timeout to the returned future (e.g. `send().orTimeout(...).join()` or `send().get(timeout, unit)`); `send()` itself does **not** wait for the process instance to complete
-   if your app also uses `@Deployment`, do not start instances from `@PostConstruct`; use `@EventListener(CamundaPostDeploymentEvent.class)` so startup runs after deployment completes

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

```java
    public ProcessInstanceEvent startProcessByKeyAssignedOnDeploymentWithBusinessId(Long processDefinitionKey, String businessId, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCreateInstanceCommand()
                .processDefinitionKey(processDefinitionKey)
                .businessId(businessId)
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```
-   C7 `businessKey` maps to C8 `businessId` (available since Camunda 8.9) — set via `.businessId()` on the create instance command
-   on Camunda 8.8 (no businessId) use tags or a process variable instead — see the [Business Key pattern](business-key-and-tags.md)
-   if you need a bounded wait for the command response, apply a timeout to the returned future (e.g. `send().orTimeout(...).join()` or `send().get(timeout, unit)`); `send()` itself does **not** wait for the process instance to complete

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
    public CorrelateMessageResponse startProcessByMessage(
            String messageName, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .withoutCorrelationKey()
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   no specific method to start a process instance by message
-   no method to target a specific process definition
-   if the message is received by a message start event of a deployed process definition (latest version), a process instance is created
-   when the C7 `businessKey` is null, use `.withoutCorrelationKey()` to start a new process instance through the matching message start event
-   when the C7 `businessKey` is non-null, mark this mapping for manual redesign
-   for more information, see [the docs on messages](https://docs.camunda.io/docs/next/components/concepts/messages/#message-correlation-overview)
-   C8 message correlation cannot set a `businessId`
-   on Camunda 8.8 (no businessId) use tags or a process variable instead — see the [Business Key pattern](business-key-and-tags.md)
-   it is also possible to publish a message with a time to live
