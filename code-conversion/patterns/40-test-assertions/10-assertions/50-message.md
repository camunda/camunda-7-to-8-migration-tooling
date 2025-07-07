# Message Correlation

## Camunda 7

In Camunda 7, you can correlate a message using runtimeService and then assert that the process advanced. You can provide multiple correlationKeys that must match process variables of the process instance.

```java
@Test
void testMessageCorrelation() {
  ProcessInstance instance = runtimeService()
    .startProcessInstanceByKey("message-process");

  assertThat(instance)
    .isWaitingAt("MessageCatchEvent");
    
  // Correlate message to waiting message event
	Map<String, Object> correlationKeys = //...
	runtimeService().correlateMessage("Message_Continue", correlationKeys);

  assertThat(instance)
    .hasPassed("MessageCatchEvent")
    .isEnded();
}
```

## Camunda 8

Camunda 8 uses the client API to publish a message, and assertions are typically based on observing that the process moved forward. Note that the correlation is based on one single String - the correlationKey.

```java
@Test
void testMessageCorrelation() {
  ProcessInstanceEvent instance = client.newCreateInstanceCommand()
    .bpmnProcessId("message-process")
    .latestVersion()
    .send().join();

 assertThat(instance)
   .hasActiveElements("MessageCatchEvent");

  client.newPublishMessageCommand()
    .messageName("Message_Continue")
    .correlationKey("some-key")
    .send().join();

  // Wait or assert state transition
  assertThat(instance)
    .hasCompletedElements("MessageCatchEvent")
    .isCompleted();
}
```