# Broadcast Signals

The following patterns focus on methods how to broadcast signals in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public void broadcastSignalGlobally(String signalName, VariableMap variableMap) {
        engine.getRuntimeService().signalEventReceived(signalName, variableMap);
    }
```

-   signal is correlated to all suitable signal subscriptions

```java
    public void broadcastSignalToOneExecution(String signalName, String executionId, VariableMap variableMap) {
        engine.getRuntimeService().signalEventReceived(signalName, executionId, variableMap);
    }
```

-   signal is correlated to a specific executionId

```java
    public void broadcastSignalGloballyViaBuilder(String signalName, String tenantId, VariableMap variableMap) {
        engine.getRuntimeService().createSignalEvent(signalName)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .send();
    }
```

-   tenantId can only be added via builder

```java
    public void broadcastSignalToOneExecutionViaBuilder(String signalName, String executionId, String tenantId, VariableMap variableMap) {
        engine.getRuntimeService().createSignalEvent(signalName)
                .executionId(executionId)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .send();
    }
```

-   signal is correlated to a specific executionId
-   tenantId can only be added via builder

## CamundaClient (Camunda 8)

```java
    public BroadcastSignalResponse broadcastSignal(String signalName, String tenantId, Map<String, Object> variableMap) {
        return camundaClient.newBroadcastSignalCommand()
                .signalName(signalName)
                .tenantId(tenantId)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
```

-   in Camunda 8, a signal is always correlated to all suitable signal subscriptions
-   to complete a specific signal event in a running process instance without broadcasting a global signal, use the [Modify process instance API](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/modify-process-instance/)
