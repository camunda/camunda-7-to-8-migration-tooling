# RuntimeService

For an introduction to code conversion patterns, see the [patterns README](../README.md).

This document discusses code conversion patterns from the Camunda 7 engine's RuntimeService to the Camunda Spring SDK.

The different commands of the RuntimeService are grouped by action, such as starting a process instance, with multiple examples covering different methods of performing the same action.

## Start Process Instances

<details>

<summary>

### By BPMN Model Identifier (latest version)

</summary>

#### Start Process Instance by BPMN Model Identifier (latest version) (Camunda 7)

```java
@Autowired
private ProcessEngine camunda;

public void startProcess(String orderId) {
    Map<String, Object> variablesMap = new HashMap<>();
    variablesMap.put("orderId", orderId);
    camunda.getRuntimeService().startProcessInstanceByKey("order", variablesMap);
}
```

#### Diff View between Camunda 7 and Camunda 8

```diff
@Autowired
- private ProcessEngine camunda;
+ private ZeebeClient zeebeClient;

public void startProcess(String orderId) {
    Map<String, Object> variablesMap = new HashMap<>();
    variablesMap.put("orderId", orderId);
-   camunda.getRuntimeService().startProcessInstanceByKey("order", variablesMap);
+   zeebeClient.newCreateInstanceCommand()
+       .bpmnProcessId("order")
+       .latestVersion()
+       .variables(variablesMap)
+       .send()
+       .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
}
```

#### Start Process Instance by BPMN Model Identifier (latest version) (Camunda 8)

```java
@Autowired
private ZeebeClient zeebeClient;

public void startProcess(String orderId) {
    Map<String, Object> variablesMap = new HashMap<>();
    variablesMap.put("orderId", orderId);
    zeebeClient.newCreateInstanceCommand()
        .bpmnProcessId("order")
        .latestVersion()
        .variables(variablesMap)
        .send()
        .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
}
```

</details>

<details>

<summary>

### By Key Assigned on Deployment (specific version)

</summary>

#### Start Process Instance by Key Assigned on Deployment (specific version) (Camunda 7)

```java
@Autowired
private ProcessEngine camunda;

public void startProcess(String orderId) {
    Map<String, Object> variablesMap = new HashMap<>();
    variablesMap.put("orderId", orderId);
    camunda.getRuntimeService().startProcessInstanceById("order:7:444f-fkd2-dyaf", variablesMap);
}
```

#### Diff View between Camunda 7 and Camunda 8

```diff
@Autowired
- private ProcessEngine camunda;
+ private ZeebeClient zeebeClient;

public void startProcess(String orderId) {
    Map<String, Object> variablesMap = new HashMap<>();
    variablesMap.put("orderId", orderId);
-   camunda.getRuntimeService().startProcessInstanceByKey("order", variablesMap);
+   zeebeClient.newCreateInstanceCommand()
+           .processDefinitionKey(21653461)
+           .variables(variables)
+           .send()
+           .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
}
```

#### Start Process Instance by Key Assigned on Deployment (specific version) (Camunda 8)

```java
@Autowired
private ZeebeClient zeebeClient;

public void startProcess(String orderId) {
    Map<String, Object> variablesMap = new HashMap<>();
    variablesMap.put("orderId", orderId);
    zeebeClient.newCreateInstanceCommand()
            .processDefinitionKey(21653461)
            .variables(variables)
            .send()
            .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
}
```

</details>

## OpenRewrite recipe (WIP)

-   [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/client/ProcessEngineToZeebeClient.java)
-   [Learn how to apply recipes](../recipes/)
