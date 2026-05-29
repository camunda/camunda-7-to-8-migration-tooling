# NullabilityContract — `IncidentEntity.flowNodeInstanceKey`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Failing test: `NullabilityContractTest.shouldNotProduceNullFlowNodeInstanceKeyForAsyncBeforeIncident`
Related row in [NULLABILITY-status.md](./NULLABILITY-status.md): **#10**.

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added a
`requireNonNull(flowNodeInstanceKey, "flowNodeInstanceKey")` to the
`IncidentEntity` compact constructor
([`IncidentEntity.java:39`](../../../../../../../../../../../../camunda/search/search-domain/src/main/java/io/camunda/search/entities/IncidentEntity.java#L39)).
The migrator writes SQL `NULL` for that column on one identifiable
migration path, causing the C8 search-API read side to throw NPE.

## Root cause

The null write is at
[`IncidentMigrator.java:129-140`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/IncidentMigrator.java#L129-L140):

```java
if (dbModel.flowNodeInstanceKey() == null) {
  if (hasMultipleFlowNodes.get()) {
    throw new EntitySkippedException(c7Incident, SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE);
  }
  // Activities on async before waiting state will not have a flow node instance key,
  // but should not be skipped
  if (!c7Client.hasWaitingExecution(c7Incident.getProcessInstanceId(), c7Incident.getActivityId())) {
    throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_FLOW_NODE);
  }
}
```

The null write is the **fall-through**: when `flowNodeInstanceKey` is
null and `hasWaitingExecution(...)` returns `true`, neither branch
throws. Control falls out of the block and the incident is inserted
with `flowNodeInstanceKey = null` at line 150.

`flowNodeInstanceKey` is null at line 129 because the lookup at lines
97-99 returned null:

```java
flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(),
    c7Incident.getProcessInstanceId(), hasMultipleFlowNodes);
builder.flowNodeInstanceKey(flowNodeInstanceKey);
```

`findFlowNodeInstanceKey`
([`HistoryEntityMigrator.java:340-368`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/HistoryEntityMigrator.java#L340-L368))
queries the C8 RDBMS for an already-migrated flow node row matching
`(activityId, processInstanceKey)`. It returns null when no such row
exists in C8.

Migration order is correct in production:
[`HistoryMigrator.getMigrators()`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/HistoryMigrator.java#L90-L106)
runs `flowNodeMigrator` before `incidentMigrator`, so every
`HistoricActivityInstance` that *exists in C7* at the moment of
migration has already been copied to C8. The lookup returns null
because **no `HistoricActivityInstance` exists in C7** for an
async-before activity that has not been entered yet.

The status doc's one-liner ("async-before incidents — deliberate null
in `IncidentMigrator.java:129-140`") is correct as far as it goes, but
the trigger is narrower than "async-before incident": it requires the
C7 *runtime* execution to still be parked at the async-before activity
at migration time. See next section.

## When the deliberate-null path fires in production

`c7Client.hasWaitingExecution`
([`C7Client.java:839-848`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/clients/C7Client.java#L839-L848))
queries the C7 **runtime** (`runtimeService.createExecutionQuery()`),
not history. It returns `true` only when there is an *active*
execution at the activity in the *currently running* C7 process
instance.

So the deliberate-null write fires when **all** of the following hold
at migration time:

1. The incident is recorded against an async-before activity in C7.
2. The activity has never produced a `HistoricActivityInstance`
   (i.e. it has never been entered — async-before resolution failed
   before activity entry).
3. The C7 process instance is **still active**, with an execution
   parked at the async-before activity.

When the PI has finished, been deleted, or otherwise no longer carries
an active execution at the activity, `hasWaitingExecution` returns
`false` and the incident is skipped with
`SKIP_REASON_MISSING_FLOW_NODE` (line 138). That branch is not the
bug path.

The path therefore corresponds to **in-flight migration** of a live
C7 instance that has an unresolved async-before incident.

## Reproduction

[`NullabilityContractTest.shouldNotProduceNullFlowNodeInstanceKeyForAsyncBeforeIncident`](./NullabilityContractTest.java#L408-L435)
deploys
[`incidentProcess.bpmn`](../../../../../../resources/io/camunda/migration/data/bpmn/c7/incidentProcess.bpmn),
which has one `<bpmn:serviceTask>` with `camunda:asyncBefore="true"`,
`camunda:class="io.camunda.migration.data.qa.util.Foo"` (a missing
class), and `camunda:failedJobRetryTimeCycle="R0/PT0S"` (zero retries,
no backoff).

`triggerIncident(...)` ([`AbstractMigratorTest.java:72-81`](../../../AbstractMigratorTest.java#L72-L81))
attempts `managementService.executeJob(...)` up to three times. The
first attempt fails (class load), retries hit zero, a `HistoricIncident`
is recorded, and the execution remains parked at the async-before
activity. The PI stays active.

`historyMigrator.migrate()` then runs the full pipeline against a live
C7 PI:

- `FlowNodeMigrator` finds an HAI only for `startEventId` (synchronous
  start event), none for `incidentTask1Id` — the async-before activity
  has not been entered.
- `IncidentMigrator` looks up an FNI for `incidentTask1Id`, gets null,
  consults `hasWaitingExecution(..., "incidentTask1Id")`, which returns
  `true` (the parked execution), and inserts the incident with
  `flowNodeInstanceKey = null`.

The test then reads the incident via `incidentReader.search(...)`,
which constructs `IncidentEntity` through the compact constructor and
throws `NullPointerException("flowNodeInstanceKey")`.

## Data in scope at the write point

At [`IncidentMigrator.java:129`](../../../../../../../../../core/src/main/java/io/camunda/migration/data/impl/history/migrator/IncidentMigrator.java#L129), the migrator already
has on the builder or the source `HistoricIncident`:

- `c7Incident.getActivityId()` — the BPMN element id of the target
  activity. Non-null at this point: the lookup at line 97 dereferences
  it via `findFlowNodeInstanceKey`, which calls `.endsWith(...)` on it
  (line 346) without a null guard, so a null `activityId` would NPE
  earlier.
- `dbModel.processInstanceKey()` — non-null (the line-121 guard would
  have thrown `SKIP_REASON_MISSING_PROCESS_INSTANCE` otherwise).
- `dbModel.processDefinitionKey()` — non-null (line-117 guard).
- `dbModel.rootProcessInstanceKey()` — non-null (line-125 guard).
- `partitionId` — populated alongside `rootProcessInstanceKey`.
- `c7Incident.getId()` (HistoricIncident id), `getIncidentMessage()`,
  `getIncidentType()`, `getConfiguration()` (the C7 job id, if any).

What the migrator does **not** have:

- Any `HistoricActivityInstance` for the activity (no C7 row exists).
- A genuine "flow node instance start time" — the activity hasn't
  been entered, so no execution timestamp is meaningful.
- A C8 `FlowNodeInstanceDbModel` row to point at. Any synthesised row
  would need a fresh key, a tree path, and would be visible in
  Operate's flow-node view as a node that never started.

## Option analysis

Per the five-option vocabulary in [`NULLABILITY-resolution.md`](./NULLABILITY-resolution.md):

**Option 1 — do nothing.** Not viable. Production failure: any C8
search call returning the affected `IncidentEntity` throws NPE in the
compact constructor; a paginated incidents list for the affected PI
collapses on this row.

**Option 2 — canary test.** Not viable. There is no foreign-side
coercion masking the null — the field arrives at the entity
constructor as Java `null`, not as a substituted value. Nothing to
pin.

**Option 3 — migrator-side default.** Two shapes, materially different
in cost and side-effects, mirroring the analysis in
[`NULLABILITY-elementInstanceKey.md`](./NULLABILITY-elementInstanceKey.md):

- **3a — skip the incident.** Drop the fall-through and let line 138
  handle the case, or add an explicit skip for the
  `hasWaitingExecution == true` branch. ~3 lines. Trade-off: a live
  in-flight async-before incident disappears from C8. For an operator
  triaging an in-flight migration ("which activities have unresolved
  incidents right now?"), losing this row may matter — these are the
  incidents most likely to need human attention.
- **3b — synthesize a flow node instance.** Mint a new
  `flowNodeInstanceKey`, populate the incident, and insert a matching
  `FlowNodeInstanceDbModel`. Likely with `state = ACTIVE` (the
  execution *is* there in C7 runtime) or `TERMINATED` (the activity
  never actually started its work). Larger: introduces a "synthetic
  FNI for never-entered activity" concept, must cross-check tree-path
  / scope-key constraints in the C8 schema, and affects how Operate's
  flow-node view renders an activity that has no real entry/exit.

**Option 4 — negotiate with C8.** Ask C8 to relax `flowNodeInstanceKey`
to `@Nullable` for the "incident attached to an unentered async-before
activity" case. Low cost here; long calendar; depends on whether C8
considers a flow-node-less incident a legitimate entity state.

**Option 5 — skip migrating rows with null values.** Convergent with
Option 3a in this case (no distinct fifth path exists for this row).

## User-facing impact

- **Today:** any C8 search call returning the affected incident throws
  NPE. A user listing incidents for the affected PI sees the whole page
  fail, not just one row.
- **With Option 3a (skip):** the live in-flight async-before incident
  is absent from C8. The skip is recorded with a reason in the
  migrator's skip log, so the loss is observable to the operator. UX
  question: is "this PI had an unresolved incident but it didn't
  migrate" an acceptable visibility gap for in-flight migrations?
- **With Option 3b (synthesize FNI):** Operate shows a synthetic flow
  node for an activity that has not actually run. Whether to render it
  as ACTIVE or TERMINATED is a product call. The incident hangs off
  this synthetic node.
- **With Option 4 (relax contract):** the incident is preserved with a
  real-but-null `flowNodeInstanceKey`; Operate must handle
  flow-node-less incidents in its UI.

The choice between 3a, 3b, and 4 is a product / UX decision (what
*should* Operate show for a live in-flight incident on an activity
that hasn't been entered?), not a code-mechanics decision. Flag for a
brief design review before implementing.

## Relation to scenario #3 (`JobEntity.elementInstanceKey`)

Status row #3 ([NULLABILITY-elementInstanceKey.md](./NULLABILITY-elementInstanceKey.md))
has the same underlying mechanic: async-before activity → no
`HistoricActivityInstance` → C8 FNI lookup returns null → migrator
writes null. Both rows use `findFlowNodeInstanceKey` from
`HistoryEntityMigrator`.

Two differences worth flagging to the user:

1. **Trigger predicate.** `JobMigrator` writes null unconditionally
   for async-before jobs whose FNI lookup misses (covers both live
   waiting and finished/never-entered states). `IncidentMigrator`
   gates the null write on `c7Client.hasWaitingExecution(...) == true`
   (live PI only) and skips otherwise. So #3's trigger surface is
   strictly broader than #10's.
2. **Production reach.** #3 is reachable from *both* live and
   finished C7 instances (verified end-to-end in the e2e seed where
   the PI has finished — see #3's "Trigger A"). #10 is reachable only
   from a **live** C7 PI with an active waiting execution. This
   matters for who hits the bug: customers doing in-flight migrations,
   not customers migrating after C7 retirement.

Because the **mechanic** is shared, the resolution choice (3a / 3b /
4) should be made jointly for #3 and #10. A consistent answer keeps
the symmetric pair consistent in Operate. The differing **trigger
predicates** can stay differing if 3a is chosen (each migrator's
existing predicates remain valid skip-vs-keep decisions). For 3b
(synthesize FNI), the synthesised FNI for the job and the incident
should be the *same* row when they correspond to the same C7 activity
on the same PI — that is a design constraint, not a refactor.

**Not investigated in this run** beyond noting the shared mechanic.
The investigation for #3 has its own pre-work in
`NULLABILITY-elementInstanceKey.md`; this doc is scoped to #10.

## Open items to confirm before Phase C

1. **Confirm the C7 runtime state in the test scenario.** I have not
   directly verified that after `triggerIncident` on the
   `incidentProcess.bpmn` fixture (`R0/PT0S` retry cycle), the
   execution is parked at `incidentTask1Id` such that
   `hasWaitingExecution(...)` returns `true`. The test failing on a
   null `flowNodeInstanceKey` is consistent with this, but the precise
   C7 lifecycle of an async-before job that immediately exhausts
   retries (whether the execution stays at the activity or rolls back)
   is worth a direct check via the camunda-7-investigation skill — it
   determines whether 3a's skip rule applies to the test scenario
   identically, or whether the test happens to land in a narrower
   subcase.
2. **Decide the product-level question** (3a vs 3b vs 4): what should
   Operate show for an in-flight async-before incident attached to an
   activity that has never been entered?
3. **Resolve jointly with #3.** Phase C for #10 should pair with
   Phase C for #3 so a coherent answer covers both. If 3b is chosen,
   the synthesised FNI must be shared between the job and the
   incident.
