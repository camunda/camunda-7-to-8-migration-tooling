# Correlate Messages

The following patterns focus on methods how to correlate messages in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
	public void correlateMessage(VariableMap variableMap) {
        engine.getRuntimeService().correlateMessage("message name", "a business key", variableMap);
    }
```

```java
    public void messageEventReceived(VariableMap variableMap) {
        engine.getRuntimeService().messageEventReceived("message name", "execution id", variableMap);
    }
```

```java
    public void correlateMessageViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createMessageCorrelation("message name")
            .processInstanceBusinessKey("a business key")
            .tenantId("tenantId")
            .setVariables(variableMap)
            .correlate();
    }
```

```java
    public void correlateMessagesViaBuilderAsync(List<String> processInstanceId, VariableMap variableMap) {
        engine.getRuntimeService().createMessageCorrelationAsync("message name")
            .processInstanceIds(processInstanceId)
            .setVariables(variableMap)
            .correlateAllAsync();
    }
```

-   tenantId only possible via sync builder pattern
-   sync builder: various _correlate..()_ methods possible to achieve different outcomes
-   asnyc builer: correlates multiple messages asynchronously, various queries possible

## CamundaClient (Camunda 8)

```java
    public void correlateMessage(Map<String, Object> variableMap) {
        camundaClient.newCorrelateMessageCommand()
            .messageName("message name")
            .correlationKey("some correlation key")
            .variables(variableMap)
            .send();
    }
```

```java
    public void publishMessage(Map<String, Object> variableMap) {
        camundaClient.newPublishMessageCommand()
            .messageName("message name")
            .correlationKey("a correlation key")
            .messageId("some messageId")
            .timeToLive(Duration.ofSeconds(30000L))
            .variables(variableMap)
            .send();
    }
```

-   no business key in Camunda 8.8
-   when correlating a message, the message is not buffered
-   a published message can be buffered by specifying a time to live
-   the messageId can be used to differentiate between different buffered message
-   messages are correlated once to a process based on BPMN process id (processDefinitionId), but can correlated to different processes
-   for more information, see [the docs](https://docs.camunda.io/docs/next/components/concepts/messages)
