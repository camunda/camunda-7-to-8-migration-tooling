# Process Test Coverage

Camunda 7 coverage extensions add a rule or JUnit extension to process tests. CPT generates its own coverage report from Camunda 8.8.

## Camunda 7

```java
@ExtendWith(ProcessEngineCoverageExtension.class)
public class OrderProcessTest {
  @Test
  public void startsAnOrder() {
    runtimeService().startProcessInstanceByKey("order");
  }
}
```

## Camunda 8

```java
@CamundaProcessTest
class OrderProcessTest {
  @Test
  void startsAnOrder() {
    client.newCreateInstanceCommand()
        .bpmnProcessId("order").latestVersion().send().join();
  }
}
```

Remove the Camunda 7 `camunda-process-test-coverage` rule, extension, and dependency when no other tests use them. CPT writes its HTML and JSON report to `target/coverage-report`. Do not add a separate coverage dependency.

[CPT coverage report](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
