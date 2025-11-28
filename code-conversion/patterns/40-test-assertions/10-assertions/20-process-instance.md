# Process Instance Assertions

## Camunda 7

Camunda 7 provides fluent assertions via [Camunda Platform Assert](https://github.com/camunda/camunda-bpm-platform/tree/master/test-utils/assert), allowing you to check the current state of a process instance:

```java
@Test
void testProcessInstanceIsWaitingAtUserTask() {
  ProcessInstance processInstance = runtimeService()
    .startProcessInstanceByKey("example-process");

  assertThat(processInstance)
    .isNotEnded()
    .isWaitingAt("UserTask_1");
}
```

## Camunda 8

Camunda 8 uses [Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/) to check the state of a process instance. There are currently less utility methods (like `runtimeService()`) and tests rely on normal Spring behavior plus custom code.

In test cases you typically want blocking behavior for the client API, so use `send().join()`:

```java
@Autowired
CamundaClient client;

@Test
void testProcessInstanceIsWaitingAtUserTask() {
  ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
    .bpmnProcessId("example-process")
    .latestVersion()
    .send().join();

  assertThat(processInstance)
    .isActive()
    .hasActiveElements("UserTask_1");
}
```

[List of supported assertions](https://docs.camunda.io/docs/next/apis-tools/testing/assertions/).