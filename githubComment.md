# Nullability cases — proposed resolution

## Option vocabulary

For each scenario, five resolution paths are available:

1. **Do nothing** — accept the current state. Defensible only when the case
   is non-failing today *and* the dependency on upstream behavior is judged
   acceptable.
2. **Add a canary test** — pin foreign-side coercion (LEFT JOIN hydration,
   `NullToEmptyStringTypeHandler`) so a future C8-side regression surfaces
   as a clear failure in this suite rather than as a cryptic NPE downstream.
3. **Set value at Migrator-side** — write a sensible non-null value at migration
   time (either a placeholder or actual value), removing any dependency on read-side coercion or upstream contract
   changes. The migrator owns the invariant.
4. **Negotiate with C8 API team** — request a sanctioned placeholder, a
   relaxed nullability annotation, or a partial-retrieval mode. Long horizon;
   requires cross-team alignment.
5. **Skip migrating rows with null values** — omit the row from the
   migration entirely rather than writing it with a null. Loses data but
   avoids fabricating values; defensible when the row is meaningless
   without the missing field.

The options are not mutually exclusive. Option 4 can run in parallel
with Option 3 as a validation pass ("we plan to substitute *X*; please
confirm").

## Per-case decision

✅     marks the proposed primary option for each scenario.

| #  | Field                                             | Status                         | 1: Do nothing | 2: Canary test | 3: Set value in Migrator | 4: Negotiate w/ C8 | 5: Skip row |
|----|---------------------------------------------------|--------------------------------|---------------|----------------|--------------------------|--------------------|-------------|
| 1  | `JobEntity.worker`                                | Real bug                       |               |                | ✅                       |                    |             |
| 2  | `JobEntity.lastUpdateTime`                        | Real bug                       |               |                | ✅                       |                    |             |
| 3  | `JobEntity.elementInstanceKey`                    | Real bug                       |               |                | ?                        | ?                  | ?           |
| 4  | `AuditLogEntity.entityKey` (JOB)                  | Real bug                       |               |                | ?                        | ?                  |             |
| 5  | `AuditLogEntity.entityKey` (EXTERNAL_TASK)        | Real bug                       |               |                | ?                        | ?                  |             |
| 6  | `DecisionDefinitionEntity.decisionRequirementsId` | Real bug                       |               |                | ✅                       |                    |             |
| 7  | `DecisionInstanceEntity.decisionDefinitionName`   | Non-issue in RDBMS             | ✅            |                |                          |                    |             |
| 8  | `DecisionInstanceEntity.result`                   | Masked                         |               |                | ✅                       |                    |             |
| 9  | `DecisionInstanceEntity.decisionDefinitionType`   | Unreachable via normal C7 path | ✅            |                |                          |                    |             |
| 10 | `IncidentEntity.flowNodeInstanceKey`              | Real bug                       |               |                | ?                        | ?                  | ?           |
| 11 | `IncidentEntity.errorMessage`                     | Masked                         |               |                | ✅                       |                    |             |
| 12 | `AuditLogEntity.entityKey` (general)              | Real bug                       |               |                | ?                        | ?                  | ?           |

## Summary / next steps

| Status                                                      | Rows                                                         |
|-------------------------------------------------------------|--------------------------------------------------------------|
| Selected resolution paths with open implementation question | 2, 6, 8, 11                                                  |
| Selected resolution paths, ready for implementation         | 1                                                            |
| Decided, no implementation                                  | 7, 9                                                         |
| Decision needed, root cause HistoricActivityInstance not persisted in C7 | 3 (`JobEntity.elementInstanceKey`) & 10 `IncidentEntity.flowNodeInstanceKey` |
| Decision needed, root cause `AuditLogEntity.entityKey`.  | 4, 5, 12 |


## Questions concerns
- Should we back-port changes to 0.3.x?
  - Doing so would change behaviour in a way that might not be expected by the user. But would enable us to ship new features in 0.3. 
  - Not doing so would risk people migrating data that is not compatible with the 8.9 -> 8.10 migration route. 
- If a customer is using data migrator 0.3.0 to migrate they might have issue with the 8.9 -> 8.10 migration route.

## Implementation sub-decisions

Sub-decision candidates for the 4 cases marked "open implementation question" above (Rows 2, 6, 8, 11).

| Case                                              | Implementation option                                   | Pros                                          | Cons                                           | Time         |
|---------------------------------------------------|---------------------------------------------------------|-----------------------------------------------|------------------------------------------------|--------------|
| `JobEntity.lastUpdateTime`                        | A: use `creationTime`                                   | quick; no extra C7 query                      | not the actual last lifecycle event            | half a day   |
|                                                   | B: fetch latest log entry per job                       | semantically accurate                         | extra C7 query per migrated job                | 1-2 days     |
| `DecisionDefinitionEntity.decisionRequirementsId` | A: transformer-side using `C7Client`                       | smaller change (~5 lines)                     | adds `C7Client` dependency to transformer      | half a day   |
|                                                   | B: migrator-side: return DRD id from `migrateSyntheticDrd` | cleaner ownership boundary                    | larger change (~10 lines, two files)           | 1 day        |
| `DecisionInstanceEntity.result`                   | A: `"null"` (4-char JSON literal)                          | matches single-rule output; lowest surprise   |  | half a day   |
|                                                   | B: `"[]"`                                                  | semantically "no result rows"                 | no existing migrator output uses this shape    | half a day   |
|                                                   | C: `""` (empty string)                                     | aligns with read-side coercion                | blank pane in Operate's Result tab             | half a day   |
| `IncidentEntity.errorMessage`                     | A: `""` (empty string)                                     | aligns with current masking behavior          | loses null vs empty C7-message distinction     | half a day   |
|                                                   | B: `"<no message>"`                                        | explicit human-readable sentinel              | new convention, not used elsewhere             | half a day   |

Time estimates are rough — code-change effort only, doesn't include review / testing / deployment.

## Per-case rationale

### Row 1 — `JobEntity.worker`

<details>
<summary><b>Row 1 — <code>JobEntity.worker</code></b></summary>

#### Root cause
Two transformers feed `JobEntity.worker`. `ExternalTaskTransformer.java:65`
hardcodes `.worker(null)`. `JobTransformer.java:58` passes through
`historicJobLog.getHostname()`, which can also be null in edge cases
(custom `HostnameProvider`, legacy DB rows from older C7 versions).
The property `worker` is a string referring to the machine name where the job had run. The place holder does not need to adhere to any rules. Eg `MIGRATOR_PLACEHOLDER` is fine. For this reason `skipping` or `Negotiating with C8 ` was not considered

#### Fix **Option 3.**

Fix: replace each `.worker(...)` call with a null-safe fallback to a stable
placeholder. Apply to both transformers so both null paths satisfy the C8
contract.

</details>

### Row 2 — `JobEntity.lastUpdateTime`

<details>
<summary><b>Row 2 — <code>JobEntity.lastUpdateTime</code></b></summary>

#### Root cause

The migrator never sets `lastUpdateTime` on any code path that writes a
`JobDbModel`. Both transformers (`JobTransformer.java:53-66` for regular
jobs, `ExternalTaskTransformer.java:54-72` for external tasks) and both
migrators leave the field as the builder's default null, which the MyBatis
insert maps to SQL NULL.

#### Solution set value in (Option 3)
- A, **`lastUpdateTime = creationTime`.** ~2 lines per
  transformer. Caveat: that timestamp is the
  *earliest* C7 log entry's, not the actual last lifecycle event.
- B, **fetch the latest log entry per job.** Extend
  `C7Client` with a `DESC`-ordered single-result variant of
  `getHistoricJobLog` (and the external-task counterpart), source
  `lastUpdateTime` from it. ~20 lines + one extra C7 round trip per
  migrated job. The migrator currently reads only the earliest log per
  job (`HistoryEntityMigrator.java:375-380` skips already-tracked IDs). Semantic gain:
  `lastUpdateTime` reflects the real last C7 lifecycle event (failure,
  success, deletion) rather than collapsing onto creation time.
- C **Option 3c — `Instant.now()`.** Misleading; encodes
  migration time, not anything about the C7 job.

<details> <summary> **Options considered:** </summary>

- **Option 1 — do nothing.** Rejected. Every C8 job search of a migrated
  row NPEs at the entity constructor.
- **Option 2 — canary test.** Rejected. No read-side coercion to pin —
  nothing is silently masked.
- **Option 4 — negotiate with C8.** Ask the team to mark
  `lastUpdateTime` `@Nullable`. Defensible (a migrated job is a snapshot)
  but long calendar.
- **Option 5 — skip rows.** Equivalent to "skip every migrated job".
  Not viable.

</details> 

**Product/UX question deferred:** is lastUpdateTime used in any user facing surface? 

</details>

### Row 3 — `JobEntity.elementInstanceKey`

<details>
<summary><b>Row 3 — <code>JobEntity.elementInstanceKey</code></b></summary>

**Decision pending — Options 3, 4, and 5 under consideration.**

The migrator writes null at `JobMigrator.java:123-127` when
`findFlowNodeInstanceKey` returns null. The lookup misses in case C7 has
no `HistoricActivityInstance` for the activity due to an async-before
service task that fails on every retry.

**Options under consideration:**


- **Option 3 a — synthesize an FNI.** Mint a `flowNodeInstanceKey` on the
  job and insert a matching `FlowNodeInstanceDbModel`. Introduces a
  "synthetic FNI for never-entered activity" concept; Operate's flow-node
  view must render an activity with no real entry/exit. Choose values for
  `flowNodeScopeKey`, `treePath`, `endDate`, `state` that have no C7
  source.
- **Option 3 b — insert place holder value** If the `flowNodeInstanceKey` is not a FK in the DB then generating a placeholder value could offer a simple solution. 
Is there a safe value that can be used? Probably needs syncing with C8 as `flowNodeInstanceKey` might be used in Operate or different services. 
- **Option 4 — negotiate with C8.** Ask the team to mark
  `elementInstanceKey` `@Nullable` for jobs whose activity never executed.
- **Option 5 — skip the job.** Loses the failed async-before job
  from C8 history; the skip is recorded with a reason in the migrator's
  skip log.

**Joint with Row 10:** the `findFlowNodeInstanceKey`-miss is shared, but
the surrounding gate differs (`isAsyncAfter` vs `hasWaitingExecution`).
The two rows share a C8-team conversation, not a single mechanical code
fix.

**Open product/UX question:** what should Operate show for a failed
async-before job whose activity never executed in C7?

</details>

### Row 4 — `AuditLogEntity.entityKey` (JOB)

<details>
<summary><b>Row 4 — <code>AuditLogEntity.entityKey</code> (JOB)</b></summary>

The JOB instance of the same gap that Row 12 covers. Treated together with
Rows 5 and 12 under [Row 12](#row-12--auditlogentityentitykey-jobexternal_taskgeneral) below — that's where the fix lives.

</details>

### Row 5 — `AuditLogEntity.entityKey` (EXTERNAL_TASK)

<details>
<summary><b>Row 5 — <code>AuditLogEntity.entityKey</code> (EXTERNAL_TASK)</b></summary>

The EXTERNAL_TASK instance of the same gap that Row 12 covers. Treated together
with Rows 4 and 12 under [Row 12](#row-12--auditlogentityentitykey-jobexternal_taskgeneral) below — that's where the fix lives.

</details>

### Row 6 — `DecisionDefinitionEntity.decisionRequirementsId`

<details>
<summary><b>Row 6 — <code>DecisionDefinitionEntity.decisionRequirementsId</code></b></summary>

**Option 3.**

`DecisionDefinitionTransformer.java:35` calls
`prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey())`, which
returns null for standalone DMNs (no parent DRD on the C7 side). The migrator
already creates a synthetic DRD row for these decisions via
`migrateSyntheticDrd` in `DecisionDefinitionMigrator.java:87-94`, with a
well-formed `decisionRequirementsId` of `"c7-legacy-<definitionsId>"`. Only
the foreign-key reference from the decision row to the synthetic DRD row is
missing.

Fix: populate `decisionRequirementsId` on the decision row to match the
synthetic DRD's id when `getDecisionRequirementsDefinitionKey()` is null.

The question "does it make sense to link the synthetic DRD in Operate?" was
considered. The synthetic DRD row already exists in C8 today regardless of
this fix; any rendering question pre-dates this change. The fix just makes
the foreign-key reference consistent with what's already there — UX is a
non-blocking sub-decision worth confirming with product but not gating the
change.

**Implementation sub-decision:** transformer-side branch using `C7Client`
(~5 lines) or migrator-side ownership returning the DRD id from
`migrateSyntheticDrd` (~10 lines, cleaner boundary). Both are valid Option 3
variants.

</details>

### Row 7 — `DecisionInstanceEntity.decisionDefinitionName`

<details>
<summary><b>Row 7 — <code>DecisionInstanceEntity.decisionDefinitionName</code></b></summary>

#### Non-issue - do nothing
Reason: There is no decisionDefinitionName stored in the DecisionInstance table when using RDBMS. 

Context: 
DecisionInstanceEntity.decisionDefinitionName has been nullable on the API entity instantiation level in the past. 
This has changed. Now it is not nullable. 
The data migrator uses the RDBMS writer to create a DecisionInstance row. The DecisionInstance table does not store the decisionDefinitionName.
When the client API initializes the DecisionInstanceEntity the decisionDefinitionName is queried using a JOIN on the DecisionDefinition table. 
The non-nullable constraint might make a difference in OS/ES but that is not in scope.

</details>

### Row 8 — `DecisionInstanceEntity.result`

<details>
<summary><b>Row 8 — <code>DecisionInstanceEntity.result</code></b></summary>

The migrator does write RESULT = NULL for a no-match COLLECT decision instance. The C8 search API does not NPE on it because the RDBMS read path applies NullToEmptyStringTypeHandler (same as row 11 `IncidentEntity.errorMessage`). 

<details> <summary> code </summary>

``` java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionInstanceTransformer.java:60-86
public void execute(HistoricDecisionInstance entity, Builder builder) {
  var evaluatedOutputs = mapOutputs(entity, entity.getOutputs());

  String resultJsonString;
  var collectResultValue = entity.getCollectResultValue();
  if (collectResultValue != null) {
    resultJsonString = constructResultFromCollectValue(collectResultValue);
  } else {
    resultJsonString = constructResultJsonFromOutputs(evaluatedOutputs);
  }
  …
  .result(resultJsonString)
```

``` java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionInstanceTransformer.java:173-176
protected String constructResultJsonFromOutputs(List<EvaluatedOutput> outputValues) {
  if (outputValues == null || outputValues.isEmpty()) {
    return null;
  }
```

</details>

**Option 3.**

The `""` coercion also produces a worse user-facing outcome: Operate's
Decision Instance "Result" tab renders `""` as a blank read-only editor
pane — its `?? '{}'` fallback only triggers on null/undefined, not on
`""`. Writing a meaningful JSON literal at the migrator removes both the
contract masking and the blank-pane issue.

**Placeholder value to consider:**.
- `"null"` (the 4-char JSON literal) — matches what
  `objectMapper.writeValueAsString(null)` already produces in the
  single-rule path of `constructResultJsonFromOutputs`. Lowest-surprise
  choice for downstream consumers.
- `"[]"` — semantically "no result rows", but doesn't match any
  existing migrator output shape.
- `""` — aligns with current read-side coercion but preserves the
  blank-pane UX. Not preferred.

Worth bundling the placeholder question with the Row 11 conversation (same
masking, same shape of choice).

</details>

### Row 9 — `DecisionInstanceEntity.decisionDefinitionType`

<details>
<summary><b>Row 9 — <code>DecisionInstanceEntity.decisionDefinitionType</code></b></summary>

#### Non-issue - do nothing
Even though a code branch that returns null `decisionDefinitionType` is present in `DecisionInstanceMigrator.determineDecisionType`
(`DecisionInstanceMigrator.java:246`) it is unreachable in any normal C7 deployment.
At deploy time, the engine sets each `DecisionDefinitionEntity.key` to the `id`
attribute of the parsed `<decision>` element (`DmnDecisionTransformHandler.java:33`),
so `getDmnModelInstance(definitionId).getModelElementById(key)` always resolves.
If the decision definition no longer exists, `getDmnModelInstance` throws rather
than returning a model without it. Camunda 7 is in maintenance, so the invariant
is stable and not worth a defensive shim.

</details>

### Row 10 — `IncidentEntity.flowNodeInstanceKey`

<details>
<summary><b>Row 10 — <code>IncidentEntity.flowNodeInstanceKey</code></b></summary>

**Decision pending — Options 3, 4, and 5 under consideration.**

The history incident migrator explicitly migrates incident without flowNodeInstanceKey
in data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/IncidentMigrator.java
```
      if (dbModel.flowNodeInstanceKey() == null) {
        if (hasMultipleFlowNodes.get()) {
          // Multi-instance activities produce multiple flow nodes for the same activityId within a process
          // instance, making it impossible to deterministically resolve the correct flow node for this
          // incident. Skip to avoid wrong associations. See https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103
          throw new EntitySkippedException(c7Incident, SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE);
        }
        // Activities on async before waiting state will not have a flow node instance key, but should not be skipped
        if (!c7Client.hasWaitingExecution(c7Incident.getProcessInstanceId(), c7Incident.getActivityId())) {
          throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_FLOW_NODE);
        }
      }
```

**Options under consideration:**

- **Option 3 — synthesize an FNI.** Mint a `flowNodeInstanceKey`, insert
  a matching `FlowNodeInstanceDbModel`. Introduces a "synthetic FNI for
  never-entered activity" concept; Operate's flow-node view must render
  an activity with no real entry/exit.

- **Option 3 b — insert place holder value** If the `flowNodeInstanceKey` is not a FK in the DB then generating a placeholder value could offer a simple solution. 
Is there a safe value that can be used? Probably needs syncing with C8 as `flowNodeInstanceKey` might be used in Operate or different services. 
  
- **Option 4 — negotiate with C8.** Ask the team to mark
  `flowNodeInstanceKey` `@Nullable` for incidents on unentered activities.
- **Option 5 — skip rows with null values.** Loses a live in-flight async-before
  incident.

**Joint with Row 3:** the `findFlowNodeInstanceKey`-miss mechanic is shared
with `JobEntity.elementInstanceKey` (Row 3). Whichever option is chosen here
should be consistent with Row 3's.

**Open product/UX question:** what should Operate show for a live in-flight
incident attached to an async before activity that is never persisted in C7?

</details>

### Row 11 — `IncidentEntity.errorMessage`

<details>
<summary><b>Row 11 — <code>IncidentEntity.errorMessage</code></b></summary>

**Option 3.

The migrator currently writes SQL NULL when C7's
`HistoricIncident.getIncidentMessage()` is null; the C8 read side masks this
via `NullToEmptyStringTypeHandler` on the `ERROR_MESSAGE` column, so the
field arrives at the `IncidentEntity` constructor as `""` and `requireNonNull`
is silently satisfied. The masking is invisible unless the type handler is
removed.

Option 3 (write a placeholder value at the migrator) is the best answer because:

- **Cost is negligible.** ~3 lines in `IncidentMigrator`.
- **No semantic risk.** Empty error message is data a customer can already
  produce; no downstream consumer can distinguish `""` from SQL NULL via
  the search API (the type handler already coerces them identically).
- **Removes a foreign dependency.** The migrator no longer needs
  `NullToEmptyStringTypeHandler` to satisfy the contract. If the type
  handler is ever removed — a C8-internal decision two layers away from
  this codebase — nothing in this suite breaks.

The only thing being given up is the ability to distinguish "C7 incident had
a null message" from "C7 incident had an empty message" at the DB level.
Neither distinction is observable via the search API today, and no downstream
feature requires it.

</details>

### Row 12 — `AuditLogEntity.entityKey` (JOB / EXTERNAL_TASK / general)

<details>
<summary><b>Row 12 — <code>AuditLogEntity.entityKey</code> (JOB / EXTERNAL_TASK / general)</b></summary>


Combined treatment for Rows 4, 5, and 12 — same field, same migrator code,
same decision shape.

#### Root cause

`AuditLogMigrator` sets `entityKey` only for three of the ten allowlisted
C7 entity-type categories:
- `PROCESS_INSTANCE`
- `PROCESS_DEFINITION`
- `TASK`

The other categories land null. They split into three buckets by data shape:

- **Typed FK already on the C7 row, single-entity ops only:** `JOB`
  (`getJobId()`), `EXTERNAL_TASK` (`getExternalTaskId()`), `DEPLOYMENT`
  (`getDeploymentId()`), `TENANT` (`getTenantId()`).
- **Identifier carried as a `PropertyChange.newValue`:** `USER`, `GROUP`,
  `TENANT`, `INCIDENT` (annotation operations).
- **No single natural key on the row:** `GROUP_MEMBERSHIP`,
  `TENANT_MEMBERSHIP`, `AUTHORIZATION`, `VARIABLE`, plus the batch /
  by-jobDefinitionId / suspend-by-process-key variants of `JOB`, and
  the async batch variant of `EXTERNAL_TASK`. C7 itself does not record
  a per-row target on these.

#### The decision

**Sub-decision 1 — fill `entityKey` for the cheap cases?**
For `JOB` and `EXTERNAL_TASK`, the value is already resolved one line
above the omission (`resolveJobKey` returns the C8 `jobKey`; the same
lookup pattern extends to EXTERNAL_TASK via `HISTORY_EXTERNAL_TASK`).
~10 lines, no semantic call. **Yes / no?**

**Sub-decision 2 — fill `entityKey` for the harder cases?**
For `DEPLOYMENT`/`TENANT` (typed FK but new branches), and `USER` /
`GROUP` / `TENANT` / `INCIDENT` (identifier via `PropertyChange.newValue`,
needs per-type null-safety). Adds ~20-100 lines plus a few per-type
sentinel choices for ambiguous sub-cases. **Yes / no?**

**Sub-decision 3 — what to do about the cases where no `right` value can be set?**
The "no single natural key" bucket genuinely has no target entity to
point at. Two blanket strategies:

- **Loosen the C8 contract (Option 4):** ask C8 to mark `entityKey`
  `@Nullable`. Most honest for rows that genuinely describe operations
  with no single target. Long calendar; cross-team coordination.
- **Uniform placeholder (Option 3 sentinel):** use a stand-in
  (e.g. `auditLogKey` itself, or `<entityType>:<auditLogKey>`) across
  all entity-less rows. Quick; downstream consumers can't filter audit
  logs by entity for these rows.

The Sub-decision 3 choice also applies to whatever Sub-decisions 1 and 2
leave uncovered (e.g., "yes" to 1, "no" to 2 means the blanket fallback
also handles the harder cases).

#### Cross-cutting note (not a nullability bug, but adjacent)

`updateEntityTypesThatDontMatchBetweenC7andC8` rewrites `entityType`
*after* `entityKey` has been resolved — e.g., a `RESOLVE` on
`PROCESS_INSTANCE` becomes `entityType=INCIDENT` while `entityKey`
still holds the process instance key; a `SET_VARIABLE(S)` becomes
`entityType=VARIABLE` while `entityKey` holds the task or process
instance key. Independent of the null problem, but worth raising in
the same conversation since the per-type sentinel decisions touch the
same code path.

</details>