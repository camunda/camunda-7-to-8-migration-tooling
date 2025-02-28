# Client API Usage (Your solution calling Camunda)

Your code might use Camunda API to create process instances, work with human tasks, or deploy process definitions. How to refactor this code is part of this section.

* [Process Engine (Spring) &#8594; Zeebe Client (Spring)](#process-engine-spring--zeebe-client-spring)

## Process Engine (Spring) &#8594; Zeebe Client (Spring)

The ProcessEngine is the main proxy to get the various services from Camunda (mainly RuntimeService, TaskService and HistoryService). This needs to be replaced with the ZeebeClient API of Camunda 8.

### Before (Camunda 7)

```java
@Autowired
private ProcessEngine camunda;

public void startProcess(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    camunda.getRuntimeService().startProcessInstanceByKey("order", variables);
}
```

### After (Camunda 8)

```java
@Autowired
private ZeebeClient zeebeClient;

public void startProcess(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    zeebeClient.newCreateInstanceCommand()
        .bpmnProcessId("order")
        .latestVersion()
        .variables(variables)
        .send()
        .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
}
```

### Remarks

- ProcessEngine reference is changed to ZeebeClient
- startProcessInstanceByKey is changed to createInstanceCommand

### OpenRewrite recipe 

- [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/client/ProcessEngineToZeebeClient.java)
- [Learn how to apply recipes](../recipes/)
