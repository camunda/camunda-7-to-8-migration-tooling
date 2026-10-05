# Spring Boot Test Setup

Camunda 7 Spring Boot tests run an embedded engine in the application context. CPT 8.8 and later use `@CamundaSpringProcessTest` with the application's Spring Boot test.

## Camunda 7

```java
@RunWith(SpringRunner.class)
@SpringBootTest
public class OrderProcessTest {
  @Autowired RuntimeService runtimeService;
  @MockBean PaymentService paymentService;

  @Test
  public void startsAnOrder() {
    runtimeService.startProcessInstanceByKey("order");
  }
}
```

## Camunda 8

```java
@SpringBootTest
@CamundaSpringProcessTest
class OrderProcessTest {
  @Autowired CamundaClient client;
  @Autowired CamundaProcessTestContext processTestContext;
  @MockitoBean PaymentService paymentService;

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

Use `camunda-process-test-spring` with the Spring Boot 4 starter or `camunda-process-test-spring-boot-3` with the Spring Boot 3 starter. See [dependencies](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/10-general/dependencies.md) for the Camunda 8.8 artifact names.

Use `@MockitoBean` instead of deprecated `@MockBean` with Spring Boot 3.4 or later. Keep the mock at the same boundary as the Camunda 7 test. Keep the real worker enabled when the test mocks only its collaborator. Disable the matching worker with `camunda.client.worker.override.<type>.enabled=false` when the test replaces the worker itself.

For a test that replaces the `notify` worker, disable the real worker and register a CPT worker mock:

```java
@SpringBootTest(properties = "camunda.client.worker.override.notify.enabled=false")
@CamundaSpringProcessTest
class NotifyWorkerTest {
  @Autowired CamundaProcessTestContext processTestContext;

  @Test
  void mocksTheWorker() {
    processTestContext.mockJobWorker("notify").thenComplete();
  }
}
```

[CPT Spring setup](https://docs.camunda.io/docs/apis-tools/testing/getting-started/) · [Disable a job worker](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#disable-a-job-worker)
