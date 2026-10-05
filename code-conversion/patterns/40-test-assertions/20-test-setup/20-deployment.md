# Test Deployment

Camunda 7 tests deploy models through `@Deployment` or `RepositoryService`. CPT's `@TestDeployment` is available from Camunda 8.9. Use converted copies, not the original Camunda 7 models.

## Camunda 7

```java
@Deployment(resources = {"order.bpmn", "order.dmn"})
public class OrderProcessTest {
  @Test
  public void startsAnOrder() {
    runtimeService().startProcessInstanceByKey("order");
  }
}
```

`@Deployment` without `resources` implicitly deploys a model named after the test class or method. `repositoryService.createDeployment().addClasspathResource(...)` also deploys a classpath model.

## Camunda 8

```java
@CamundaProcessTest
@TestDeployment(resources = {"converted-c8-order.bpmn", "converted-c8-order.dmn"})
class OrderProcessTest {
  @Test
  void startsAnOrder() {
    client.newCreateInstanceCommand()
        .bpmnProcessId("order").latestVersion().send().join();
  }
}
```

On Camunda 8.8, use the CPT client to deploy the converted copy in `@BeforeEach` because `@TestDeployment` is not available:

```java
@BeforeEach
void deployConvertedModel() {
  client.newDeployResourceCommand()
      .addResourceFromClasspath("converted-c8-order.bpmn")
      .send().join();
}
```

Method-level `@TestDeployment` takes precedence over a class-level annotation. Deploy every converted BPMN or DMN copy that the test needs.

[CPT deployment and setup](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
