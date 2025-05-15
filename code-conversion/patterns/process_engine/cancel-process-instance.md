# Cancel Process Instance

The following patterns focus on methods how to cancel process instances in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public void cancelProcessInstance() {
        engine.getRuntimeService().deleteProcessInstance("processInstanceId", "deleteReason");
    }
```

```java
    public void cancelProcessInstances(List<String> processInstanceIds) {
        engine.getRuntimeService().deleteProcessInstances(processInstanceIds, "deleteReason", true, true);
    }
```

```java
    public void cancelProcessInstancesAsync(List<String> processInstanceIds) {
        engine.getRuntimeService().deleteProcessInstancesAsync(processInstanceIds, "deleteReason");
    }
```

-   multiple process instances can be canceled, sync or async
-   when cancelling process instances, it can be specified if custom listeners and i/o mapping should be skipped
-   cancelling processes asynchronously allows use of queries

## CamundaClient (Camunda 8)

```java
	public void cancelProcessInstance() {
        camundaClient.newCancelInstanceCommand(2391324L).send();
    }
```

-   only one instance can be cancelled at a time
-   process instance is cancelled via _processInstanceKey_
