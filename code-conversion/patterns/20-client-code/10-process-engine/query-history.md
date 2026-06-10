# Query History

In Camunda 7, the `HistoryService` exposes queries over historic process instances, activity instances, variables, and the user operation log — all served from the engine's relational database.

In Camunda 8, runtime and history data are separated: historic data is exported to *secondary storage* (Elasticsearch, OpenSearch, or — since 8.9 — an RDBMS) and queried via the **Orchestration Cluster API search endpoints**, which cover both running and finished entities. The `CamundaClient` exposes them as search requests.

| Camunda 7 (`HistoryService`)                       | Camunda 8 (`CamundaClient` / Orchestration Cluster API)   |
| -------------------------------------------------- | --------------------------------------------------------- |
| `createHistoricProcessInstanceQuery()`             | `newProcessInstanceSearchRequest()`                        |
| `createHistoricActivityInstanceQuery()`            | `newElementInstanceSearchRequest()`                        |
| `createHistoricVariableInstanceQuery()`            | `newVariableSearchRequest()`                               |
| `createHistoricIncidentQuery()`                    | `newIncidentSearchRequest()`                               |
| `createHistoricTaskInstanceQuery()`                | `newUserTaskSearchRequest()`                               |
| `createHistoricDecisionInstanceQuery()`            | `newDecisionInstanceSearchRequest()`                       |
| `createUserOperationLogQuery()`                    | Audit log search (`POST /audit-logs/search`, 8.9+)         |

## Searching Finished Process Instances

### ProcessEngine (Camunda 7)

```java
    public List<HistoricProcessInstance> findFinishedInstances(String processDefinitionKey) {
        return engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .finished()
                .list();
    }
```

### CamundaClient (Camunda 8)

```java
    public List<ProcessInstance> findFinishedInstances(String processDefinitionId) {
        return camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .processDefinitionId(processDefinitionId)
                        .state(ProcessInstanceState.COMPLETED))
                .send()
                .join()
                .items();
    }
```

-   the same search endpoints serve running *and* finished entities — there is no separate "history API"
-   search results are *eventually consistent*: data becomes visible after export to secondary storage, typically within a second; do not use search requests for read-after-write logic inside a worker
-   history time to live (HTTL) and data retention are configured on the cluster, not per query
-   element instances are the equivalent of C7 activity instances; filter by `processInstanceKey` to get the execution trace (audit trail) of one instance

## Notes for Migration

-   **historic data itself does not migrate via code** — to carry Camunda 7 audit data over, use the [History Data Migrator](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/history/) (available since 8.9, requires RDBMS secondary storage)
-   `UserOperationLog` entries are converted into the Camunda 8 [audit log](https://docs.camunda.io/docs/components/audit-log/overview/) format by the History Data Migrator (8.9)
-   C7 history levels (`FULL`, `AUDIT`, `ACTIVITY`, `NONE`) have no equivalent — Camunda 8 always exports; retention is controlled via TTL
-   reporting/BI use cases should consider Optimize or consuming exported data instead of polling search endpoints
