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

## Recipe boundary

The recipe converts only complete, inline `.active()` counts (`count()`, `list().size()`, and `list().stream().count()`) with no other filter or a single `processDefinitionKey(...)`. It marks all other process-instance queries with a manual-migration TODO **without changing the query or its result type**. This includes variable predicates, business keys, `activityIdIn(...)`, default/suspended state, query aliases, `singleResult()`, and `list()` results. A Camunda 8 search page's `.items()` is not equivalent to Camunda 7's unbounded `list()`.

## Camunda 8.9+ manual lookup

For an exact active name/value match, Camunda 8.9's `POST /v2/process-instances/search` supports a server-side `variables` filter:

```http
POST /v2/process-instances/search
Content-Type: application/json

{
  "filter": {
    "processDefinitionId": "orders",
    "state": "ACTIVE",
    "variables": [{ "name": "projectId", "value": "\"project-42\"" }]
  },
  "page": { "limit": 2 }
}
```

The variable value is JSON-serialized (a string therefore includes escaped quotation marks). Verify that C7 comparison and variable-scope semantics match before substituting this filter. Use the filtered result to distinguish zero, one, and multiple matches; do not check an unfiltered count or assume the first page contains every match. For complete lists, follow the search cursor until no further pages remain. Search data is [eventually consistent](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-data-fetching/#data-consistency), so an immediate lookup may miss a newly started instance.

Camunda 7's default runtime query can include suspended instances; explicit `.active()` excludes them. The Camunda 8.9 state filter has no `SUSPENDED` value, so default/suspended queries need a target-version-specific design. Business ID filtering in process-instance search starts in 8.10; business-key queries also remain manual.
