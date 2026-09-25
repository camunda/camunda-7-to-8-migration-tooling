# Search Process Instances

Camunda 7 runtime process-instance queries select running instances and can filter process variables. Keep those filters when migrating the query.

## Camunda 7

```java
public ProcessInstance findSingleActiveByVariable(
        String processDefinitionKey, String variableName, Object variableValue) {
    return engine.getRuntimeService()
            .createProcessInstanceQuery()
            .processDefinitionKey(processDefinitionKey)
            .variableValueEquals(variableName, variableValue)
            .active()
            .singleResult();
}
```

## Camunda 8.9+ (manual migration for variable filters)

The Orchestration Cluster REST API's `POST /v2/process-instances/search` contract does not define a variable field in `ProcessInstanceFilter`. Do not emit `.variables(...)` on `newProcessInstanceSearchRequest()`; the client builder may compile, but the server cannot enforce that predicate. The query migration recipe leaves chains containing `variableValueEquals(...)` unchanged, including `list()`, `count()`, and `singleResult()` queries. This also avoids partially converting a query with multiple variable predicates.

For supported `list()` and `count()` conversions, the recipe adds `state(ACTIVE)` only when the Camunda 7 query called `.active()`.

For a manual migration, use `POST /v2/variables/search` to find candidate variables by name and JSON-serialized value, then use their process-instance keys to apply the remaining process-definition and active-state filters. Preserve pagination and account for the variable search endpoint's scope and consistency semantics. Do not drop the variable predicate when migrating the other filters.
