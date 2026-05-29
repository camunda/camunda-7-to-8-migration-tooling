# Nullability cases — proposed resolution

Companion to [NULLABILITY-status.md](./NULLABILITY-status.md). The status doc
catalogs what was found; this doc proposes what to do about each case.

## Option vocabulary

For each scenario, five resolution paths are available:

1. **Do nothing** — accept the current state. Defensible only when the case
   is non-failing today *and* the dependency on upstream behavior is judged
   acceptable.
2. **Add a canary test** — pin foreign-side coercion (LEFT JOIN hydration,
   `NullToEmptyStringTypeHandler`) so a future C8-side regression surfaces
   as a clear failure in this suite rather than as a cryptic NPE downstream.
3. **Migrator-side default** — write a sensible non-null value at migration
   time, removing any dependency on read-side coercion or upstream contract
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

Pipe (`\|`) marks the chosen primary option for each scenario.

| #  | Field                                              | Status       | 1: Do nothing | 2: Canary test | 3: Migrator default | 4: Negotiate w/ C8 | 5: Skip row |
|----|----------------------------------------------------|--------------|---------------|----------------|---------------------|--------------------|-------------|
| 1  | `JobEntity.worker`                                 | Real bug     |               |                | ✅                  |                    |             |
| 2  | `JobEntity.lastUpdateTime`                         | Real bug     |               |                | ✅                  |                    |             |
| 3  | `JobEntity.elementInstanceKey`                     | Real bug     |               |                |                     |                    |             |
| 4  | `AuditLogEntity.entityKey` (JOB)                   | Real bug     |               |                |                     |                    |             |
| 5  | `AuditLogEntity.entityKey` (EXTERNAL_TASK)         | Real bug     |               |                |                     |                    |             |
| 6  | `DecisionDefinitionEntity.decisionRequirementsId`  | Real bug     |               |                | ✅                  |                    |             |
| 7  | `DecisionInstanceEntity.decisionDefinitionName`    | Masked       |               |                |                     |                    |             |
| 8  | `DecisionInstanceEntity.result`                    | Masked       |               |                | ✅                  |                    |             |
| 9  | `DecisionInstanceEntity.decisionDefinitionType`    | Unreachable  |               |                |                     |                    |             |
| 10 | `IncidentEntity.flowNodeInstanceKey`               | Real bug     |               |                | ?                   | ?                  | ?           |
| 11 | `IncidentEntity.errorMessage`                      | Masked       |               |                | ✅                  |                    |             |
| 12 | `AuditLogEntity.entityKey` (general)               | Real bug     |               |                |                     |                    |             |

## Per-case rationale

### Row 1 — `JobEntity.worker`
**Option 3.**

Two transformers feed `JobEntity.worker`. `ExternalTaskTransformer.java:65`
hardcodes `.worker(null)`. `JobTransformer.java:58` passes through
`historicJobLog.getHostname()`, which can also be null in edge cases
(custom `HostnameProvider`, legacy DB rows from older C7 versions). Full
analysis in [`NULLABILITY-worker.md`](./NULLABILITY-worker.md).

Fix: replace each `.worker(...)` call with a null-safe fallback to a stable
placeholder. Apply to both transformers so both null paths satisfy the C8
contract.

**Sub-decision pending:** placeholder string. The investigation proposed
`"camunda-7-migrated"`; the codebase already has `C7_LEGACY_PREFIX = "c7-legacy"`
in `MigratorConstants.java:25` as the prefix convention for migrated data.
Reusing the existing convention would keep the audit tag consistent.

### Row 2 — `JobEntity.lastUpdateTime`
**Option 3 (variant 3b).**

The migrator never sets `lastUpdateTime` on any code path that writes a
`JobDbModel`. Both transformers (`JobTransformer.java:53-66` for regular
jobs, `ExternalTaskTransformer.java:54-72` for external tasks) and both
migrators leave the field as the builder's default null, which the MyBatis
insert maps to SQL NULL. The C8 read path has no coercion for
`OffsetDateTime` fields, so the null reaches the `JobEntity` constructor
and `requireNonNull` fires. Full analysis in
[`NULLABILITY-lastUpdateTime.md`](./NULLABILITY-lastUpdateTime.md).

**Options considered:**

- **Option 1 — do nothing.** Rejected. Every C8 job search of a migrated
  row NPEs at the entity constructor.
- **Option 2 — canary test.** Rejected. No read-side coercion to pin —
  nothing is silently masked.
- **Option 3a — `lastUpdateTime = creationTime`.** ~2 lines per
  transformer. All migrated rows have `state = COMPLETED` hardcoded, so
  the C8 row is a terminal-state snapshot and "last update" collapses to
  the only timestamp the migrator records. Caveat: that timestamp is the
  *earliest* C7 log entry's, not the actual last lifecycle event.
- **Option 3b (selected) — fetch the latest log entry per job.** Extend
  `C7Client` with a `DESC`-ordered single-result variant of
  `getHistoricJobLog` (and the external-task counterpart), source
  `lastUpdateTime` from it. ~20 lines + one extra C7 round trip per
  migrated job. The migrator currently reads only the earliest log per
  job (`HistoryEntityMigrator.java:375-380` skips already-tracked IDs),
  so this is a real extension, not a one-line tweak. Semantic gain:
  `lastUpdateTime` reflects the real last C7 lifecycle event (failure,
  success, deletion) rather than collapsing onto creation time.
- **Option 3c — `Instant.now()`.** Rejected as misleading; encodes
  migration time, not anything about the C7 job.
- **Option 4 — negotiate with C8.** Ask the team to mark
  `lastUpdateTime` `@Nullable`. Defensible (a migrated job is a snapshot)
  but long calendar.
- **Option 5 — skip rows.** Equivalent to "skip every migrated job".
  Not viable.

**Product/UX question deferred:** the investigation flagged "does any
downstream consumer distinguish `lastUpdateTime` from `creationTime` for
migrated terminal-state jobs?" as an open question gating 3a vs 3b.
3b is selected here; if product input later concludes the distinction
is not user-visible, the cheaper 3a remains a fallback.

### Row 3 — `JobEntity.elementInstanceKey`
**Option 3, with semantic decision required.** Async-before jobs are created
before any flow node instance exists in C7's lifecycle. The clean migrator-
side fix is to materialise the flow node instance row first, then reference
its key from the job — an ordering change in `AsyncContinuationMigrator`,
not a new sentinel. If the ordering refactor is judged too invasive, this
row can be re-targeted at Option 4 (ask C8 to permit null for the
"async-before, no FNI yet" case). Flag for design review before implementing.

### Row 4 — `AuditLogEntity.entityKey` (JOB)
**Option 3.** Specific instance of Row 12, exercised through the JOB code path.
Same fix as Row 12.

### Row 5 — `AuditLogEntity.entityKey` (EXTERNAL_TASK)
**Option 3.** Specific instance of Row 12, exercised through the EXTERNAL_TASK
code path. Same fix as Row 12.

### Row 6 — `DecisionDefinitionEntity.decisionRequirementsId`
**Option 3.**

`DecisionDefinitionTransformer.java:35` calls
`prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey())`, which
returns null for standalone DMNs (no parent DRD on the C7 side). The migrator
already creates a synthetic DRD row for these decisions via
`migrateSyntheticDrd` in `DecisionDefinitionMigrator.java:87-94`, with a
well-formed `decisionRequirementsId` of `"c7-legacy-<definitionsId>"`. Only
the foreign-key reference from the decision row to the synthetic DRD row is
missing. Full analysis in
[`NULLABILITY-decisionRequirementsId.md`](./NULLABILITY-decisionRequirementsId.md).

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

### Row 7 — `DecisionInstanceEntity.decisionDefinitionName`
**Option 3.** Same shape as Row 11. The migrator already writes the parent
`DECISION_DEFINITION` row with a non-null `NAME`; copy that value onto
`DECISION_INSTANCE.DECISION_DEFINITION_NAME` at write time so the field is
self-sufficient and not reliant on the LEFT JOIN to hydrate it at read time.
Removes the dependency on read-side coercion.

### Row 8 — `DecisionInstanceEntity.result`
**Option 3.**

Earlier classification ("Unreachable defensive branch") was wrong. The
empty-outputs branch in `DecisionInstanceTransformer.java:173-176` is
reachable: when C7 evaluates a `COLLECT` (no aggregator) decision with
zero matched rules, the C7 history producer
(`DefaultDmnHistoryEventProducer.java:277-305`) iterates matched rules,
not output clauses — zero rules ⇒ zero output instances. The transformer
hits the empty branch and writes Java `null`, which the MyBatis insert
persists as SQL NULL.

At read time the value is masked by `NullToEmptyStringTypeHandler` on the
`RESULT` column (`DecisionInstanceMapper.xml:218-219`), so the
`DecisionInstanceEntity` constructor sees `""` and `requireNonNull`
passes. Same masking mechanism as `IncidentEntity.errorMessage` (Row 11).
Full analysis in [`NULLABILITY-result.md`](./NULLABILITY-result.md).

The `""` coercion also produces a worse user-facing outcome: Operate's
Decision Instance "Result" tab renders `""` as a blank read-only editor
pane — its `?? '{}'` fallback only triggers on null/undefined, not on
`""`. Writing a meaningful JSON literal at the migrator removes both the
contract masking and the blank-pane issue.

**Sub-decision pending:** placeholder choice.
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

### Row 9 — `DecisionInstanceEntity.decisionDefinitionType`
**Option 3.** Mirror of Row 8 — defensive null branch is unreachable but cheap
to harden. Replace `return null;` with `return DecisionDefinitionType.UNSPECIFIED;`
(or `UNKNOWN`). Suggest confirming the sentinel choice with the C8 team in
passing — it doesn't gate the change.

### Row 10 — `IncidentEntity.flowNodeInstanceKey`
**Decision pending — Options 3, 4, and 5 under consideration.**

The migrator writes null at `IncidentMigrator.java:129-140` via a
fall-through: `findFlowNodeInstanceKey` returns null (the async-before
activity has no `HistoricActivityInstance` in C7 — it hasn't been entered),
`hasMultipleFlowNodes` is false, and `hasWaitingExecution` returns true
(C7 runtime has a parked execution at the activity), so neither skip-throw
fires and the incident is inserted with null `flowNodeInstanceKey`.
Migration ordering is correct — `HistoryMigrator.getMigrators()` line 95
(FlowNodeMigrator) precedes line 100 (IncidentMigrator); the null is not
an ordering accident. Full analysis in
[`NULLABILITY-flowNodeInstanceKey.md`](./NULLABILITY-flowNodeInstanceKey.md).

**Trigger scope:** fires only for in-flight migrations of live C7 instances
with a parked execution at the async-before activity. Migrations done after
C7 retirement do not hit this — `hasWaitingExecution` returns false and the
incident is skipped with `SKIP_REASON_MISSING_FLOW_NODE`.

**Options under consideration:**

- **Option 3a — skip the incident.** ~3 lines. Drop the fall-through, let
  the existing skip path handle it. Loses a live in-flight async-before
  incident — exactly the kind operators triaging an in-flight migration
  most need to see.
- **Option 3b — synthesize an FNI.** Mint a `flowNodeInstanceKey`, insert
  a matching `FlowNodeInstanceDbModel`. Introduces a "synthetic FNI for
  never-entered activity" concept; Operate's flow-node view must render
  an activity with no real entry/exit.
- **Option 4 — negotiate with C8.** Ask the team to mark
  `flowNodeInstanceKey` `@Nullable` for incidents on unentered activities.
  Long calendar; depends on whether C8 accepts a flow-node-less incident.
- **Option 5 — skip rows with null values.** Convergent with 3a — no
  distinct fifth path for this row.

**Rejected:** Option 1 (NPE on any returning search), Option 2 (no
read-side coercion to pin).

**Joint with Row 3:** the `findFlowNodeInstanceKey`-miss mechanic is shared
with `JobEntity.elementInstanceKey` (Row 3). Whichever option is chosen here
should be consistent with Row 3's. If 3b is selected, the synthesized FNI for
the job and the incident must be the *same* row when they correspond to
the same C7 activity on the same PI.

**Open product/UX question:** what should Operate show for a live in-flight
incident attached to an activity that has never been entered? The answer
dictates the 3a / 3b / 4 choice.

### Row 11 — `IncidentEntity.errorMessage`
**Option 3.

The migrator currently writes SQL NULL when C7's
`HistoricIncident.getIncidentMessage()` is null; the C8 read side masks this
via `NullToEmptyStringTypeHandler` on the `ERROR_MESSAGE` column, so the
field arrives at the `IncidentEntity` constructor as `""` and `requireNonNull`
is silently satisfied. The masking is invisible unless the type handler is
removed.

Option 3 (write `""` at the migrator) is the best answer because:

- **Cost is negligible.** ~3 lines in `IncidentMigrator`.
- **No semantic risk.** Empty error message is data a customer can already
  produce; no downstream consumer can distinguish `""` from SQL NULL via
  the search API (the type handler already coerces them identically).
- **Removes a foreign dependency.** The migrator no longer needs
  `NullToEmptyStringTypeHandler` to satisfy the contract. If the type
  handler is ever removed — a C8-internal decision two layers away from
  this codebase — nothing in this suite breaks.
- **Banks the investigation.** `NULLABILITY-errorMessage.md`, the
  `ThrowsWithNullMessage` fixture, and the disabled
  `shouldNotProduceNullErrorMessageForIncident` test all become a closed
  artifact instead of an ongoing watch item.
- **Forecloses one column in the "what if C8 hardens further" risk matrix.**
  The `NULLABILITY-*` series suggests C8 contracts are still being tightened;
  reducing the migrator's exposure to read-side coercion removes one class
  of future regressions before they materialise.

The only thing being given up is the ability to distinguish "C7 incident had
a null message" from "C7 incident had an empty message" at the DB level.
Neither distinction is observable via the search API today, and no downstream
feature requires it. If a future product need surfaces, the migrator can
switch to a richer sentinel (the exception class name, or `"<no message>"`)
without disturbing the contract.

### Row 12 — `AuditLogEntity.entityKey` (general)
**Option 3.** Generalisation of Row 4 and Row 5. `AuditLogMigrator` currently sets
`entityKey` only for the `PROCESS_INSTANCE`, `PROCESS_DEFINITION`, and
`TASK` branches; the other 7 allowlisted C7 entity types (`VARIABLE`,
`USER`, `GROUP`/`GROUP_MEMBERSHIP`, `TENANT`/`TENANT_MEMBERSHIP`,
`AUTHORIZATION`, `INCIDENT`, `DEPLOYMENT`, plus the JOB/EXTERNAL_TASK
cases tracked separately as Row 4/Row 5) land null. The data shape is uneven —
some types carry a typed FK (`JOB`, `EXTERNAL_TASK`, `DEPLOYMENT`), some
carry the id only as a `PropertyChange.newValue` (`USER`, `GROUP`,
`TENANT`, `INCIDENT`), and some have no single natural key
(`GROUP_MEMBERSHIP`, `TENANT_MEMBERSHIP`, `AUTHORIZATION`, `VARIABLE`).
So this is **Option 3 with 4-5 small per-type sentinel decisions**, not a
single mechanical completeness fix. Full per-type breakdown in
[`NULLABILITY-entityKey.md`](./NULLABILITY-entityKey.md).

The mechanical JOB/EXTERNAL_TASK fix (Row 4 + Row 5) can ship first — it's
unblocked, ~10 lines, and closes the two focused repros in
`NullabilityContractTest`. The remaining 5 sentinel decisions can be
batched into a single follow-up once the product call on
"what should `entityKey` mean for a `VARIABLE`/`MEMBERSHIP`/`AUTHORIZATION`
row" is made.
## Why Option 3 is the default answer

A pattern emerges across all 12 cases: **migrator-side default substitution
is the right resolution**, with Option 4 reserved as fallback for the few
cases where the substitution itself needs C8 input (Row 3, Row 6, Row 10).

This is consistent with the design principle that **the migrator should
produce rows that satisfy the C8 contract on its own**, without relying on
read-side coercion (LEFT JOIN hydration, type handlers) or upstream contract
relaxation. Read-side coercion is invisible to the migrator's tests and
fragile to upstream changes; upstream contract relaxation is slow and may
not be granted.

A migrator that owns its invariants:

- **Has a single failure mode.** If a contract is violated, the migrator's
  own write-side tests catch it; no need to test through the read layer.
- **Survives upstream churn.** C8 can tighten, loosen, or add coercions
  without affecting the migrator's correctness.
- **Has a clean audit story.** The `NULLABILITY-*.md` series, the disabled
  contract tests, and the workaround patch (`data-migrator/qa/e2e-tests/0001-fix-data-migrator-query-RDBMS-as-workaround-for-enfo.patch`)
  all collapse into a small number of migrator-side commits with clear
  before/after semantics.

The roll-up of Option 3 actions yields a manageable punch list on
issue [#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339):

- **7 straightforward sentinel/default writes:** Row 1, Row 2, Row 4, Row 5, Row 7, Row 11, Row 12.
- **2 defensive hardenings of unreachable branches:** Row 8, Row 9.
- **3 cases needing a one-line design decision before implementing:**
  Row 3, Row 6, Row 10. Resolve Row 3 and Row 10 jointly (shared root cause: FNI
  materialisation order).

The Option 4 conversation with the C8 team narrows from "please relax these
N non-null fields" to "are these specific sentinels acceptable?" — a much
smaller scope and a much shorter calendar.
