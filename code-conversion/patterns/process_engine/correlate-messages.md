# Correlate Messages

The following patterns focus on methods how to correlate messages in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public void correlateMessage(String messageName, String businessKey, VariableMap variableMap) {
        engine.getRuntimeService().correlateMessage(messageName, businessKey, variableMap);
    }
```

-   correlates to a message subscription of a running process instance with given business key
-   can start new process instance with message start event

```java
    public void correlateMessageToOneExecution(String messageName, String executionId, VariableMap variableMap) {
        engine.getRuntimeService().messageEventReceived(messageName, executionId, variableMap);
    }
```

-   programmatically tells an execution that a message event has been received

```java
    public void correlateMessageViaBuilder(String messageName, String businessKey, String tenantId, VariableMap variableMap) {
        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .correlate();
    }
```

-   tenantId only possible via sync builder pattern
-   various _correlate...()_ methods possible to achieve different outcomes

```java
    public Batch correlateMessagesViaBuilderAsync(String messageName, List<String> processInstanceId, VariableMap variableMap) {
        return engine.getRuntimeService().createMessageCorrelationAsync(messageName)
                .processInstanceIds(processInstanceId)
                .setVariables(variableMap)
                .correlateAllAsync();
    }
```

-   correlates multiple messages asynchronously, various queries possible

## CamundaClient (Camunda 8)

```java
    public CorrelateMessageResponse correlateMessage(String messageName, String correlationKey, Map<String, Object> variableMap) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

```java
    public PublishMessageResponse publishMessage(String messageName, String correlationKey, String messageId, Map<String, Object> variableMap) {
        return camundaClient.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .messageId(messageId)
                .timeToLive(Duration.ofSeconds(30000L))
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   no business key in Camunda 8.8
-   when correlating a message, the message is not buffered
-   a published message can be buffered by specifying a time to live
-   the messageId can be used to differentiate between different buffered message
-   messages are correlated once to a process based on BPMN process id (processDefinitionId), but can be correlated to different processes
-   for more information, see [the docs](https://docs.camunda.io/docs/next/components/concepts/messages)
