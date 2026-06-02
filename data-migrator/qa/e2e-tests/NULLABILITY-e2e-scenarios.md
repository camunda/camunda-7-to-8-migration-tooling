# Nullability edge cases — manual Operate walk-through

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)

This file describes the six nullability scenarios seeded into Camunda 7 by
`src/main/resources/seed-c7-test-data.sh`. After `mvn verify` (or the manual
docker compose flow described in `README.md`) finishes the migration, point
your browser at the running stack and walk through the table below to see
what each null does to the Operate UI.

## Stack endpoints

| Service       | URL                                             | Credentials |
| ------------- | ----------------------------------------------- | ----------- |
| Operate       | http://localhost:8088/operate                   | demo / demo |
| Camunda 7     | http://localhost:8090/camunda                   | demo / demo |
| C8 REST       | http://localhost:8088/v2                        | demo / demo |

If the migrator has not produced a row, the corresponding null is not in C8 —
either the seed step failed (check `docker compose logs data-migrator`) or
the scenario relies on a different test artifact than expected.

## Scenarios

### 1. `JobEntity.worker` — external task never locked

- **Seed**: `nullExternalTaskProcessId` is started; its external task on topic
  `nullabilityTopic` is left unlocked (no `fetchAndLock` call). C7 stores a
  job row with `WORKER_ IS NULL`.
- **Migrator behaviour**: `ExternalTaskTransformer` explicitly passes `null`
  into `JobDbModel.worker`.
- **Where to look**:
  - Operate → Processes → `nullExternalTaskProcessId` → click the instance.
  - Open the side panel for the external task element.
  - Look at the **Jobs** tab / details. Note how the row renders an absent
    worker (empty cell, dash, or layout glitch).
  - Also check the same instance via REST: `GET /v2/jobs/search` with a
    filter on the process instance — confirm `worker` is omitted / null.

### 2. `JobEntity.lastUpdateTime` — never written by the migrator

- **Seed**: the existing `miProcess` async-before jobs cover this; no extra
  step.
- **Migrator behaviour**: `JobDbModel.lastUpdateTime` is never set on any
  code path. Every migrated job row has it null.
- **Where to look**:
  - Operate → Processes → `miProcess` → any instance → Jobs tab.
  - The "last updated" / sort-by-last-update column will look strange:
    blank cells, missing tooltips, or sort behaving unexpectedly.
  - REST verification: any row in `GET /v2/jobs/search` should have
    `lastUpdateTime` missing.

### 3. `JobEntity.elementInstanceKey` — async-before timing

- **Seed**: the `miProcess` async-before subprocess + failing service task.
  The async-before job is created *before* the corresponding flow node
  instance exists; the migrator therefore has no `elementInstanceKey` to
  associate.
- **Migrator behaviour**: `JobMigrator` leaves `elementInstanceKey` null
  when no flow node instance is found.
- **Where to look**:
  - Operate → Processes → `miProcess` → an instance with an incident.
  - In the side panel, click into the Job and try to navigate from the Job
    detail back to the flow node instance — the link should rely on
    `elementInstanceKey` and may break or land on the wrong scope.
  - Also: incidents view → click the failing job; verify the linked element
    instance lookup behaves correctly.

### 4. `AuditLogEntity.entityKey` — JOB user-operation-log

- **Seed**: `setRetries` is called on the failing `miProcess` job, which
  records a `UserOperationLogEntry` with `entityType=Job`.
- **Migrator behaviour**: `AuditLogMigrator.resolveJobKey` populates
  `jobKey` on the C8 row but leaves `entityKey` null.
- **Where to look**:
  - Operate does not display audit logs in its end-user UI. Use the C8 REST
    API to inspect: `GET /v2/audit-logs/search` (or the equivalent endpoint
    in your build), filter to `entityType=JOB`, and verify whether the
    backend errors out or returns the row with `entityKey` missing.
  - This is mostly a contract-violation surface for downstream tools
    (auditing dashboards, compliance exports), not Operate.

### 5. `AuditLogEntity.entityKey` — EXTERNAL_TASK user-operation-log

- **Seed**: `setPriority` is called on the unlocked external task from
  scenario #1.
- **Migrator behaviour**: same as #4 but for `entityType=EXTERNAL_TASK`.
- **Where to look**: same as #4. Filter the audit log endpoint to
  `entityType=EXTERNAL_TASK`.

### 6. `DecisionDefinitionEntity.decisionRequirementsId` — standalone DMN

- **Seed**: `nullStandaloneDmn.dmn` is deployed. It contains a single
  `<decision>` element with no DRD wrapper, so C7 does **not** create a
  `DecisionRequirementsDefinition` entity and
  `decisionRequirementsDefinitionKey` is null on the C7 row.
- **Migrator behaviour**: `DecisionDefinitionTransformer` passes the null
  through `prefixDefinitionId(null) → null` into the C8 column.
- **Where to look**:
  - Operate → Decisions → look for `Null Standalone Decision` /
    `nullStandaloneDecisionId`.
  - Click into it. The DRD (Decision Requirements Diagram) panel relies on
    `decisionRequirementsId`. Expect either a blank diagram, a console error,
    or a missing navigation breadcrumb.
  - Compare against `Invoice Classification` (from the existing
    invoice-example DMN) which **does** have a DRD — the contrast makes the
    breakage easy to spot.

## What we are *not* covering here

The three disabled tests in `NullabilityContractTest` cover null paths in
the migrator that are unreachable through any realistic Camunda 7 deployment
(LEFT JOIN read-side hydration, C7 history always emitting at least one
output row, DMN model always containing its own decision element). See the
`NULLABILITY-*.md` files next to that test for the full analysis.

## If a scenario does *not* reproduce

The seed script logs `WARNING:` lines (not `ERROR:`) when an optional step
can't find its target. If you do not see the expected null in C8:

1. Run `docker compose logs data-migrator` and search for the WARNING line.
2. Check the relevant REST endpoint on Camunda 7 manually to confirm the
   seed state is what we expected.
3. Verify the migrator actually picked up the row by querying the C8 RDBMS
   directly (`docker compose exec postgres psql -U camunda -d camunda -c …`).
