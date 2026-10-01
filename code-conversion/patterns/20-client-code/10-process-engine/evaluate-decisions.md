# Evaluate Decisions (DMN)

In Camunda 7, DMN decisions are evaluated via the `DecisionService`. In Camunda 8, use the `newEvaluateDecisionCommand` of the `CamundaClient` (available since 8.6 via the REST API).

## Evaluate a Decision by Id

### ProcessEngine (Camunda 7)

```java
    public DmnDecisionTableResult evaluateDecision(String decisionDefinitionKey, VariableMap variableMap) {
        return engine.getDecisionService().evaluateDecisionTableByKey(decisionDefinitionKey, variableMap);
    }
```

-   the fluent variant (`evaluateDecisionByKey().variables(...).evaluate()`) returns a `DmnDecisionResult` rather than `DmnDecisionTableResult`, but both are evaluated the same way in C8

### CamundaClient (Camunda 8)

```java
    public EvaluateDecisionResponse evaluateDecisionByDMNModelIdentifier(String decisionDefinitionId, Map<String, Object> variableMap) {
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

`Map.of` rejects null values. If the C7 `VariableMap` can contain nulls, preserve every input key and value in a mutable map:

```java
import java.util.HashMap;
import java.util.Map;

Map<String, Object> variableMap = new HashMap<>();
variableMap.put("timezone", timezone);
variableMap.put("sla", sla);
variableMap.put("tier", tier);
variableMap.put("account", account);
```

Preserve any source guard that skips evaluation when a required input is absent. Do not assume that an explicit null and an omitted variable have the same DMN meaning. Verify that behavior with the target decision. If you cannot match the C7 null behavior, flag the input for user review instead of omitting it or adding a default.

-   naming follows the same swap as process definitions: the C7 *decision definition key* (the id in the DMN XML) is the C8 `decisionId`; the C8 `decisionKey` is the unique key assigned on deployment
-   using `decisionId` evaluates the latest deployed version
-   `response.getDecisionOutput()` is a JSON-encoded string; parse it using the output shape of the target decision
-   when the target decision returns an empty object or array for no matching rules, return `null` and keep validating non-empty outputs against the expected shape
-   `response.getEvaluatedDecisions()` contains details of all evaluated (required) decisions
-   `DmnDecisionTableResult` convenience methods like `getSingleEntry()` have no direct equivalent — parse the JSON output instead
-   test a missing required input, each nullable input, the complete input set, matching and nonmatching rules, and the expected return value against the target Camunda version
-   assert the actual JSON `decisionOutput` and `evaluatedDecisions` response in a target-version test
-   decisions evaluated *inside* a process should be modeled as a BPMN business rule task instead of being evaluated from glue code; the task's binding (`latest`, `deployment`, `versionTag`) controls version selection
