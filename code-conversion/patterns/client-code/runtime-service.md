# RuntimeService

For an introduction to code conversion patterns, see the [patterns README](../README.md).

This document discusses code conversion patterns from the Camunda 7 engine's RuntimeService to the Camunda Spring SDK.

The different commands of the RuntimeService are grouped by action, such as starting a process instance, with multiple examples covering different methods of performing the same action.

<details>

<summary>Start Process Instances</summary>

<details>

<summary class="level-2-summary">By Process Identifier</summary>

#### Start Process Instance by Process Identifier (Camunda 7)

```java
@Autowired
private ProcessEngine camunda;

public void startProcess(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
    camunda.getRuntimeService().startProcessInstanceByKey("order", variables);
}
```

#### Diff View between Camunda 7 and Camunda 8

```diff
@Autowired
- private ProcessEngine camunda;
+ private ZeebeClient zeebeClient;

public void startProcess(String orderId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", orderId);
-   camunda.getRuntimeService().startProcessInstanceByKey("order", variables);
+   zeebeClient.newCreateInstanceCommand()
+       .bpmnProcessId("order")
+       .latestVersion()
+       .variables(variables)
+       .send()
+       .join(); // mimic synchronous blocking behavior as this is closest to Camunda 7 logic
}
```

#### Start Process Instance by Process Identifier (Camunda 8)

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

#### OpenRewrite recipe

-   [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/client/ProcessEngineToZeebeClient.java)
-   [Learn how to apply recipes](../recipes/)

</details>

</details>

<style>

.level-2-summary {
    font-size: medium;
    font-weight: 500;
    text-indent: 2rem;
}

summary {
    font-size: large;
    font-weight: 700;
}

</style>
