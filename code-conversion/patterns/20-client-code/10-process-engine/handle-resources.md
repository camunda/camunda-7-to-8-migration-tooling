# Handle Resources

The following patterns focus on methods how to handle resources in Camunda 7 and how they convert to Camunda 8.

## Deploy Resources via Annotation

### ProcessEngine (Camunda 7)

```java
@SpringBootApplication
@EnableProcessApplication
public class Application {

    public static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }
}
```

-   the annotation `@EnableProcessApplication` alongside a meta-data description `META-INF/processes.xml` takes care of deploying (and undeploying) the resources
-   for more information, see the docs [here](https://docs.camunda.org/manual/latest/user-guide/spring-boot-integration/process-applications/) and [here](https://docs.camunda.org/manual/latest/user-guide/process-applications/the-processes-xml-deployment-descriptor/)

### CamundaClient (Camunda 8)

```java
import io.camunda.client.annotation.Deployment;

@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/converted-c8-*.bpmn")
public class ProcessPaymentsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProcessPaymentsApplication.class, args);
	}
}
```

Select the annotation import from the chosen starter:

| Camunda target | Spring Boot generation | Selected starter | `@Deployment` Java import |
| --- | --- | --- | --- |
| 8.9.21 | 3.5.x | `io.camunda:camunda-spring-boot-3-starter` | `io.camunda.client.annotation.Deployment` |
| 8.9.21 | 4.0.x or 4.1.x | `io.camunda:camunda-spring-boot-starter` | `io.camunda.client.annotation.Deployment` |

For another target version, inspect its selected starter's classes before choosing the import.
Compile application and test sources before calling the CPT harness ready.
-   the annotation `@Deployment` can be used to specify specific files or multiple resources via a wildcard pattern to be deployed to the engine
-   Derive each pattern from the actual converted copies.
-   Use a separate `resources` array entry for each required resource type.
-   Never join two patterns with a comma inside one entry.
-   Resolve each entry with Spring's `PathMatchingResourcePatternResolver`.
-   Require nonempty converted resources of one type for each entry.
-   Check that every inventoried model matches exactly one entry.
-   Reject matches for original Camunda 7 models.
-   Camunda 8 has no implicit classpath auto-deployment equivalent to the Camunda 7 Spring Boot starter defaults. Even if your Camunda 7 app had no `@EnableProcessApplication`, you must still add explicit deployment wiring (`@Deployment` or deploy commands) for BPMN/DMN resources.
-   for more information, see [the docs](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/getting-started/#deploy-process-models)

## Deploy BPMN Model

### ProcessEngine (Camunda 7)

```java
    public Deployment deployBPMNModel(String tenantId, String bpmnModelName, BpmnModelInstance bpmnModelInstance) {
        return engine.getRepositoryService().createDeployment()
                .tenantId(tenantId)
                .addModelInstance(bpmnModelName, bpmnModelInstance)
                .deploy();
    }
```

### CamundaClient (Camunda 8)

```java
    public DeploymentEvent deployBPMNModel(String tenantId, String bpmnModelName, BpmnModelInstance bpmnModelInstance) {
        return camundaClient.newDeployResourceCommand()
                .addProcessModel(bpmnModelInstance, bpmnModelName)
                .send()
                .join();
    }
```

## Deploy Multiple Resources by Filename

### ProcessEngine (Camunda 7)

```java
    public Deployment deployMultipleResourcesByFileName(String fileName1, String fileName2) {
        return engine.getRepositoryService().createDeployment()
                .addClasspathResource(fileName1)
                .addClasspathResource(fileName2)
                .deploy();
    }
```

### CamundaClient (Camunda 8)

```java
    public DeploymentEvent deployMultipleResourcesByFileName(String fileName1, String fileName2) {
        return camundaClient.newDeployResourceCommand()
                .addResourceFromClasspath(fileName1)
                .addResourceFromClasspath(fileName2)
                .send()
                .join();
    }
```

## Delete Process Definition

### ProcessEngine (Camunda 7)

```java
    public void deleteProcessDefinition(String processDefinitionId) {
        engine.getRepositoryService().deleteProcessDefinition(processDefinitionId);
    }
```

### CamundaClient (Camunda 8)

```java
    public DeleteResourceResponse deleteProcessDefinition(Long processDefinitionKey) {
        return camundaClient.newDeleteResourceCommand(processDefinitionKey)
                .send()
                .join();
    }
```

## Get Decision Definition

### ProcessEngine (Camunda 7)

```java
    public DecisionDefinition getDecisionDefinition(String decisionDefinitionId) {
        return engine.getRepositoryService().getDecisionDefinition(decisionDefinitionId);
    }
```

### CamundaClient (Camunda 8)

```java
    public DecisionDefinition getDecisionDefinition(Long decisionDefinitionKey) {
        return camundaClient.newDecisionDefinitionGetRequest(decisionDefinitionKey)
                .send()
                .join();
    }
```
