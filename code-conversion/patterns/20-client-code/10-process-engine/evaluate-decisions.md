# Evaluate Decisions (DMN)

In Camunda 7, DMN decisions are evaluated via the `DecisionService`. In Camunda 8, use the `newEvaluateDecisionCommand` of the `CamundaClient` (available since 8.6 via the REST API).

## Evaluate a Decision by Id

### ProcessEngine (Camunda 7)

```java
    public DmnDecisionTableResult evaluateDecisionByDMNModelIdentifier(String decisionDefinitionKey, VariableMap variableMap) {
        return engine.getDecisionService().evaluateDecisionTableByKey(decisionDefinitionKey, variableMap);
    }
```

```java
    public DmnDecisionResult evaluateDecision(String decisionDefinitionKey, VariableMap variableMap) {
        return engine.getDecisionService().evaluateDecisionByKey(decisionDefinitionKey)
                .variables(variableMap)
                .evaluate();
    }
```

### CamundaClient (Camunda 8)

```java
    public EvaluateDecisionResponse evaluateDecisionByBPMNModelIdentifier(String decisionDefinitionId, Map<String, Object> variableMap) {
        return camundaClient.newEvaluateDecisionCommand()
                .decisionId(decisionDefinitionId)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

```java
    public EvaluateDecisionResponse evaluateDecisionByKeyAssignedOnDeployment(long decisionDefinitionKey, Map<String, Object> variableMap) {
        return camundaClient.newEvaluateDecisionCommand()
                .decisionKey(decisionDefinitionKey)
                .variables(variableMap)
                .send()
                .join();
    }
```

-   naming follows the same swap as process definitions: the C7 *decision definition key* (the id in the DMN XML) is the C8 `decisionId`; the C8 `decisionKey` is the unique key assigned on deployment
-   using `decisionId` evaluates the latest deployed version
-   the result is returned as JSON: `response.getDecisionOutput()` contains the output, `response.getEvaluatedDecisions()` the details of all evaluated (required) decisions
-   `DmnDecisionTableResult` convenience methods like `getSingleEntry()` have no direct equivalent — parse the JSON output instead
-   decisions evaluated *inside* a process should be modeled as a BPMN business rule task instead of being evaluated from glue code; the task's binding (`latest`, `deployment`, `versionTag`) controls version selection
