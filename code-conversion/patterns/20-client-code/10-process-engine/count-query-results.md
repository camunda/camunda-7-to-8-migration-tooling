# Count Query Results

Camunda 7 query results can be counted with `list().size()`, `list().stream().count()`, or `count()`.
The first two forms count the complete in-memory list returned by the engine.

The maintenance/0.2 recipe set does not rewrite process-instance query chains. Apply this mapping
manually and preserve all query filters, runtime state, and result semantics. The Camunda 8 example
below matches only the explicitly `.active()` Camunda 7 query shown; default or suspended queries
need a target-specific mapping.

## Camunda 7

```java
long runningInstances = engine.getRuntimeService()
        .createProcessInstanceQuery()
        .processDefinitionKey("order-process")
        .active()
        .list()
        .stream()
        .count();
```

## Camunda 8

```java
import io.camunda.client.api.search.enums.ProcessInstanceState;
import java.util.Optional;

long runningInstances = Optional.of(camundaClient.newProcessInstanceSearchRequest()
        .filter(filter -> filter
                .processDefinitionId("order-process")
                .state(ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .page())
        .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
        .orElseThrow(() -> new IllegalStateException(
                "Process-instance count exceeds search limit; paginate to count exactly"))
        .totalItems().longValue();
```

`totalItems()` is only an exact count when `hasMoreTotalItems()` is `false`. When it is `true`,
the total is capped and is a lower bound; fail rather than using it for a count, guard, or business
decision. To obtain an exact count in that case, paginate through all matching results.
Use `.intValue()` when the original `list().size()` result type is `int` or `Integer`.
Do not use `items().size()` or `items().stream().count()` for a complete result count.
The `items()` list contains only the current page and can be limited by the configured page size.
