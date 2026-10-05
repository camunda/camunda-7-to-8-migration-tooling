# Process Instance Assertions

Camunda Process Test (CPT) supports these assertions from Camunda 8.8. Most assertions wait for the expected state for up to 10 seconds by default. `hasNotActivatedElements(...)` is an exception: it evaluates immediately and does not wait. Use it only after a waiting assertion has established the process state where the absence is meaningful. Set a different timeout with `CamundaAssert.setAssertionTimeout(...)` or, in Spring, `camunda.process-test.assertion.timeout`.

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

Camunda 8 uses [Camunda Process Test (CPT)](https://docs.camunda.io/docs/apis-tools/testing/getting-started/) to check the state of a process instance. There are fewer utility methods than in Camunda 7, so tests use the client and test context.

In test cases you typically want blocking behavior for the client API, so use `send().join()`:

Deploy the converted model before starting an instance. See the [test deployment pattern](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/40-test-assertions/20-test-setup/20-deployment.md) for Camunda 8.8 and 8.9 setup.

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

## Negative assertions

Use `hasNoActiveElements("A")` to map `isNotWaitingAt("A")`. It checks the current process state.

Do not use `hasNotActivatedElements("A")` for this mapping.

`hasNotActivatedElements("A")` does not wait, so first use a waiting assertion to establish the observation point. It is stricter than Camunda 7 `hasNotPassed("A")` and also fails when element A is active. Use it only when that stricter behavior is intended.

```java
assertThat(processInstance).hasNoActiveElements("A");
assertThat(processInstance).hasActiveElements("ObservationPoint");
assertThat(processInstance).hasNotActivatedElements("B");
```