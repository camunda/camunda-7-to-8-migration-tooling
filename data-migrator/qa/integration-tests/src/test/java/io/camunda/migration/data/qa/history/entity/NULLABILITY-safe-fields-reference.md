# Newly-non-null fields not in issue #1339 — safety reference

Related: [`NULLABILITY-status.md`](./NULLABILITY-status.md),
[camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)

C8 PR [camunda/camunda#51301](https://github.com/camunda/camunda/pull/51301)
added `Objects.requireNonNull` to a number of search-domain entity fields.
Issue #1339 enumerates the fields where the migrator currently produces
null. This document covers the other side: the **~30 newly-non-null fields
that issue #1339 does *not* mention** because the migrator already populates
them safely. Kept as a reference so a future change in the migrator (or in
C7's history layer) can be cross-checked against the safety mechanism each
field relies on.

## Safety mechanism legend

- **Always-present source data** — the C7 entity always has the value.
- **Skip-filter guard** — the migrator refuses to write the C8 row if the
  required upstream id is null (`EntitySkippedException` with a named
  `SKIP_REASON_*`).
- **Hardcoded default** — the migrator writes a constant, independent of
  C7 state.
- **Safe-by-accident** — the field has no explicit guard but the underlying
  code would NPE rather than write null; see notes per field.

## Fields

### `IncidentEntity`

| Field                  | How it's populated                                                          | Safety mechanism             |
|------------------------|-----------------------------------------------------------------------------|------------------------------|
| `incidentKey`          | `getNextKey()` — always returns a value                                     | Always-present source data   |
| `processDefinitionKey` | Skipped with `SKIP_REASON_MISSING_PROCESS_DEFINITION` if null               | Skip-filter guard            |
| `processInstanceKey`   | Skipped with `SKIP_REASON_MISSING_PROCESS_INSTANCE` if null                 | Skip-filter guard            |
| `tenantId`             | `getTenantId()` utility — falls back to `<default>`                         | Hardcoded default            |
| `flowNodeId`           | `sanitizeFlowNodeId(entity.getActivityId())` — `.replace(...)` NPEs on null | Safe-by-accident (see below) |

### `JobEntity`

| Field                      | How it's populated                                                                   | Safety mechanism             |
|----------------------------|--------------------------------------------------------------------------------------|------------------------------|
| `creationTime`             | `convertDate(historicJobLog.getTimestamp())` — always present on job logs            | Always-present source data   |
| `processDefinitionId`      | Skipped if null                                                                       | Skip-filter guard            |
| `processDefinitionKey`     | Skipped if null                                                                       | Skip-filter guard            |
| `processInstanceKey`       | Skipped if null                                                                       | Skip-filter guard            |
| `retries`                  | Hardcoded to `0`                                                                      | Hardcoded default            |
| `tenantId`                 | `getTenantId()` utility                                                               | Hardcoded default            |
| `type`                     | `historicJobLog.getJobDefinitionType()` — always present                              | Always-present source data   |
| `hasFailedWithRetriesLeft` | `boolean` primitive, defaults to `false`                                              | Hardcoded default            |
| `elementId`                | `sanitizeFlowNodeId(historicJobLog.getActivityId())` — `.replace(...)` NPEs on null   | Safe-by-accident (see below) |

### `DecisionInstanceEntity`

| Field                   | How it's populated                                                | Safety mechanism            |
|-------------------------|-------------------------------------------------------------------|-----------------------------|
| `decisionInstanceId`    | Formatted from `decisionInstanceKey`                              | Always-present source data  |
| `decisionInstanceKey`   | `getNextKey()`                                                    | Always-present source data  |
| `state`                 | Hardcoded to `EVALUATED`                                          | Hardcoded default           |
| `tenantId`              | `getTenantId()` utility                                           | Hardcoded default           |
| `decisionDefinitionId`  | `prefixDefinitionId(entity.getDecisionDefinitionKey())` — C7 key is required | Always-present source data  |
| `decisionDefinitionKey` | Skipped if null                                                   | Skip-filter guard           |

### `DecisionDefinitionEntity`

| Field                         | How it's populated                                       | Safety mechanism           |
|-------------------------------|----------------------------------------------------------|----------------------------|
| `decisionDefinitionKey`       | `getNextKey()`                                           | Always-present source data |
| `decisionDefinitionId`        | `prefixDefinitionId(entity.getKey())` — C7 key required  | Always-present source data |
| `name`                        | C7 `DecisionDefinition.getName()` — always present       | Always-present source data |
| `version`                     | C7 `DecisionDefinition.getVersion()` — always present    | Always-present source data |
| `decisionRequirementsKey`     | Skipped if null                                          | Skip-filter guard          |
| `decisionRequirementsVersion` | Populated from C8 DRD or C7 fallback                     | Always-present source data |
| `tenantId`                    | `getTenantId()` utility                                  | Hardcoded default          |

### `AuditLogEntity`

| Field           | How it's populated                                                          | Safety mechanism           |
|-----------------|-----------------------------------------------------------------------------|----------------------------|
| `auditLogKey`   | Formatted from `getNextKey()`                                               | Always-present source data |
| `entityType`    | `convertEntityType()` — throws on unsupported, never returns null           | Always-present source data |
| `operationType` | `convertOperationType()` — throws on unsupported, never returns null        | Always-present source data |
| `timestamp`     | C7 `UserOperationLogEntry.getTimestamp()` — always present                  | Always-present source data |
| `result`        | Hardcoded to `SUCCESS`                                                      | Hardcoded default          |
| `category`      | `convertCategory()` — falls back to `UNKNOWN`, never null                   | Hardcoded default          |

## Safe-by-accident details — `sanitizeFlowNodeId`

`IncidentEntity.flowNodeId` and `JobEntity.elementId` both go through
`sanitizeFlowNodeId(entity.getActivityId())`. That helper calls
`.replace(...)` directly on its argument — it would NPE if `activityId`
were ever null on the C7 side. So the field can't silently produce a null
in C8; the migrator would instead **crash loudly**.

The NPE path is currently unreachable. Cross-checked against the migrator's
supported scope (async-continuation jobs + their `failedJob` incidents,
written in `resolved` state):

- **Async-continuation jobs always have a non-null `activityId`** — the BPMN
  parser creates a `JobDefinition` for every async element with
  `activityId` set to the BPMN element id, and
  `JobEntity.ensureActivityIdInitialized()` resolves the job's
  `activityId` from that `JobDefinition` before persistence.
- Scenarios where `activityId` might intuitively be null are all ruled out:

  | Scenario                                                                 | Why it can't produce a null `activityId` we'd migrate                                                                                                                              |
  |--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
  | `RuntimeService.createIncident()` on a root execution (no current activity) | Blocked at the C7 API by `EnsureUtil.ensureNotNull("activity", execution.getActivity())` in `CreateIncidentCmd`.                                                                  |
  | Async-signal delivery job (no `JobDefinition`, no `executionId`)         | `activityId` is set from the event subscription at job construction (`EventSubscriptionJobDeclaration.newJobInstance` → catching event's id).                                      |
  | Failing timer-start job before any process instance exists               | The C7 incident has no process instance → migrator skips with `SKIP_REASON_MISSING_PROCESS_INSTANCE` before `sanitizeFlowNodeId` is reached.                                       |
  | Recursive incident propagated to a super-execution at root scope         | Requires the super-execution's `currentActivityId` to be null while its call-activity is still active — not reachable through the public C7 API today.                            |
  | Multi-instance job/incident                                              | Migrator skips with `SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE` when more than one flow node matches the C7 activity id (see issue [#1103](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103)). |
  | Engine-internal jobs (history cleanup, batch seed/monitor, timer suspend) | No `processDefinitionKey` → skipped with `SKIP_REASON_MISSING_PROCESS_DEFINITION`.                                                                                                 |

### Suggested hardening

Add an explicit null check inside `sanitizeFlowNodeId` so a future engine
change that *could* produce a null `activityId` lands as a controlled
`EntitySkippedException` (with a named skip reason) rather than a JVM-level
NPE. Cheap, belt-and-suspenders, no scope creep.

## When to revisit this document

- A new field is added to a search-domain entity's compact constructor with
  `requireNonNull`.
- The migrator's supported scope changes (e.g. multi-instance jobs become
  in-scope) — re-check the "out of scope" rows in the safe-by-accident
  table.
- A skip-filter is removed or weakened — re-check the corresponding
  "Skip-filter guard" rows.
