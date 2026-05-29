# Nullability contract investigation — status

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)

Working document. Edit freely as we learn more.

## Background

C8 PR [camunda/camunda#51301](https://github.com/camunda/camunda/pull/51301)
added `Objects.requireNonNull` to compact constructors of search-domain
entities (`JobEntity`, `DecisionInstanceEntity`, `DecisionDefinitionEntity`,
`AuditLogEntity`, `IncidentEntity`). The migrator writes null values into
several of the newly-enforced fields, causing the C8 search-API read path
to throw NPE.

## Scenarios

12 scenarios across 5 entities. Statuses:

- ✅ **Real bug** — null reaches C8 read path and triggers `requireNonNull`.
- 🟡 **Masked at read** — write-side null silently converted by infrastructure
  (JOIN hydration, type handler). Same C8-design question for both.
- ❌ **Not reproducible** — null branch in migrator code exists but cannot be
  triggered by any realistic Camunda 7 deployment.

| #  | Source     | Field                                                       | Status                | Notes                                                                                                                                  |
|----|------------|-------------------------------------------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| 1  | new test   | `JobEntity.worker`                                          | ✅ Real bug           | External task never locked → `ExternalTaskTransformer:65` writes null                                                                  |
| 2  | new test   | `JobEntity.lastUpdateTime`                                  | ✅ Real bug           | Field never set by migrator on any code path                                                                                           |
| 3  | new test   | `JobEntity.elementInstanceKey`                              | ✅ Real bug           | Async-before job created before flow node instance exists                                                                              |
| 4  | new test   | `AuditLogEntity.entityKey` (JOB)                            | ✅ Real bug           | Specific case of #12 — kept as a focused repro                                                                                         |
| 5  | new test   | `AuditLogEntity.entityKey` (EXTERNAL_TASK)                  | ✅ Real bug           | Specific case of #12 — kept as a focused repro                                                                                         |
| 6  | new test   | `DecisionDefinitionEntity.decisionRequirementsId`           | ✅ Real bug           | `prefixDefinitionId(null)` passthrough for standalone DMNs                                                                             |
| 7  | new test   | `DecisionInstanceEntity.decisionDefinitionName`             | 🟡 Masked at read     | LEFT JOIN to `DECISION_DEFINITION` hydrates the field — write-side null invisible to contract                                          |
| 8  | new test   | `DecisionInstanceEntity.result`                             | 🟡 Masked at read     | Empty-outputs branch in `DecisionInstanceTransformer.java:173-176` writes null when a `COLLECT` decision matches zero rules; null masked at read by `NullToEmptyStringTypeHandler` on `DECISION_INSTANCE.RESULT`. Reproduced by `NullabilityContractTest.shouldNotProduceNullResultForDecisionInstanceWithEmptyOutputs` (`@Disabled`) using `noMatchCollectDmn.dmn`. Earlier classification "Not reproducible — C7 always emits a synthetic output row" was incorrect; corrected per [`NULLABILITY-result.md`](./NULLABILITY-result.md). |
| 9  | new test   | `DecisionInstanceEntity.decisionDefinitionType`             | ❌ Not reproducible   | `getDmnModelInstance(id).getModelElementById(key)` always finds the decision → defensive null branch unreachable                       |
| 10 | CI + new test | `IncidentEntity.flowNodeInstanceKey`                     | ✅ Real bug           | Async-before incidents — deliberate null in `IncidentMigrator.java:129-140`. Reproduced by `NullabilityContractTest.shouldNotProduceNullFlowNodeInstanceKeyForAsyncBeforeIncident` using the existing `incidentProcess.bpmn` fixture. |
| 11 | CI + new test | `IncidentEntity.errorMessage`                            | 🟡 Masked at read     | Pass-through of C7 `getIncidentMessage()`; null masked at read by `NullToEmptyStringTypeHandler`. Reproduced by `NullabilityContractTest.shouldNotProduceNullErrorMessageForIncident` (`@Disabled`) using `runtimeService.createIncident("foo", executionId, null)` on `userTaskProcess.bpmn` — sidesteps #10 so the masking is the only observable effect. |
| 12 | CI         | `AuditLogEntity.entityKey` (general)                        | ✅ Real bug           | Only set for C7 `PROCESS_INSTANCE` / `PROCESS_DEFINITION` / `TASK` entity types — every other type lands as null. #4/#5 are instances  |

**Roll-up:**
- 8 real bugs: #1, #2, #3, #4, #5, #6, #10, #12.
- 3 masked-at-read patterns: #7, #8, #11.
- 1 unreachable defensive branch: #9.

## Artifacts

### Integration tests
- `NullabilityContractTest.java` — 11 tests covering scenarios #1–#11.
  - 7 enabled and failing (the real bugs: #1, #2, #3, #4, #5, #6, #10).
  - 4 `@Disabled` with detailed reasoning (the unreachable/masked cases:
    #7, #8, #9, #11).
- Scenario #12 (`AuditLogEntity.entityKey` in its general form) is not
  duplicated here — the JOB / EXTERNAL_TASK instances (#4, #5) already
  exercise the same migrator code path.

### Fixtures (integration-test side)
- `dmn/c7/noMatchCollectDmn.dmn` + `bpmn/c7/noMatchCollectBusinessRuleProcess.bpmn`
  — added when chasing scenario #8. Kept because the test exercises the
  COLLECT hit policy + `collectEntries` mapping path which wasn't covered
  before.

### Design docs for C8 team review
Sibling READMEs in this folder:
- `NULLABILITY-decisionDefinitionName.md` — relevant to #7. JOIN-based
  masking. Follows trigger → reproduction attempt → why unreachable →
  question for C8.
- `NULLABILITY-errorMessage.md` — relevant to #11. `NullToEmptyStringTypeHandler`-based
  masking. Same question shape as #7 — could be bundled into one C8-team
  conversation.
- `NULLABILITY-result.md` — #8. Same format.
- `NULLABILITY-decisionDefinitionType.md` — #9. Same format.
- `NULLABILITY-safe-fields-reference.md` — per-field safety mechanism for
  the ~30 newly-non-null fields *not* listed in issue #1339. Cross-check
  reference for future migrator changes.

### E2E setup (manual Operate walk-through)
Lives under `data-migrator/qa/e2e-tests/`:
- Fixtures in `src/main/resources/process-example/`:
  `nullExternalTaskProcess.bpmn`, `nullStandaloneDmn.dmn`.
- Extended `seed-c7-test-data.sh`: deploys the new fixtures, starts the
  external-task process and leaves the task unlocked.
- Walk-through doc: `NULLABILITY-e2e-scenarios.md`.

#### Verified nulls in C8 (postgres against the running stack)

| Scenario                          | Rows | OK?  |
|-----------------------------------|------|------|
| `job.worker null`                 | 1    | ✅   |
| `job.last_update_time null`       | 3    | ✅   |
| `job.element_instance_key null`   | 1    | ✅   |
| `decision_def.drd_id null`        | 1    | ✅   |
| `audit_log.entity_key null`       | 0    | ❌   |

E2E does not currently exercise #10 (`IncidentEntity.flowNodeInstanceKey`)
or #11 (`IncidentEntity.errorMessage`) — both are covered by CI but easy to
add to the e2e seed if user-impact exploration in Operate would help.

### Audit-log scenarios — known e2e gap
Seed-script attempts to trigger `SetJobRetries` / `SetPriority` operations
did not produce `act_hi_op_log_` rows in C7 even with HTTP Basic auth
(`-u demo:demo`) — the EE docker image's REST API does not have
`ProcessEngineAuthenticationFilter` wired up, so the engine sees anonymous
callers and skips operation-log persistence.

**Decision:** don't pursue an e2e reproduction. Bugs are proven in the
integration tests + CI, and audit logs aren't surfaced by Operate's UI
anyway (only downstream auditing tools consume them).

**Pending cleanup:** strip the audit-log seeding steps from
`seed-c7-test-data.sh` and mark scenarios #4 / #5 / #12 as "not covered by
e2e" in the walk-through doc. Not yet applied.

## Issue scope verification

Question: is the field list in issue #1339 complete, or are there newly
`requireNonNull`-enforced fields the migrator can also leave null?

Cross-checked against the C8 side (full list of fields PR #51301 made
non-null) and then walked each unlisted field through the migrator code and
the C7 engine code. Outcome: **issue scope is complete — no missing fields**.

### Unlisted newly-non-null fields are safely populated

The C8 contract adds `requireNonNull` to ~30 fields beyond what the issue
flags. Each one is safely populated by the migrator through one of three
mechanisms: always-present source data, an existing skip-filter guard, or a
hardcoded default. Full per-field breakdown lives in
[`NULLABILITY-safe-fields-reference.md`](./NULLABILITY-safe-fields-reference.md).

### Two safe-by-accident fields: `IncidentEntity.flowNodeId`, `JobEntity.elementId`

Both go through `sanitizeFlowNodeId(entity.getActivityId())`, which calls
`.replace(...)` directly on its argument — it would NPE if `activityId`
were ever null on the C7 side. So the field can't silently produce a null
in C8; the migrator would instead **crash loudly**.

The NPE path is currently unreachable: async-continuation jobs always have
a non-null `activityId` (set from the `JobDefinition` at job construction),
and every other scenario where `activityId` might intuitively be null is
either blocked at the C7 API or already filtered out by an existing skip
rule (`SKIP_REASON_MISSING_PROCESS_INSTANCE`,
`SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE`,
`SKIP_REASON_MISSING_PROCESS_DEFINITION`). See the safe-fields reference
doc for the full scenario-by-scenario walkthrough.

**Suggested hardening**: add an explicit null check inside
`sanitizeFlowNodeId` so a future engine change that *could* produce a null
`activityId` would land as a controlled "skip with reason" rather than a
JVM-level NPE. Cheap, belt-and-suspenders, no scope creep.

## Pending decisions

1. **Masked-at-read pattern (#7, #11)** — same underlying question for both:
   is read-time masking (LEFT JOIN hydration; `NullToEmptyStringTypeHandler`)
   considered a supported contract for the affected entities, or is it
   incidental and the migrator should populate the write-side field
   explicitly? Bundling #7 and #11 into one C8-team question would be
   efficient.
2. **Cleanup of audit-log seeding in e2e** — agreed in principle, not yet
   applied.
3. **Add #10 / #11 to e2e walk-through?** — integration test coverage is
   now in place (`NullabilityContractTest`), so this is optional. Would
   only be useful if you want to *see* the incident null impact through
   Operate's incidents view rather than just trust the integration result.
4. **`sanitizeFlowNodeId` null check** — safe-by-accident NPE guard for
   `IncidentEntity.flowNodeId` and `JobEntity.elementId` is unreachable
   today, but adding an explicit null check would convert a future crash
   into a controlled skip-with-reason. Cheap to apply.
5. **Original plan items not started**: ArchUnit rule banning RDBMS readers
   in tests; refactor of existing tests to prefer the search API; estimate
   for the broader issue #1339 fix.

## Suggested next move

Apply the audit-log cleanup, then translate the real bugs into a
migrator-side punch list on issue #1339:

- 7 scenarios with focused integration-test repros (#1, #2, #3, #4, #5, #6,
  #10).
- #12 (general `AuditLogEntity.entityKey`) means the audit-log fix should
  cover *all* C7 entity types in `AuditLogMigrator`, not just the JOB /
  EXTERNAL_TASK cases #4 and #5 exercise directly.

Bring the **2 masked-at-read** patterns (#7, #11) and **2 unreachable
branches** (#8, #9) to the C8 team as a single design conversation — the
sibling READMEs already capture the questions. With that, you have a
defensible scope for #1339 plus concrete user-impact evidence (the Operate
walk-through) to share alongside.
