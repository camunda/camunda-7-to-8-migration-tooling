# Message Correlation

## Camunda 7

In Camunda 7, you can correlate a message using runtimeService and then assert that the process advanced. You can provide multiple correlationKeys that must match process variables of the process instance.

```java
import java.util.Map;

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

Camunda 8 uses the client API to correlate a message immediately. The `newCorrelateMessageCommand()` and CPT assertion APIs shown here are available from Camunda 8.8. The message subscription uses one string correlation key.

```java
import java.util.Map;

@Test
void testMessageCorrelation() {
  Map<String, Object> variables = Map.of("correlationKey", "some-key");
  ProcessInstanceEvent instance = client.newCreateInstanceCommand()
    .bpmnProcessId("message-process")
    .latestVersion()
    .variables(variables)
    .send().join();

 assertThat(instance)
   .hasActiveElements("MessageCatchEvent");

  client.newCorrelateMessageCommand()
    .messageName("Message_Continue")
    .correlationKey("some-key")
    .send().join();

  // Wait or assert state transition
  assertThat(instance)
    .hasCompletedElements("MessageCatchEvent")
    .isCompleted();
}
```

## Message and signal API mappings

| Camunda 7 | Camunda 8 | Note |
|---|---|---|
| `runtimeService.correlateMessage(name, businessKey, vars)` | `client.newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(vars).send().join()` | Read the correlation-key expression from the converted model's message subscription. Pass its evaluated key, not the business key or expression text. Only map when the model has an explicit subscription correlation key and the matching process variable is initialized before the catch event. Variables sent with this command are applied after correlation and cannot initialize the key. Otherwise, manually redesign the model. |
| `runtimeService.signalEventReceived(name)` | `client.newBroadcastSignalCommand().signalName(name).send().join()` | Use the signal name from the converted model. |

This example assumes that the converted model's message subscription reads the `correlationKey` process variable. Set that variable to the same value passed to `.correlationKey(...)`. Use `newPublishMessageCommand()` when the test needs publication or buffering semantics instead of immediate correlation.
