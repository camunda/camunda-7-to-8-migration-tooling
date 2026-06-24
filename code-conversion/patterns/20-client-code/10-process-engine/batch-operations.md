# Batch Operations

In Camunda 7, batch operations are created via `RuntimeService` and `HistoryService` methods (typically the `...Async` variants) and managed via the `ManagementService`.

Since **Camunda 8.8**, batch operations are a first-class concept of the Orchestration Cluster: they are executed by Zeebe itself, addressed by filter criteria, and can be monitored, suspended, resumed, and cancelled. Available types:

| Operation                  | Camunda 7                                            | Camunda 8 (Orchestration Cluster API)                           |
| -------------------------- | ---------------------------------------------------- | --------------------------------------------------------------- |
| Cancel process instances   | `runtimeService.deleteProcessInstancesAsync(...)`    | `POST /v2/process-instances/cancellation` (8.8+)                 |
| Resolve incidents / retry  | `managementService.setJobRetriesAsync(...)`          | `POST /v2/process-instances/incident-resolution` (8.8+)          |
| Migrate process instances  | `runtimeService.newMigration(plan).executeAsync()`   | `POST /v2/process-instances/migration` (8.8+)                    |
| Modify process instances   | `runtimeService.createModification(...).executeAsync()` | `POST /v2/process-instances/modification` (8.8+)              |
| Delete historic instances  | `historyService.deleteHistoricProcessInstancesAsync(...)` | `POST /v2/process-instances/deletion` (8.9+)                  |
| Delete decision instances  | `historyService.deleteHistoricDecisionInstancesAsync(...)` | `POST /v2/decision-instances/deletion` (8.9+)                |

## Cancel Process Instances in Batch

### ProcessEngine (Camunda 7)

```java
    public Batch cancelProcessInstances(List<String> processInstanceIds, String reason) {
        return engine.getRuntimeService().deleteProcessInstancesAsync(processInstanceIds, reason);
    }
```

### Orchestration Cluster API (Camunda 8)

In Camunda 8, the batch is defined by a *filter* instead of an explicit ID list — matching instances are determined at execution time:

```
POST /v2/process-instances/cancellation
{
  "filter": {
    "processDefinitionId": "order-process",
    "hasIncident": true
  }
}
```

The response contains a `batchOperationKey`. Track progress via `GET /v2/batch-operations/{batchOperationKey}`, and search batches via `POST /v2/batch-operations/search`.

The Camunda Java client exposes the same batch operation endpoints — see the [Java client documentation](https://docs.camunda.io/docs/apis-tools/java-client/getting-started/) and the [batch operations docs](https://docs.camunda.io/docs/components/concepts/batch-operations/) for the current command API. To target an explicit list of instances, filter on `processInstanceKey` with an `$in` operator.

-   batch operations run asynchronously and are resilient to leader changes and region failovers (Zeebe-managed since 8.8)
-   batches can be suspended, resumed, and cancelled via the API; Operate provides a dedicated Batch Operations monitoring view (8.9)
-   only ACTIVE root instances can be cancelled — state filters are overridden during a cancellation batch
-   cancelling a batch does not roll back already-processed items

## Notes for Migration

-   do **not** flag Camunda 7 batch code as "no equivalent in Camunda 8" — since 8.8 there is a direct mapping for the common cases listed above
-   Camunda 7 *seed/execution job* tuning (`batchJobsPerSeed`, invocation counts) has no equivalent and can be dropped; Zeebe partitions the work internally
-   custom batch handlers (`ManagementService#createBatch` with custom job handlers) have no generic equivalent — implement those as a regular client-side loop over a search request, or as a process
