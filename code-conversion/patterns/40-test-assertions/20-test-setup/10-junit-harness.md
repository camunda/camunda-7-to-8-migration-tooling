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

## Harness and lifecycle mappings

| Camunda 7 | Camunda Process Test | Note |
|---|---|---|
| `@Rule ProcessEngineRule`, `@ClassRule`, or `ProcessEngineRule("custom.cfg.xml")` | `@CamundaProcessTest` with injected `CamundaClient` and `CamundaProcessTestContext` fields | Configure the CPT runtime in `camunda-container-runtime.properties`. |
| `@ExtendWith(ProcessEngineExtension.class)` or `@RegisterExtension ProcessEngineExtension` | `@CamundaProcessTest` with the same fields | |
| `extends ProcessEngineTestCase` | JUnit 5 class with `@CamundaProcessTest` | Add `@Test` to each `testXxx()` method. Map overridden `setUp()` and `tearDown()` to `@BeforeEach` and `@AfterEach`, and remove calls to `super`. |
| `extends AbstractProcessEngineRuleTest` or `new StandaloneInMemoryTestConfiguration().rule()` | `@CamundaProcessTest` | These helpers start a standalone engine with `MockExpressionManager` and no Spring context. |
| JUnit 4 `@Before`, `@After`, `@Ignore`, `@Test(expected = ...)`, and `org.junit.Assert` | JUnit 5 `@BeforeEach`, `@AfterEach`, `@Disabled`, `assertThrows`, and JUnit 5 `Assertions` or AssertJ | |
| `@RunWith(SpringJUnit4ClassRunner.class)` or `@RunWith(SpringRunner.class)` in a Spring test without Spring Boot | `@ExtendWith(SpringExtension.class)` without `@RunWith` | Keep `@ContextConfiguration` when the test needs Spring-managed beans. |

## Camunda 8

```java
@CamundaProcessTest
class OrderProcessTest {
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void deployConvertedModel() {
    client.newDeployResourceCommand()
        .addResourceFromClasspath("converted-c8-order.bpmn")
        .send().join();
  }

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
