# Process Variable Assertions

## Camunda 7

Camunda 7 provides a nested variable assertion:

```java
@Test
void testProcessVariable() {
  ProcessInstance processInstance = runtimeService()
    .startProcessInstanceByKey("example-process", Variables.putValue("x", 5));

  assertThat(processInstance)
    .variables()
    .containsEntry("x", 5);
}
```

You can also check variables at the end of the process:

```java
assertThat(processInstance)
  .isEnded()
  .variables()
  .containsEntry("result", "done");
```

## Camunda 8

[Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/) has direct support for assertions on the process instance level:

```java
@Test
void testProcessVariable() {
  Map<String, Object> variables = new HashMap<>();
  variables.put("x", 5);
  
  ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
    .bpmnProcessId("example-process")
    .latestVersion()
    .variables(variables)
    .send().join();

  assertThat(processInstance)
    .hasVariable("x", 5);   
}
```

You can also assert final output variables after completion:
```java
assertThat(processInstance)
  .isCompleted()
  .hasVariable("result", "done");
```
