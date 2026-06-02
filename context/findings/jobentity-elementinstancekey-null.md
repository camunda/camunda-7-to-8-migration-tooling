# `JobEntity.elementInstanceKey` is null after migration — what's happening and what to do

**Audience:** migration-tooling, C7, or C8 engineers picking this up cold.
**Reading time:** ~5 minutes.

Related: issue [#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339),
contract PR [camunda/camunda#51301](https://github.com/camunda/camunda/pull/51301),
companion technical doc
[NULLABILITY-elementInstanceKey.md](../../data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-elementInstanceKey.md).

## TL;DR

C8 recently added `requireNonNull(elementInstanceKey)` to the
`JobEntity` record. The data migrator can produce job rows where
`elementInstanceKey` is `NULL` — specifically, when a Camunda 7
async-before job never actually entered its activity (because the job
failed before then and retries ran out). After migration, any C8 search
API call that returns one of these jobs throws NPE, and that NPE
typically breaks an entire page of jobs in Operate, not just the bad
row.

The fix is small in code but the choice between fixes is a product
question, because the migrator is essentially being asked: *"what should
Operate show for a job whose activity never ran in C7?"*

## Why does the migrator write null?

The migrator's job is to take Camunda 7 history rows and write
equivalent Camunda 8 rows. For each C7 historic job log it processes,
it needs to know which C8 flow-node instance the job belongs to — that's
the `elementInstanceKey` field.

It looks this up by asking: *"is there a C8 flow-node row already
written for this C7 activity in this process instance?"* That lookup can
miss. When it misses, the current code silently leaves the field unset
and proceeds with the insert:

```
Long elementInstanceKey = findFlowNodeInstanceKey(activityId, processInstanceId, ...);
if (elementInstanceKey != null) {
  builder.elementInstanceKey(elementInstanceKey);
}
// no else — the row is written with NULL
```

For **async-after** jobs the same code path throws a recoverable error
instead. For **async-before** jobs the null is silently allowed. That
asymmetry is the bug.

### Why does the lookup miss?

The C8 flow-node row only exists if the migrator already migrated a
corresponding C7 `HistoricActivityInstance`. If no such row exists in
C7, the C8 side is empty, and the lookup fails.

This is **not** an ordering bug. The migrator does process flow nodes
before jobs in the default sequence. The lookup misses because C7 never
recorded the activity in the first place — not because the migrator
hasn't gotten around to it yet.

## When does this happen in real life?

Two distinct scenarios trigger the same null write. Only one is a real
production scenario; the other is the synthetic test that pins the bug.

### Scenario A — production: an async-before activity that never ran

This is the one customers hit. A service task is marked
`camunda:asyncBefore="true"` and configured with a `camunda:class`
delegate. When the async-before job fires, it tries to load that class
to enter the activity. If the class can't be loaded (deleted JAR, typo,
classpath drift after an upgrade), the job fails. After retries are
exhausted, the activity is never entered and the C7 history doesn't get
an activity-instance row for it. C7 *does* still record the failed job
attempts.

The e2e test suite reproduces this with a service task literally named
`failingTask` whose class reference doesn't resolve. After running the
full migrator, the resulting C8 database has exactly one job row with
`element_instance_key IS NULL` — the failing one.

> **Note for C7 engineers:** the precise C7-side question — *"does C7
> ever record a `HistoricActivityInstance` for an async-before job that
> fails before its activity is entered?"* — has not been traced through
> the engine code in this investigation. The e2e evidence is consistent
> with "no, it doesn't," but if you've seen the engine internals here
> and can confirm or refute that, please flag it. It matters for some
> of the fix options below.

### Scenario B — synthetic: the failing contract test

Independent of the production scenario, the migrator has an integration
test (`shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob`) that
forces the null path by deliberately migrating jobs *without* migrating
flow nodes first. The async-before user task in this test runs
successfully in C7, so the activity-instance row exists — but the test
prevents the migrator from copying it to C8. The lookup misses for the
same code reason as Scenario A.

This is a pin against the silent-null path, not a reproduction of
customer impact. Useful for catching regressions if a future change
breaks migration ordering or skips flow nodes.

## What does the user experience?

When the C8 search API loads a row from the `JOB` table, it constructs
a `JobEntity` Java record. That record's constructor calls
`Objects.requireNonNull(elementInstanceKey)`. With a NULL row in the
database, that throws NPE.

Concretely: a customer who has migrated their C7 history opens
Operate's "Jobs" view (or any UI / API call that pages through jobs)
and sees an internal error. Because the search API returns jobs in
pages, **one bad row can break an entire page of jobs**, not just the
affected one. So one missing flow-node instance for one failed
async-before job can hide dozens of correctly-migrated jobs from view
until the customer either deletes the row, filters around it, or
upgrades to a fixed migrator.

For migration triage specifically, hiding a *failed* async-before job
is the opposite of what a customer wants — that's exactly the kind of
job they're trying to find.

## Options to populate the field

Listed roughly from cheapest to most invasive. None of them is
obviously correct without a product call.

### Option A — skip the job

Mirror what the migrator already does for async-after jobs: if the
lookup misses, throw `EntitySkippedException` and record the skip in
the migrator's skip table with a clear reason.

- **Cost:** ~1–3 lines, no new concepts.
- **Customer impact:** the failed async-before job is gone from C8
  history. The skip is logged and visible to the migration operator,
  but not visible in Operate.
- **Tradeoff:** removes the broken-page problem entirely, at the cost
  of hiding one row per affected activity. The remaining jobs in the
  process instance become visible again.

### Option B — synthesize a flow-node instance

When the lookup misses, the migrator mints a new flow-node key, sets
it on the job, *and* writes a matching synthetic `FlowNodeInstance`
row into C8. To Operate, it then looks like the activity briefly
existed and immediately terminated.

- **Cost:** much larger. The job migrator currently writes only job
  rows; this would add a cross-migrator concern. The synthetic
  flow-node row also needs values the migrator can't naturally derive
  for a never-executed activity: scope key, tree path, end date,
  state.
- **Customer impact:** the activity appears in Operate's flow-node
  view as if it ran briefly. This is a fabrication — the activity
  never actually executed in C7.
- **Tradeoff:** preserves visibility in Operate, but invents a row
  that has no C7 source. A C7 user looking at the same instance in
  the old engine would see nothing; a C8 user would see a brief
  terminated activity. The mismatch may confuse triage.

### Option C — relax the C8 contract

Ask the C8 team to mark `elementInstanceKey` as nullable on
`JobEntity`. The migrator writes nothing new; the contract just stops
rejecting the null.

- **Cost:** zero migrator-side code. Long external calendar — depends
  on the C8 team's view.
- **Customer impact:** the job is preserved and visible, attached to
  no flow-node. Whatever views consume `elementInstanceKey` (Operate,
  Tasklist, downstream search clients) need to handle the null
  gracefully — which is C8-side UI work, not migrator-side.
- **Tradeoff:** preserves all the data without fabricating anything,
  but pushes the burden of "what to show when there's no flow-node"
  onto every consumer of the field rather than answering it once at
  migration time.

### Combinations are possible

Option A can ship today and unblock the contract test, while a
conversation about Option C happens in parallel. Option B is the only
one that preserves Operate-visibility *and* doesn't need the C8 team's
involvement, but it's also the only one that fabricates data.

## What's the right call?

This is a product/UX decision more than a code decision: **what should
Operate show for a failed async-before job whose activity never ran in
C7?**

- If the answer is *"nothing — this job isn't actionable"*, pick
  Option A.
- If the answer is *"the activity, marked terminated"*, pick Option B
  and accept the fabrication.
- If the answer is *"the job, unattached to any activity"*, pick
  Option C and accept the cross-team wait.

There is a related row in the same investigation —
`IncidentEntity.flowNodeInstanceKey` — that has the same root mechanic
(the same lookup misses for the same reason) but a different
surrounding code path. Whichever option is chosen here should be
discussed jointly with that row, so we don't end up with an incident
visible in Operate pointing at a job that's not.

## Where to look next

- **Technical detail (per-claim, with code citations):**
  [NULLABILITY-elementInstanceKey.md](../../data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-elementInstanceKey.md).
- **Status catalogue across all 12 null-write cases:**
  [NULLABILITY-status.md](../../data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-status.md).
- **Failing test:**
  `NullabilityContractTest.shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob`.
- **Open C7 question** (engine engineers welcome): is a
  `HistoricActivityInstance` ever recorded for an async-before job
  that fails before entering its activity?
