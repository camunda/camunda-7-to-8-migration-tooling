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
	public void startProcessByBPMNModelIdentifier(VariableMap variableMap) {
		engine.getRuntimeService().startProcessInstanceByKey("orderProcessIdentifier", variableMap);
	}
```

```java
    public void startProcessByBPMNModelIdentifierViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createProcessInstanceByKey("order")
                .businessKey("some business key")
                .processDefinitionTenantId("some tenantId")
                .setVariables(variableMap)
                .execute();
    }
```

-   tenantId only possible via builder pattern
-   also possible to execute with variables in return (for synchronous part of process instance)

### CamundaClient (Camunda 8)

```java
	public void startProcessByBPMNModelIdentifier(Map<String, Object> variableMap) {
		camundaClient.newCreateInstanceCommand()
			.bpmnProcessId("orderProcessIdentifier")
			.latestVersion()
			.variables(variableMap)
			.send()
			.join(); // add reactive response and error handling instead of join()
	}
```

-   no business key in Camunda 8.8
-   _.join()_ can be specified with a timeout to wait for the process instance to complete

## By Key Assigned on Deployment (specific version)

### ProcessEngine (Camunda 7)

```java
	public void startProcessByKeyAssignedOnDeployment(VariableMap variableMap) {
		engine.getRuntimeService().startProcessInstanceById("order:7:444f-fkd2-dyaf", "some business key", variableMap);
	}
```

```java
	public void startProcessByKeyAssignedOnDeploymentViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createProcessInstanceById("order:7:444f-fkd2-dyaf")
            .businessKey("some business key")
            .processDefinitionTenantId("some tenantId")
            .setVariables(variableMap)
            .execute();
    }
```

-   tenantId only possible via builder pattern
-   also possible to execute with variables in return (for synchronous part of process instance)

### CamundaClient (Camunda 8)

```java
	public void startProcessByKeyAssignedOnDeployment(Map<String, Object> variableMap) {
		camundaClient.newCreateInstanceCommand()
			.processDefinitionKey(21653461L)
			.variables(variableMap)
			.send()
			.join(); // add reactive response and error handling instead of join()
}
```

-   no business key in Camunda 8.8
-   _.join()_ can be specified with a timeout to wait for the process instance to complete

## By Message (And ProcessDefinitionId)

### ProcessEngine (Camunda 7)

```java
	public void startProcessByMessage(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByMessage("message name", "some business key", variableMap);
    }
```

```java
	public void startProcessByMessageAndProcessDefinitionId(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId("message name", "processDefinitionId", "some business key", variableMap);
    }
```

### CamundaClient (Camunda 8)

```java
	public void startProcessByMessage(Map<String, Object> variableMap) {
    	camundaClient.newCorrelateMessageCommand()
            .messageName("message name")
            .correlationKey("some correlation key")
            .variables(variableMap)
            .send()
            .join();
  }
```

-   no specific method to start a process instance by message
-   no method to target a specific process definition
-   if the message is received by a message start event of a deployed process definition (latest version), a process instance is created
-   for more information, see [the docs on messages](https://docs.camunda.io/docs/next/components/concepts/messages/#message-correlation-overview)
-   no business key in Camunda 8.8
-   it is also possible to publish a message with a time to live
