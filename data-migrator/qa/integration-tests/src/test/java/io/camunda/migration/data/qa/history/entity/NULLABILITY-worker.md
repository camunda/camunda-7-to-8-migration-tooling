# `JobEntity.worker` — Nullability investigation

Related: [issue #1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Triggering C8 change: [camunda/camunda#51301](https://github.com/camunda/camunda/pull/51301) added `Objects.requireNonNull(worker, "worker")` to `JobEntity`'s compact constructor.
Test: `NullabilityContractTest.shouldNotProduceNullWorkerForExternalTask` (enabled, failing).

Two C7 source types feed `JobEntity.worker` through different transformers. They have different defects.

## Two migration paths

Both transformers live under `data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/`. Dispatch happens by entity type in the corresponding migrators (`JobMigrator.migrateTransactionally` and `ExternalTaskMigrator.migrateTransactionally`).

| C7 source                          | Transformer                                                            | Worker today           |
|------------------------------------|------------------------------------------------------------------------|------------------------|
| `HistoricJobLog` (regular jobs)    | `JobTransformer.java:58` — `.worker(historicJobLog.getHostname())`     | Non-null in normal C7  |
| `HistoricExternalTaskLog`          | `ExternalTaskTransformer.java:65` — `.worker(null)`                    | Always null            |

The external-task transformer never reads `entity.getWorkerId()` — null is hardcoded.Sometimes worker could be populated by reading the external task logs. Currently we only process the first log for each external task. There would be still edge cases where it is null and would increase complexity to populate a field that is not particularly important for customers.
 The regular-job transformer pass-throughs `getHostname()` with no null check.

## Edge cases — regular jobs can also produce null

`historicJobLog.getHostname()` is non-null in normal C7 operation but is not contractually guaranteed. The C7 source that matters lives in `camunda-bpm-platform-maintenance/engine/src/main/java/`:

1. **Custom `HostnameProvider` returning null.**
   `org/camunda/bpm/engine/impl/cfg/ProcessEngineConfigurationImpl.java:4479` exposes `setHostnameProvider(HostnameProvider)` as a public configuration extension. Customers can install any implementation, including one that returns null. The history producer at `org/camunda/bpm/engine/impl/history/producer/DefaultHistoryEventProducer.java:1133-1134` stamps the value onto the log event with no null check.

2. **Legacy rows with null `HOSTNAME_`.**
   `ACT_HI_JOB_LOG.HOSTNAME_` is declared `varchar(255)` (nullable) on every supported RDBMS — see `engine/src/main/resources/org/camunda/bpm/engine/db/create/activiti.{h2,postgres,oracle,mysql,mssql,mariadb,db2}.create.history.sql` around line 290. Rows written by older C7 versions or by engines with broken hostname recording remain null in the database.

3. **Failed local-host resolution (degraded, but not null).**
   The default `SimpleIpBasedProvider.getHostname()` (`org/camunda/bpm/engine/impl/history/event/SimpleIpBasedProvider.java:33-44`) catches `InetAddress` exceptions and falls back to `"$<engineName>"`. Doesn't produce null on its own, but produces a degraded value that downstream tooling will treat as a real worker identifier.

In normal deployments, regular-job migration produces a non-null worker. In edge-case deployments (1–2 above), it produces null, and the C8 `requireNonNull` fires through exactly the same code path as the external-task case.

## Solution proposals

### Option B — set worker where possible, placeholder otherwise (recommended)

Single fallback expression in both transformers:

```java
// JobTransformer.java:58
.worker(historicJobLog.getHostname() != null ? historicJobLog.getHostname() : MIGRATED_PLACEHOLDER)

// ExternalTaskTransformer.java:65
.worker(entity.getWorkerId() != null ? entity.getWorkerId() : MIGRATED_PLACEHOLDER)
```

Suggested constant: `"camunda-7-migrated"`. Unambiguous, won't collide with valid worker IDs, makes the audit story explicit for downstream consumers.

- Preserves real hostname for the dominant regular-job case.
- Satisfies the C8 `requireNonNull` contract on both paths.
- Forward-compatible: when the migrator eventually selects a non-creation log entry for external tasks (tracked separately — the dedup picks the creation log today, which has no `workerId`), the same expression starts transcribing real `workerId` values without code change.

### Option A — universal placeholder (rejected)

Write the placeholder for every migrated job regardless of source. Strictly simpler, but discards the real hostname data that regular-job migration currently transcribes. No benefit over Option B.

### Option 4 — relax the C8 contract (parallel, optional)

`worker` is semantically nullable: a job that was created but never activated has no worker on the C8 side either. Worth raising with the C8 team as a parallel question — a relaxed contract would let the placeholder eventually disappear. Doesn't gate Option B.