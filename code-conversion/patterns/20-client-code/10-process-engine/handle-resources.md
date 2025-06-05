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
@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
public class ProcessPaymentsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProcessPaymentsApplication.class, args);
	}
}
```

-   the annotation `@Deployment` can be used to specify specific files or multiple resources via a wildcard pattern to be deployed to the engine
-   for more information, see [the docs](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/getting-started/#deploy-process-models)

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
