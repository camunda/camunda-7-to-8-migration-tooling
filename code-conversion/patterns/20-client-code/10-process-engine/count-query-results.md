# Count Query Results

Camunda 7 query results can be counted with `list().size()`, `list().stream().count()`, or `count()`.
The first two forms count the complete in-memory list returned by the engine.

## Camunda 7

```java
long runningInstances = engine.getRuntimeService()
        .createProcessInstanceQuery()
        .processDefinitionKey("order-process")
        .list()
        .stream()
        .count();
```

## Camunda 8

```java
import io.camunda.client.api.search.enums.ProcessInstanceState;

long runningInstances = camundaClient.newProcessInstanceSearchRequest()
        .filter(filter -> filter
                .processDefinitionId("order-process")
                .state(ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .page()
        .totalItems();
```

Use `page().totalItems()` when the result drives a count, guard, or business decision.
Use `page().totalItems().intValue()` when the original `list().size()` result type is `int` or
`Integer`.
Do not use `items().size()` or `items().stream().count()` for a complete result count.
The `items()` list contains only the current page and can be limited by the configured page size.
Review `page().hasMoreTotalItems()` when the search can exceed cluster result limits.
