# NullabilityContract — `JobEntity.lastUpdateTime`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Failing test: `NullabilityContractTest.shouldNotProduceNullLastUpdateTimeForJob`
Related row in [NULLABILITY-status.md](./NULLABILITY-status.md): **#2**.

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added
`Objects.requireNonNull(lastUpdateTime, "lastUpdateTime")` to the
`JobEntity` compact constructor
([`JobEntity.java:64`](../../../../../../../../../../../../camunda/search/search-domain/src/main/java/io/camunda/search/entities/JobEntity.java#L64)).
The migrator never assigns the field on any code path that writes a
`JobDbModel`, so every migrated job row has `LAST_UPDATE_TIME IS NULL`
in the C8 RDBMS — and the C8 read path turns that into NPE.

## Write paths — both leave the field null

Two C7 source types feed `JobDbModel` through different transformers,
both dispatched from
[`HistoryEntityMigrator`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/HistoryEntityMigrator.java)
subclasses. Neither sets `lastUpdateTime` anywhere.

| C7 source                       | Transformer + Migrator                                                                                  | Where `lastUpdateTime` is set |
|---------------------------------|---------------------------------------------------------------------------------------------------------|-------------------------------|
| `HistoricJobLog` (regular jobs) | [`JobTransformer.java:53-66`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/JobTransformer.java#L53-L66) + [`JobMigrator.java:92-159`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/JobMigrator.java#L92-L159) | nowhere |
| `HistoricExternalTaskLog`       | [`ExternalTaskTransformer.java:54-72`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/ExternalTaskTransformer.java#L54-L72) + [`ExternalTaskMigrator.java:86-144`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/ExternalTaskMigrator.java#L86-L144) | nowhere |

The `JobDbModel.Builder` defaults the field to a null `OffsetDateTime`
([`JobDbModel.java:438`](../../../../../../../../../../../../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/write/domain/JobDbModel.java#L438)),
which the MyBatis insert maps to SQL `NULL`
([`JobMapper.xml:255-265`](../../../../../../../../../../../../camunda/db/rdbms/src/main/resources/mapper/JobMapper.xml#L255-L265)
— `#{job.lastUpdateTime, jdbcType=TIMESTAMP}` with no `jdbcType`
substitution or default coalescing).

## Read path — no coercion

[`JobEntityMapper.toEntity`](../../../../../../../../../../../../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/mapper/JobEntityMapper.java#L20-L48)
passes `jobDbModel.lastUpdateTime()` straight into the builder. There
is no analogue of
[`NullSafeStrings.nullToEmpty`](../../../../../../../../../../../../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/NullSafeStrings.java)
for `OffsetDateTime`, and the only string fields it covers are `type`,
`worker`, `processDefinitionId`, `elementId`, `tenantId`. So a null DB
value reaches the `JobEntity` compact constructor as a Java `null`,
where `requireNonNull(lastUpdateTime, "lastUpdateTime")` throws.

Confirmed observable by `NullabilityContractTest.shouldNotProduceNullLastUpdateTimeForJob`
([NullabilityContractTest.java:101-132](./NullabilityContractTest.java#L101-L132)),
which migrates a single async-before job and asserts the search-API
result has a non-null `lastUpdateTime`. The test is enabled and (per
the status doc's classification of #2 as ✅ Real bug) failing.

## Data in scope at the write point

When either transformer runs, the following are already in scope:

- The single log entry being processed: its `getTimestamp()` is the
  value already used for `creationTime`
  ([`JobTransformer.java:54`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/JobTransformer.java#L54),
  [`ExternalTaskTransformer.java:56`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/ExternalTaskTransformer.java#L56)).
  Non-null in C7 (`Date getTimestamp()` is part of the
  `HistoricJobLog` interface contract;
  [`ACT_HI_JOB_LOG.TIMESTAMP_`](../../../../../../../../../../../../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/history/HistoricJobLog.java#L50-L52)
  is the stamp the history producer writes for every log entry).
- The migrator-side `creationTime` value already on the builder.

What the migrator does **not** have without an extra query:

- The timestamp of the *latest* log entry for the same job. The
  framework consumes logs in
  `(timestamp ASC, jobId ASC)` order
  ([`C7Client.java:629-643`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/clients/C7Client.java#L629-L643))
  and `shouldMigrate`
  ([`HistoryEntityMigrator.java:375-380`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/HistoryEntityMigrator.java#L375-L380))
  skips every subsequent log entry once the job ID is tracked. So the
  one entry the transformer sees is always the *earliest*; subsequent
  lifecycle events (failure, success, deletion) are not read.

The "last log entry" would therefore require either:
- A separate `historyService.createHistoricJobLogQuery().jobId(...).orderByTimestamp().desc().listPage(0,1)`
  call per job at migration time (one extra round trip), or
- Reversing the iteration to skip-then-overwrite, which the current
  `shouldMigrate`/tracking-table design doesn't support.

## Option analysis

The five-option vocabulary is defined in
[`NULLABILITY-resolution.md`](./NULLABILITY-resolution.md). Per-row
viability:

**Option 1 — do nothing.** Not viable. Every C8 job search that returns
a migrated row NPEs at the entity constructor.

**Option 2 — canary test.** Not viable. There is no read-side
coercion to pin: the null arrives at the entity constructor as a Java
`null`, and the `requireNonNull` fires immediately. Nothing is being
silently masked.

**Option 3 — migrator-side default.** Three sub-shapes:

- **3a — set `lastUpdateTime = creationTime`.** Single line per
  transformer (or a builder default if pushed up). The value carries
  the same meaning as `creationTime` for migrated rows: "this is the
  latest known state from C7." Semantically defensible because every
  migrated job is written with `state = JobState.COMPLETED`
  ([`JobTransformer.java:59`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/JobTransformer.java#L59),
  [`ExternalTaskTransformer.java:60`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/ExternalTaskTransformer.java#L60))
  — i.e., the C8 row is a terminal-state snapshot, so the C8 notion
  of "last update" collapses to the only timestamp the migrator
  records. Caveat: the timestamp written is the *earliest* C7 log
  entry's timestamp (creation log), not the actual last lifecycle
  event in C7. For terminal-state rows that distinction is invisible
  via the C8 search API; it would matter only if a downstream consumer
  diffs `creationTime` and `lastUpdateTime` for migrated rows. Cost:
  ~2 lines.

- **3b — fetch the latest log entry per job.** Extend the C7Client
  with a `getHistoricJobLogLatest(jobId)` (and the external-task
  counterpart) that orders by `timestamp DESC` and returns the first
  result — mirror of the existing `getHistoricJobLog(jobId)` at
  [`C7Client.java:614-624`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/clients/C7Client.java#L614-L624)
  which orders ASC. Then set
  `lastUpdateTime = convertDate(latestLog.getTimestamp())`, using the
  same `HistoricJobLog#getTimestamp()` method that
  [`JobTransformer.java:54`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/JobTransformer.java#L54)
  already calls on the earliest log to source `creationTime`.
  `HistoricJobLog` has no separate "last update" field of its own
  ([interface contract](../../../../../../../../../../../../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/history/HistoricJobLog.java#L42-L185)
  — `getTimestamp()` is the only per-entry timestamp); the "latest"
  comes from picking a different *row*, not a different *field*.
  Cost: ~20 lines + one extra C7 round trip per migrated job,
  slowing migration proportionally to the job count. Semantic gain:
  `lastUpdateTime` reflects the real last lifecycle event in C7
  (typically the final success / failure / deletion log, per
  [`HistoricJobLog.java:155-174`](../../../../../../../../../../../../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/history/HistoricJobLog.java#L155-L174)).
  Whether that gain is observable to any C8 consumer is not
  established — all migrated rows have `state = COMPLETED`, and the
  C8 search API does not surface "time since last log entry" except
  by exposing this field.

  Side-note if 3b is chosen: today's `creationTime` is sourced from
  the earliest log entry the transformer sees, which is *usually*
  but not contractually the entry with `isCreationLog() == true`.
  3b only fixes `lastUpdateTime`; tightening `creationTime` to come
  explicitly from the creation log is a separate decision.

- **3c — set `lastUpdateTime` to a fabricated value (e.g.
  `Instant.now()` at migration time).** Rejected as misleading: it
  encodes "migration time", not anything about the C7 job.

**Option 4 — negotiate with C8.** Ask the C8 team to permit
`lastUpdateTime` to be `@Nullable`. Cheap from this repo's side; long
calendar. Defensible if the C8 team agrees that "a job migrated from
C7 history is fundamentally a snapshot, and a 'last update' field is
only meaningful for jobs that exist in C8's runtime." The contract
relaxation would require reverting one `requireNonNull` line and one
`@Nullable` annotation in
[`JobEntity.java`](../../../../../../../../../../../../camunda/search/search-domain/src/main/java/io/camunda/search/entities/JobEntity.java).

**Option 5 — skip rows with null `lastUpdateTime`.** Equivalent to
"skip every migrated job", which is not a viable migration outcome.

## User-facing impact

- **Today:** any C8 search API call that returns a migrated job
  throws NPE at the entity constructor. As with #3, the failure
  blocks a page of jobs, not just the one with the null field, when
  Operate or any consumer issues a job search.
- **With Option 3a (`= creationTime`):** the C8 row satisfies the
  contract; the search API returns. Operate's "last updated" column
  for migrated jobs equals the creation time — i.e. the time the
  first C7 log entry was recorded (typically job creation). That is
  not factually wrong for a job in terminal state but is also not
  the "real" last update; flag for product review only if Operate's
  UI presents `lastUpdateTime` as a primary timestamp distinct from
  `creationTime`.
- **With Option 3b (latest-log timestamp):** the column shows the
  actual time of the last C7 lifecycle event. More accurate, but the
  observable improvement depends on Operate surfacing
  `lastUpdateTime` separately, and the cost is a per-job C7 query.
- **With Option 4 (relax to nullable):** Operate / search-API
  consumers must tolerate null `lastUpdateTime`. Implementation work
  shifts from migrator to C8 + UI.

## Audit against existing docs

`NULLABILITY-status.md` row #2 reads
"Field never set by migrator on any code path." — verified across
`JobTransformer`, `ExternalTaskTransformer`, `JobMigrator`,
`ExternalTaskMigrator`. No discrepancy.

`NULLABILITY-resolution.md` line 70-74 already proposes Option 3 with
the choice between "creationTime" and "latest log entry". The
sub-option costs and trade-offs above add what was missing for a
confident Phase C decision; no claim in the existing rationale is
contradicted, but the "or from `HistoricJobLog.getTimestamp()` on the
latest log entry per job" hint glosses the fact that no current code
path reads more than the first log entry per job — Option 3b is a
non-trivial extension, not a one-line tweak.

## Open items to confirm before Phase C

1. **Product decision: 3a vs 3b vs 4.** Does Operate (or any
   downstream consumer) distinguish `lastUpdateTime` from
   `creationTime` for migrated, terminal-state jobs in a way that
   makes the per-job extra C7 query worth the latency? If "no", 3a
   is the cheap correct answer. If "yes", 3b is justified.
2. **Cross-check the e2e claim** in
   [`NULLABILITY-status.md` line 91](./NULLABILITY-status.md#L91) that
   the running stack shows `job.last_update_time null = 3 rows` for
   the existing `miProcess` seed. The e2e walk-through doc at
   `data-migrator/qa/e2e-tests/NULLABILITY-e2e-scenarios.md` line 40-51
   describes the scenario but the row count was not re-verified in
   this investigation.
