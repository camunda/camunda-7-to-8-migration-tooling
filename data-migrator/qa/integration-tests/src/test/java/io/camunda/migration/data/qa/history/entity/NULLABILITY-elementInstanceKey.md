# NullabilityContract — `JobEntity.elementInstanceKey`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Failing test: `NullabilityContractTest.shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob`
Related row in [NULLABILITY-status.md](./NULLABILITY-status.md): **#3**.

This doc follows the per-claim discipline from
[Investigations/_PROMPT-PREAMBLE.md](../Investigations/_PROMPT-PREAMBLE.md)
and [Investigations/03-JobEntity-elementInstanceKey.md](../Investigations/03-JobEntity-elementInstanceKey.md):
flat read inventory, verbatim quotes for every behavioural claim, `[FINDING]`
vs `[ASSUMPTION]` tagging, and a falsifier per finding.

## Read inventory

- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/JobMigrator.java:1-161` — contains the null write at lines 123-127 and the surrounding skip guards.
- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/HistoryEntityMigrator.java:317-369` — `findFlowNodeInstanceKey` lookup.
- `data-migrator/core/src/main/java/io/camunda/migration/data/HistoryMigrator.java:90-106` — migration ordering.
- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/FlowNodeMigrator.java:80-140` — to confirm `FlowNodeInstanceDbModel` is only inserted from a `HistoricActivityInstance`.
- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/IncidentMigrator.java:100-155` — to confirm the related (but out-of-scope) row #10 mechanic.
- `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:134-166` — Trigger B reproduction.
- `data-migrator/qa/integration-tests/src/test/resources/io/camunda/migration/data/bpmn/c7/asyncBeforeUserTaskProcess.bpmn:1-17` — fixture for Trigger B.
- `data-migrator/qa/e2e-tests/src/main/resources/process-example/miProcess-subprocess.bpmn:1-50` — fixture for Trigger A (`failingTask` async-before service task).
- `data-migrator/qa/e2e-tests/src/main/resources/seed-c7-test-data.sh:30-220` — e2e seed confirming the failingTask deployment and recorded null roll-up.
- `camunda/search/search-domain/src/main/java/io/camunda/search/entities/JobEntity.java:1-69` — confirms the C8 `requireNonNull` contract.
- `camunda/db/rdbms/src/main/resources/mapper/JobMapper.xml:223-252` — confirms no read-side coercion on `ELEMENT_INSTANCE_KEY`.
- `camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/mapper/JobEntityMapper.java:1-49` — confirms `JobDbModel → JobEntity` is a pass-through with no `nullToEmpty`-style coercion for `elementInstanceKey`.
- `camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/jobexecutor/AsyncContinuationJobHandler.java:40-91` — handler that runs on async-before job firing (read to bound the Trigger A C7 mechanism, **not** traced end-to-end — see open item).

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added a
`requireNonNull(elementInstanceKey, "elementInstanceKey")` to the
`JobEntity` compact constructor. The migrator writes `NULL` for that
column on one identifiable migration path, causing the C8 read side to
throw NPE.

## Root cause

`[FINDING]` The null write is at `JobMigrator.java:123-127`. The migrator
assigns `elementInstanceKey` only when `findFlowNodeInstanceKey` returns a
non-null value; otherwise the `JobDbModel.Builder` field is left at its
default null and the row is inserted with SQL `NULL`.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/JobMigrator.java:123
        Long elementInstanceKey = findFlowNodeInstanceKey(c7JobLog.getActivityId(), c7ProcessInstanceId,
            hasMultipleFlowNodes);
        if (elementInstanceKey != null) {
          builder.elementInstanceKey(elementInstanceKey);
        }
```

This is wrong if: an `else` branch or wrapping non-null default exists
outside the shown range (e.g. an interceptor in `convert(...)` later
substitutes the field), making the silent null impossible.

`[FINDING]` `findFlowNodeInstanceKey` returns null in three cases:
(a) the C7 process instance has not been migrated yet,
(b) the activity id ends with the `#multiInstanceBody` suffix (and
`hasMultipleFlowNodes` is flipped), or
(c) the C8 RDBMS search for `(activityId, processInstanceKey)` yields
no row and there's no multi-instance ambiguity in C7.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/HistoryEntityMigrator.java:340
  protected Long findFlowNodeInstanceKey(String activityId, String processInstanceId, AtomicBoolean hasMultipleFlowNodes) {
    Long processInstanceKey = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (processInstanceKey == null) {
      return null;
    }

    if (activityId.endsWith(C7_MULTI_INSTANCE_BODY_SUFFIX)) {
      // C8 flow node can't be determined
      hasMultipleFlowNodes.set(true);
      return null;
    }

    List<FlowNodeInstanceDbModel> flowNodes = c8Client.searchFlowNodeInstances(FlowNodeInstanceDbQuery.of(
        builder -> builder.filter(FlowNodeInstanceFilter.of(
            filter -> filter.flowNodeIds(activityId).processInstanceKeys(processInstanceKey)))));

    if (!flowNodes.isEmpty()) {
      // only some of the flow nodes might have been migrated at this point so first check how many entities are in C7
      var historicActivityInstances = c7Client.findHistoricActivityInstances(activityId, processInstanceId);
      if (historicActivityInstances != null && historicActivityInstances.size() > 1) {
        // C8 flow node can't be determined
        hasMultipleFlowNodes.set(true);
        return null;
      }
      if (flowNodes.size() == 1) {
        return flowNodes.getFirst().flowNodeInstanceKey();
      }
    }
    return null;
  }
```

This is wrong if: `dbClient.findC8KeyByC7IdAndType` or
`c8Client.searchFlowNodeInstances` is masked by an interface
implementation that synthesises a key when none exists.

`[FINDING]` Of the three downstream guards in `JobMigrator`, only the
multi-flow-node branch (line 144-146) and the async-after branch (line
149-151) catch a null `elementInstanceKey`. **Async-before with no
multi-instance ambiguity does not** — the null falls straight through to
`c8Client.insertJob(dbModel)` at line 153.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/JobMigrator.java:144
      if (hasMultipleFlowNodes.get() && dbModel.elementInstanceKey() == null) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE);
      }

      // For async-after jobs, element instance key is required
      if (isAsyncAfter && dbModel.elementInstanceKey() == null) {
        throw new C8EntityNotFoundException(HISTORY_FLOW_NODE, dbModel.processInstanceKey(), c7JobLog.getActivityId());
      }

      c8Client.insertJob(dbModel);
```

This is wrong if: a later wrapper around `c8Client.insertJob` rejects
null `elementInstanceKey` before the DB write, or a MyBatis-side
`NOT NULL` constraint on `ELEMENT_INSTANCE_KEY` would surface the null
as a constraint violation rather than as a silent persisted NULL.

`[FINDING]` Migration ordering is **already** correct in production —
`flowNodeMigrator` runs before `jobMigrator`, so by the time
`JobMigrator` calls `findFlowNodeInstanceKey`, every C7
`HistoricActivityInstance` that exists has had a chance to be migrated.
The bug is therefore **not** an ordering race; it is "no C7 source row,
therefore no C8 target row, therefore the lookup misses".

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/HistoryMigrator.java:90
  protected List<HistoryEntityMigrator<?, ?>> getMigrators() {
    return List.of(
        formMigrator,
        processDefinitionMigrator,
        processInstanceMigrator,
        flowNodeMigrator,
        userTaskMigrator,
        variableMigrator,
        jobMigrator,
        externalTaskMigrator,
        incidentMigrator,
        decisionRequirementsMigrator,
        decisionDefinitionMigrator,
        decisionInstanceMigrator,
        auditLogMigrator
    );
  }
```

This is wrong if: production code paths invoke migrators outside
`getMigrators()`. (`HistoryMigrator.migrateByType` exists and runs a
single migrator, but the production `migrate()` and `retry()` calls both
go through `getMigrators()` at lines 172 and 156.)

`[FINDING]` `FlowNodeMigrator` only constructs a
`FlowNodeInstanceDbModel` inside `migrateTransactionally(HistoricActivityInstance ...)`
— i.e. it requires a C7-side `HistoricActivityInstance` to exist.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/FlowNodeMigrator.java:80
  @Override
  public MigrationResult migrateTransactionally(HistoricActivityInstance c7FlowNode) {
    var c7FlowNodeId = c7FlowNode.getId();
    if (shouldMigrate(c7FlowNodeId, HISTORY_FLOW_NODE)) {
      HistoryMigratorLogs.migratingHistoricFlowNode(c7FlowNodeId);

      var flowNodeInstanceKey = getNextKey();
      var builder = new FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder();
      builder.flowNodeInstanceKey(flowNodeInstanceKey);
```

This is wrong if: another migrator inserts a `FlowNodeInstanceDbModel`
synthetically. `grep -rn "insertFlowNodeInstance"` over the migrator
package would confirm whether `FlowNodeMigrator` is the sole writer.

`[FINDING]` The C8 read path turns the persisted NULL into a NPE-on-read.
The MyBatis mapper materialises `elementInstanceKey` as a plain
`java.lang.Long` with no type handler:

```xml
<!-- camunda/db/rdbms/src/main/resources/mapper/JobMapper.xml:230 -->
    <result column="ELEMENT_INSTANCE_KEY" property="elementInstanceKey" javaType="java.lang.Long" />
```

The `JobDbModel → JobEntity` translator passes the value through
unchanged (no `nullToEmpty`-style call):

```java
// camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/mapper/JobEntityMapper.java:43
        .elementInstanceKey(jobDbModel.elementInstanceKey())
```

And the `JobEntity` compact constructor calls `requireNonNull`:

```java
// camunda/search/search-domain/src/main/java/io/camunda/search/entities/JobEntity.java:61
    requireNonNull(elementInstanceKey, "elementInstanceKey");
```

This is wrong if: a different `JobDbReader` path bypasses
`JobEntityMapper.toEntity` (streaming query, in-memory cache, alternative
reader bean), or a globally registered MyBatis `TypeHandler<Long>`
substitutes 0 for NULL.

## When the lookup returns null in production

### Trigger A — async-before job whose activity never executed

The e2e seed (`miProcess-subprocess.bpmn` + `seed-c7-test-data.sh`)
includes an async-before service task `failingTask` with
`camunda:class="no-op"` (a class that doesn't resolve). The job fails on
every attempt, retries hit zero.

```xml
<!-- data-migrator/qa/e2e-tests/src/main/resources/process-example/miProcess-subprocess.bpmn:37 -->
    <bpmn:serviceTask id="Activity_052wot9" name="failingTask" camunda:asyncBefore="true" camunda:class="no-op">
```

Verified e2e result (`NULLABILITY-status.md:91`):
`job.element_instance_key null` = 1 row — matches the single
`failingTask` job in the seed.

`[ASSUMPTION]` For Trigger A, C7 records **no** `HistoricActivityInstance`
for the `failingTask` activity (only a `HistoricJobLog` with the failure
attempts). The verified e2e roll-up is consistent with this — given that
`FlowNodeMigrator` only creates a `FlowNodeInstanceDbModel` from a
`HistoricActivityInstance` and the C8 `job.element_instance_key` shows
NULL after a full migration, the simplest explanation is "no C7 source
row".

To upgrade to `[FINDING]`: trace
`AsyncContinuationJobHandler.execute → performOperation(ACTIVITY_START_CREATE_SCOPE)`
through the `ActivityInstanceStartListener` and the
`HistoryEventHandler` flush boundary, and determine whether the
`ACTIVITY_INSTANCE_START` event is committed before, or rolled back with,
the failing activity behavior. The `camunda-7-investigation` skill is the
right tool. Not done in this pass — the resolution choice between Option
3a (skip) and Option 4 (negotiate) does not actually depend on this fact,
but Option 3b (synthesize FNI) does.

### Trigger B — synthetic, partial migration (failing contract test)

`NullabilityContractTest.shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob`
deliberately calls `migrateByType(HISTORY_PROCESS_DEFINITION / HISTORY_PROCESS_INSTANCE / HISTORY_JOB)`
**without** `HISTORY_FLOW_NODE`. The async-before user task in
`asyncBeforeUserTaskProcess.bpmn` does execute successfully in C7
(`managementService.executeJob(...)` is called), so a
`HistoricActivityInstance` exists in C7 — but the test stops the migrator
from copying it to C8. The lookup then misses for the same code-path
reason as Trigger A.

```java
// data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:138
  public void shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob() {
    // given: an async-before job (flow nodes NOT migrated)
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    managementService.executeJob(jobs.getFirst().getId());

    // when: migrate jobs WITHOUT migrating flow nodes
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_JOB);
```

This is a **contract test, not a production reproduction**. It pins the
behaviour of the silent-null path so a future regression that changes
migration ordering or skips flow nodes is caught immediately.

`[FINDING]` The async-before user task in the fixture has no failing
behavior — it is a vanilla `userTask` with `camunda:asyncBefore="true"`:

```xml
<!-- data-migrator/qa/integration-tests/src/test/resources/io/camunda/migration/data/bpmn/c7/asyncBeforeUserTaskProcess.bpmn:12 -->
    <bpmn:userTask id="asyncUserTaskId" name="Async User Task" camunda:asyncBefore="true">
```

This is wrong if: an implicit `camunda:class` or task-listener attribute
elsewhere in the file makes the job fail (the file is 17 lines; the
quoted snippet is the entire `userTask` element).

## Data in scope at the write point

`[FINDING]` At `JobMigrator.java:123-127`, when `findFlowNodeInstanceKey`
returns null, the migrator has already resolved or has access to:

- `c7JobLog.getActivityId()` — known non-null because
  `findFlowNodeInstanceKey` invokes `.endsWith(...)` on it at
  `HistoryEntityMigrator.java:346` with no preceding null guard; a null
  would NPE before line 123 returns.
- `c7ProcessInstanceId` and the resolved
  `processInstance.processInstanceKey()` (set on the builder at line
  112).
- `processDefinitionKey` from
  `findProcessDefinitionKey(c7JobLog.getProcessDefinitionId())` at line
  107, asserted non-null at line 132-134.
- `partitionId` and `rootProcessInstanceKey` if the root PI was migrated
  (lines 114-121).
- `jobKey` — freshly minted from `getNextKey()` at line 104.
- The full `HistoricJobLog` via `C7Entity.of(c7JobLog)` — all C7 log
  fields including `jobDefinitionConfiguration`, `jobDefinitionType`,
  `jobExceptionMessage`.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/JobMigrator.java:104
      var jobKey = getNextKey();
      var builder = new JobDbModel.Builder().jobKey(jobKey);

      var processDefinitionKey = findProcessDefinitionKey(c7JobLog.getProcessDefinitionId());
      builder.processDefinitionKey(processDefinitionKey);
      String c7ProcessInstanceId = c7JobLog.getProcessInstanceId();
      ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      if (processInstance != null) {
        builder.processInstanceKey(processInstance.processInstanceKey());
```

This is wrong if: `c7JobLog.getActivityId()` can be null in some scenario
— that would surface as a NPE in `HistoryEntityMigrator.java:346` before
the null-write path, making the bug class broader, not narrower.

`[FINDING]` What the migrator does **not** have at this point: a
`flowNodeInstanceKey` from a migrated FNI row. Minting one via
`getNextKey()` is mechanically possible but would create an orphan key
unless a matching `FlowNodeInstanceDbModel` is also inserted. Doing the
latter requires picking `flowNodeScopeKey`, `treePath`, `endDate`,
`state`, `rootProcessInstanceKey`, and `partitionId`, and the
`FlowNodeMigrator` guards illustrate that these are not trivial defaults:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/FlowNodeMigrator.java:122
      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7FlowNode, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.flowNodeScopeKey() == null) {
        throw new EntitySkippedException(c7FlowNode, SKIP_REASON_MISSING_PARENT_FLOW_NODE);
      }
```

This is wrong if: the C8 RDBMS allows `FLOW_NODE_INSTANCE` rows with
`flowNodeScopeKey == NULL` — the guard could be migrator-side overcaution
rather than a DB constraint. C8 DDL not read in this pass; either way, a
synthetic-FNI fix is not a one-line `JobMigrator` tweak.

## Option analysis

The five-option vocabulary is defined in
[`NULLABILITY-resolution.md`](./NULLABILITY-resolution.md). Per-row
viability:

**Option 1 — do nothing.**
`[FINDING]` Not viable. The C8 read path will NPE on every search hitting
an affected row.

This is wrong if: the failing-test assertion at
`NullabilityContractTest.java:163` (`isNotNull()`) catches the absence of
the field rather than a constructor-thrown NPE — but the production
impact is NPE either way; the test shape doesn't change the conclusion.

**Option 2 — canary test.**
`[FINDING]` Not viable. There is no foreign-side coercion masking the
null (`JobMapper.xml:230` declares no type handler;
`JobEntityMapper.java:43` is a straight pass-through). Nothing to pin.

This is wrong if: a globally registered MyBatis `TypeHandler<Long>` is
configured elsewhere in the C8 RDBMS module — not searched in this pass.

**Option 3a — skip the job.**
`[FINDING]` ~1-3 lines. Either extend the async-after guard at
`JobMigrator.java:149` to also fire for async-before, or add a parallel
async-before branch using the already-imported `EntitySkippedException`
and `SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE` constant. No new imports.
The failed-async-before job disappears from C8 history, but is recorded
in the migrator's skip table with a reason.

This is wrong if: silently dropping a failed-async-before job is itself
blocked by another invariant — e.g. an `AuditLogMigrator` row referencing
the C7 job id by FK — that I haven't surveyed.

**Option 3b — synthesize a `FlowNodeInstanceDbModel`.**
`[FINDING]` Substantially larger. `JobMigrator` would need to mint a
`flowNodeInstanceKey`, populate it on the job, and also insert a matching
`FlowNodeInstanceDbModel` via `c8Client.insertFlowNodeInstance(...)` —
which crosses a module boundary `JobMigrator` does not cross today
(`JobMigrator extends HistoryEntityMigrator<HistoricJobLog, JobDbModel>`,
not `FlowNodeInstanceDbModel`). The synthetic row needs values for
`flowNodeScopeKey`, `treePath`, `endDate`, `state`, etc. — none of which
have a natural source for the "never executed" case. Introduces a
"synthetic flow node" concept that needs Operate-side rendering review.

This is wrong if: an existing helper already inserts synthetic FNI rows
(none surfaced in my read of `FlowNodeMigrator` / `HistoryEntityMigrator`).

**Option 4 — negotiate with C8.**
`[FINDING]` Zero migrator-side code. The change lands on
`JobEntity.java:61` (drop `requireNonNull(elementInstanceKey, ...)`, mark
`@Nullable Long elementInstanceKey`). Long external calendar; depends on
whether C8 considers a job without a flow-node attachment to be a
legitimate entity shape.

This is wrong if: a downstream C8 consumer (Operate / Tasklist / search
REST API) unconditionally dereferences `elementInstanceKey` and would
NPE one layer deeper — that would mean Option 4 alone is insufficient
and needs parallel C8-side null-safe rendering. C8 consumers not
surveyed in this pass.

**Option 5 — skip rows with null values.**
`[FINDING]` Convergent with Option 3a. No distinct fifth path here.

This is wrong if: "skip the row" is interpreted as "drop after write"
rather than "don't insert" — the migrator only has the "don't insert"
shape today, so the distinction is moot.

## User-facing impact

`[FINDING]` Today: the C8 search API throws NPE at `JobEntity.java:61`
for every job row whose persisted `ELEMENT_INSTANCE_KEY` is NULL. Operate's
job list and process-instance job tab read through this path; one bad row
in a page blocks the page. Observable via the enabled contract test
`NullabilityContractTest.shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob`,
which fails in CI today.

```java
// data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:161
    assertThat(c8Jobs).hasSize(1);
    // C8 contract: elementInstanceKey must not be null
    assertThat(c8Jobs.getFirst().elementInstanceKey())
        .as("JobEntity.elementInstanceKey — C8 requires non-null (requireNonNull in compact constructor)")
        .isNotNull();
```

This is wrong if: the failing assertion actually reports the
constructor-thrown NPE rather than the AssertJ message — the user-facing
result is NPE either way; the test transcript shape is the only thing
that changes.

**Option 3a (skip):** the failed async-before job is omitted from C8
history. Recorded in the migrator's skip log with a reason. Customer
impact: "I can't see in Operate that this job failed migration of an
activity that never ran in C7"; in exchange, *all other* jobs in the
process instance remain readable. The loss is observable in the skip log.

**Option 3b (synthesize FNI):** the activity appears in Operate as a
flow node that started and immediately terminated. Whether that is
"right" or "misleading" is a product call — there genuinely was no
activity execution in C7, so the synthetic row is a fiction.

**Option 4 (allow null in contract):** the job is preserved in C8 with
real-but-null `elementInstanceKey`; Operate / consumers must handle jobs
unattached to any flow node. Requires UI work on the C8 side.

The choice between 3a, 3b, and 4 is a product/UX decision (what *should*
Operate show for a failed async-before job whose activity never ran?),
not a code-mechanics decision. Flag for design review before
implementing.

## Discrepancies vs `NULLABILITY-resolution.md` and `NULLABILITY-status.md`

`[FINDING]` `NULLABILITY-resolution.md:116-124` (Row 3 rationale)
references "`AsyncContinuationMigrator`" — class does not exist.
`grep -rn "AsyncContinuationMigrator"` over the repo returns zero hits.
The relevant class is `JobMigrator`.

This is wrong if: the class was renamed to `JobMigrator` and the doc
predates the rename — even then, the doc should be updated.

`[FINDING]` `NULLABILITY-resolution.md:116-124` frames the fix as "an
ordering change". Production ordering is already correct
(`HistoryMigrator.getMigrators()` at line 95 = `flowNodeMigrator`,
line 98 = `jobMigrator`). The null is **not** an ordering bug; it is a
data-availability bug (no C7 source row → no C8 target row → lookup
misses).

This is wrong if: an alternate non-default ordering exists in
configuration (none surfaced in `HistoryMigrator`).

`[FINDING]` `NULLABILITY-status.md:30` Row 3 note ("Async-before job
created before flow node instance exists") is misleading. It reads as a
*timing* issue. The actual mechanic, for Trigger A, is "the C7
`HistoricActivityInstance` never exists, therefore the C8 flow node row
never exists, therefore the lookup misses" — a data-availability issue.
Minor — flag for cleanup.

This is wrong if: the note is intended to describe Trigger B (partial
migration) rather than Trigger A — but the surrounding context in the
status doc indicates the production trigger.

`[FINDING]` Row 10 (`IncidentEntity.flowNodeInstanceKey`) shares the
`findFlowNodeInstanceKey`-miss mechanic but uses a *different* skip-vs-
allow gate (`c7Client.hasWaitingExecution` at
`IncidentMigrator.java:137`). So Row 3 and Row 10 can share a *C8-team
conversation* but not a single *code fix* — the skip predicate differs.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/IncidentMigrator.java:129
      if (dbModel.flowNodeInstanceKey() == null) {
        if (hasMultipleFlowNodes.get()) {
          // ...
          throw new EntitySkippedException(c7Incident, SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE);
        }
        // Activities on async before waiting state will not have a flow node instance key, but should not be skipped
        if (!c7Client.hasWaitingExecution(c7Incident.getProcessInstanceId(), c7Incident.getActivityId())) {
          throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_FLOW_NODE);
        }
      }
```

This is wrong if: the design conclusion is to converge on a single
gate-shape for both migrators — in which case the code fix becomes
shared by design, not by coincidence.

## Open items to confirm before Phase C

1. **C7 mechanism behind Trigger A.** Upgrade the `[ASSUMPTION]` above to
   a `[FINDING]` if Option 3b or Option 4 is chosen. Read path:
   `AsyncContinuationJobHandler.execute` →
   `performOperation(ACTIVITY_START_CREATE_SCOPE)` →
   `ActivityInstanceStartListener` → `HistoryEventHandler` flush
   boundary. Tool: `camunda-7-investigation` skill, or a focused
   integration test asserting on
   `historyService.createHistoricActivityInstanceQuery()` count after the
   `failingTask` scenario.
2. **Product decision.** Pick between 3a / 3b / 4 — what should Operate
   show for a failed async-before job whose activity never ran?
3. **Resolve jointly with Row 10.** The two rows share a root mechanic
   but not a code fix. Phase C for Row 3 should be paired with a
   Phase B/C for Row 10 so a coherent answer covers both — but the
   shared part is the *C8 conversation*, not the migrator-side patch.
4. **Survey C8 consumers for `elementInstanceKey` dereference.**
   Required only if Option 4 is chosen. Determines whether C8-side UI
   work is needed alongside the contract relaxation.
