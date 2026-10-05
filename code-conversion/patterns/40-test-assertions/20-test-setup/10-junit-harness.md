# JUnit Harness

Camunda 7 test support starts an embedded process engine. CPT runs tests against a Camunda 8 runtime and supports JUnit 5. The CPT harness APIs in this pattern are available from Camunda 8.8.

## Camunda 7

```java
public class OrderProcessTest {
  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule("camunda.cfg.xml");

  @Test
  public void startsAnOrder() {
    ProcessInstance instance = runtimeService().startProcessInstanceByKey("order");
    assertThat(instance).isNotEnded();
  }
}
```

`ProcessEngineRule`, `@ClassRule`, `ProcessEngineExtension`, `ProcessEngineTestCase`, `AbstractProcessEngineRuleTest`, and `StandaloneInMemoryTestConfiguration` all use the embedded Camunda 7 test engine.

## Camunda 8

```java
@CamundaProcessTest
class OrderProcessTest {
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void startsAnOrder() {
    ProcessInstanceEvent instance = client.newCreateInstanceCommand()
        .bpmnProcessId("order").latestVersion().send().join();

    assertThat(instance).isActive();
  }
}
```

Use `@CamundaProcessTest` with injected `CamundaClient` and `CamundaProcessTestContext` fields. Convert JUnit 3 and JUnit 4 process tests to JUnit 5.

Remove `camunda.cfg.xml` when it configures only the test engine. Ask the user to decide how to handle a plugin, history level, or other setting that changes behavior. Keep JUnit 4 tests that are not process tests and add `junit-vintage-engine` when the module still needs them.

[CPT getting started](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
