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

## Camunda 8.9+

```java
public ProcessInstance findSingleActiveByVariable(
        String processDefinitionId, String variableName, Object variableValue) {
    var response = camundaClient.newProcessInstanceSearchRequest()
            .page(page -> page.limit(1))
            .filter(filter -> filter
                    .processDefinitionId(processDefinitionId)
                    .variables(Collections.singletonMap(variableName, variableValue))
                    .state(ProcessInstanceState.ACTIVE))
            .send()
            .join();

    if (response.page().totalItems() > 1) {
        throw new IllegalStateException("Process-instance query returned more than one result");
    }
    return response.items().stream().findFirst().orElse(null);
}
```

`ProcessInstanceFilter.variables(Map)` filters by the requested variable name and value. A C7 process variable is not automatically a C8 business ID.

`page().totalItems()` counts matches across pages. Do not use `items().size()` to enforce uniqueness because `items()` contains only the current page.

This keeps `singleResult()` behavior: no match returns `null`, one match returns that instance, and multiple matches raise an error. The active-state filter excludes non-running instances.

When a required query filter has no supported C8 equivalent, leave the query for manual migration and report the missing filter instead of dropping it.
