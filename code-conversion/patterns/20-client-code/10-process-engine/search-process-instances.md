# Search Process Instances

Camunda 7 `RuntimeService` process-instance queries return runtime instances and can filter process variables. Preserve those semantics when migrating the query.

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

The Orchestration Cluster REST API's `POST /v2/process-instances/search` contract does not define a variable field in `ProcessInstanceFilter`. Do not emit `.variables(...)` on `newProcessInstanceSearchRequest()`; the client builder may compile, but the server cannot enforce that predicate. The query migration recipe leaves chains containing `variableValueEquals(...)` unchanged, including `list()`, `count()`, and `singleResult()` queries. It also leaves list/count chains through a `ProcessInstanceQuery` parameter or alias with an untraceable origin unchanged, because the recipe cannot prove that the query has no variable filters. These guards avoid partially converting a query and dropping its predicate.

Camunda 7 `RuntimeService` queries exclude completed instances. Without `.active()`, they can include suspended instances; `.active()` excludes suspended instances. The recipe only converts supported count queries that call `.active()`, filtering them to `ACTIVE`. The recipe has no target-version setting, and `SUSPENDED` is not part of the Camunda 8.9 process-instance state enum. Queries without `.active()` therefore remain unchanged for manual migration rather than emitting code that does not compile for Camunda 8.9 or dropping suspended instances on a target that supports them. For a manual default-query migration to Camunda 8.10+, include both `ACTIVE` and `SUSPENDED` where needed. Queries with an explicit `.suspended()` filter also remain manual.

The recipe leaves queries with `processInstanceBusinessKey(...)` unchanged. Business IDs can be set in Camunda 8.9, but process-instance search filtering by `businessId` is supported starting in 8.10; because the recipe has no target-version setting, it does not emit that filter. It also leaves every process-instance `list()` result unchanged: the search API is paginated, and `.items()` returns only the current page, while Camunda 7 `list()` returns all matches. This applies to direct results, declarations, assignments, and downstream stream operations. Supported count-only forms, including `.count()`, `.list().size()`, and `.list().stream().count()`, can still be converted to `.page().totalItems()` when the query calls `.active()`.

For a manual migration, use `POST /v2/variables/search` to find candidate variables by name and JSON-serialized value, then use their process-instance keys to apply the remaining process-definition and state filters. Preserve pagination and account for the variable search endpoint's scope and consistency semantics. Do not drop the variable predicate when migrating the other filters.
