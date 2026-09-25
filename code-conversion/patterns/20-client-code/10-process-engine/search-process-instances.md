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

Camunda 8.9's `ProcessInstanceFilter` includes a `variables` array. Each entry requires a variable `name` and a JSON-serialized `value`, so process-instance search can match a variable by name and value. The recipe intentionally leaves all seven Camunda 7 `variableValue...(...)` predicates unchanged because it does not yet map their full comparison semantics to equivalent C8 filters. This applies to `list()`, `count()`, and `singleResult()` queries. It also leaves list/count chains through a `ProcessInstanceQuery` parameter or alias with an untraceable origin unchanged, because the recipe cannot prove that the query has no variable filters. These guards avoid partially converting a query and dropping its predicate.

Camunda 7 `RuntimeService` queries exclude completed instances. Without `.active()`, they can include suspended instances; `.active()` excludes suspended instances. The recipe only converts supported count queries that call `.active()`, filtering them to `ACTIVE`. The recipe has no target-version setting, and `SUSPENDED` is not part of the Camunda 8.9 process-instance state enum. Queries without `.active()` therefore remain unchanged for manual migration rather than emitting code that does not compile for Camunda 8.9 or dropping suspended instances on a target that supports them. For a manual default-query migration to Camunda 8.10+, include both `ACTIVE` and `SUSPENDED` where needed. Queries with an explicit `.suspended()` filter also remain manual.

The recipe leaves queries with `processInstanceBusinessKey(...)` unchanged. Business IDs can be set in Camunda 8.9, but process-instance search filtering by `businessId` is supported starting in 8.10; because the recipe has no target-version setting, it does not emit that filter. It also leaves every process-instance `list()` result unchanged: the search API is paginated, and `.items()` returns only the current page, while Camunda 7 `list()` returns all matches. This applies to direct results, declarations, assignments, and downstream stream operations. Supported count-only forms, including `.count()`, `.list().size()`, and `.list().stream().count()`, can still be converted to `.page().totalItems()` when the query calls `.active()`.

For an exact name/value match, use the `variables` filter on `POST /v2/process-instances/search` with the variable name and JSON-serialized value. For C7 comparison predicates that do not map directly to equivalent C8 filters, keep the query manual and preserve its semantics; do not drop the variable predicate while migrating other filters. If a separate variable search is needed, use `POST /v2/variables/search`, then apply remaining filters to the returned process-instance keys while preserving pagination and accounting for the endpoint's scope and consistency semantics.
