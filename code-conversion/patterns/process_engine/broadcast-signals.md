# Broadcast Signals

The following patterns focus on methods how to broadcast signals in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public void broadcastSignal(VariableMap variableMap) {
        engine.getRuntimeService().signalEventReceived("signal name", "executionId", variableMap);
    }
```

-   programmatically tells an execution that a signal event has been received

```java
    public void broadcastSignalViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createSignalEvent("signal name")
            .tenantId("tenantId")
            .setVariables(variableMap)
            .send();
    }
```

-   tenantId only possible via builder pattern

## CamundaClient (Camunda 8)

```java
    public void broadcastSignal(Map<String, Object> variableMap) {
        camundaClient.newBroadcastSignalCommand()
            .signalName("message name")
            .tenantId("tenantId")
            .variables(variableMap)
            .send();
    }
```

-   a signal is broadcasted to all subscriptions
