# Cancel Process Instance

The following patterns focus on methods how to cancel process instances in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public void cancelProcessInstance(String processInstanceId, String deleteReason) {
        engine.getRuntimeService().deleteProcessInstance(processInstanceId, deleteReason);
    }
```

```java
    public void cancelProcessInstances(List<String> processInstanceIds, String deleteReason, boolean skipCustomListeners, boolean externallyTerminated) {
        engine.getRuntimeService().deleteProcessInstances(processInstanceIds, deleteReason, skipCustomListeners, externallyTerminated);
    }
```

```java
    public Batch cancelProcessInstancesAsync(List<String> processInstanceIds, String deleteReason) {
        return engine.getRuntimeService().deleteProcessInstancesAsync(processInstanceIds, deleteReason);
    }
```

-   multiple process instances can be canceled, sync or async
-   when cancelling process instances, it can be specified if custom listeners and i/o mapping should be skipped
-   cancelling processes asynchronously allows use of queries

## CamundaClient (Camunda 8)

```java
    public CancelProcessInstanceResponse cancelProcessInstance(Long processInstanceKey) {
        return camundaClient.newCancelInstanceCommand(processInstanceKey)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   only one instance can be cancelled at a time
-   process instance is cancelled via _processInstanceKey_
