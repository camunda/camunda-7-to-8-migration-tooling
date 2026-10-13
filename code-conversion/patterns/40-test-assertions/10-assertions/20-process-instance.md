# Process Instance Assertions

Camunda Process Test (CPT) supports these assertions from Camunda 8.8. Most assertions wait for the expected state for up to 10 seconds by default. `hasNotActivatedElements(...)` is an exception: it evaluates immediately and does not wait. Use it only after a waiting assertion has established the process state where the absence is meaningful. Set a different timeout with `CamundaAssert.setAssertionTimeout(...)`. For plain CPT, set `assertion.timeout` in `camunda-container-runtime.properties` on the test classpath; in Spring, set `camunda.process-test.assertion.timeout`.

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

[List of supported assertions](https://docs.camunda.io/docs/apis-tools/testing/assertions/).

## Process instance API mappings

| Camunda 7 | Camunda 8 | Note |
|---|---|---|
| `runtimeService().startProcessInstanceByKey(key, vars)` | `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Returns a `ProcessInstanceEvent`. Apply the business-key pattern when the test sets a business key. |
| `historyService` or `runtimeService` queries used as assertions | CPT assertions or client search requests | CPT assertions wait for asynchronous behavior. Client search requests are eventually consistent. |

## Negative assertions

Use `hasNoActiveElements("A")` to map `isNotWaitingAt("A")`. It checks the current process state.

Do not use `hasNotActivatedElements("A")` for this mapping.

`hasNoActiveElements("A")` and `hasNotActivatedElements("A")` inspect the current process state. When absence is meaningful only after a later process step, first use a waiting assertion to establish that observation point. `hasNotActivatedElements("A")` is stricter than Camunda 7 `hasNotPassed("A")` and also fails when element A is active. Use it only when that stricter behavior is intended.

```java
assertThat(processInstance).hasActiveElements("ObservationPoint");
assertThat(processInstance).hasNoActiveElements("A");
assertThat(processInstance).hasNotActivatedElements("B");
```