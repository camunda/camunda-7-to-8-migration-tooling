# Process Test Coverage

Camunda 7 coverage extensions add a rule or JUnit extension to process tests. CPT generates its own coverage report from Camunda 8.8.

| Camunda 7 coverage support | CPT | Note |
|---|---|---|
| `camunda-process-test-coverage` rule or extension | Remove it when no remaining test uses it. | CPT reports process coverage. Do not add a separate coverage dependency. |

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
  private CamundaClient client;

  @BeforeEach
  void deployConvertedModel() {
    client.newDeployResourceCommand()
        .addResourceFromClasspath("converted-c8-order.bpmn")
        .send().join();
  }

  @Test
  void startsAnOrder() {
    ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
        .bpmnProcessId("order").latestVersion().send().join();

    assertThat(processInstance).isCompleted();
  }
}
```

Wait for the expected terminal or wait state before the test returns. CPT collects coverage in its `afterEach` lifecycle step.

Remove the Camunda 7 `camunda-process-test-coverage` rule, extension, and dependency when no other tests use them. CPT writes its HTML and JSON report to `target/coverage-report`. Do not add a separate coverage dependency.

[CPT coverage report](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
