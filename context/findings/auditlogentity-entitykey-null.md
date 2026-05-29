# `AuditLogEntity.entityKey` is null after migration — what's happening and what to do

**Audience:** migration-tooling, C7, or C8 engineers picking this up cold.
**Reading time:** ~6 minutes.

Related: issue [#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339),
contract PR [camunda/camunda#51301](https://github.com/camunda/camunda/pull/51301),
companion technical doc
[NULLABILITY-entityKey.md](../../data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-entityKey.md).

## TL;DR

C8 recently added `requireNonNull(entityKey)` to the `AuditLogEntity` record. The data migrator only populates `entityKey` for three out of the ten Camunda 7 entity-type categories it processes — `PROCESS_INSTANCE`, `PROCESS_DEFINITION`, and `TASK`. For the other seven (`JOB`, `EXTERNAL_TASK`, `USER`, `GROUP`, `TENANT`, group/tenant memberships, `AUTHORIZATION`, `INCIDENT`, `DEPLOYMENT`, `VARIABLE`) the field lands as `NULL`. After migration, any C8 search-API call that returns one of those audit-log rows throws NPE, and that NPE typically breaks an entire page of audit logs.

The narrow fix for the two cases with concrete failing tests (JOB and EXTERNAL_TASK single-entity ops) is small. The broader fix across the remaining entity types is partly a code change and partly a product call: *"what should `entityKey` be for an audit log that doesn't have a natural single entity key?"*

## Why does the migrator write null?

In Camunda 7, audit logs are `UserOperationLogEntry` rows — one row per property change. Each row carries a bunch of typed FK columns (`PROC_INST_ID_`, `TASK_ID_`, `JOB_ID_`, `EXTERNAL_TASK_ID_`, `DEPLOYMENT_ID_`, etc.) — whichever columns are relevant for the entity being audited. There is **no generic "entity id" column** in C7.

In Camunda 8, the corresponding `AuditLogEntity` has a single `entityKey` field that's supposed to identify the audited entity, irrespective of type.

The migrator's job is to pick the right C7 column (or property-change value) and copy it into `entityKey`. Today it only does that in three branches:

- If the row's `entityType` is `PROCESS_INSTANCE`, copy the migrated process-instance key.
- If it's `PROCESS_DEFINITION`, copy the migrated process-definition key.
- If it's `TASK`, copy the migrated user-task key.

For every other entity type the corresponding code is either *missing entirely* (USER, GROUP, TENANT, MEMBERSHIPs, AUTHORIZATION, INCIDENT, DEPLOYMENT, VARIABLE) or *partially wrong* — and that's where it gets interesting:

- **JOB:** the migrator looks up the C8 `jobKey` and sets `jobKey` on the row, but forgets to also assign it to `entityKey`. One-line oversight.
- **EXTERNAL_TASK:** the branch that would have handled this case is guarded on `entityType == JOB`, so external-task rows never reach the lookup at all. The C7→C8 key mapping for external tasks already exists in the migrator; it's just unreachable from this code path.

So the null write is a mix of one structural omission (most entity types) and two narrow bugs (JOB / EXTERNAL_TASK).

## When does this happen in real life?

There's no single "trigger" — it's whatever customer action produced the audit-log row in C7. The realistic call sites in the C7 engine break down like this:

### Cases where a natural single key exists in C7

The migrator has, or could trivially have, enough data to populate `entityKey`:

- **JOB, single-job operations** — executing a job, deleting a job, setting priority/retries/duedate on one job, suspending or activating one job. The C7 row carries `JOB_ID_`. *(This is the case the failing integration test exercises.)*
- **EXTERNAL_TASK, single-task operations** — setting retries or priority on one external task, handling a BPMN error from a worker. The C7 row carries `EXTERNAL_TASK_ID_`. *(Same shape as JOB; covered by another failing test.)*
- **USER / GROUP / TENANT, identity admin** — creating, updating, or deleting a user, group, or tenant. C7 stores the affected id as a *property change*, not as a typed FK column.
- **INCIDENT, annotation operations** — setting or clearing an incident annotation. C7 stores `incidentId` as a property change.
- **DEPLOYMENT, lifecycle** — deploying, redeploying, or deleting a deployment. C7 stores `DEPLOYMENT_ID_`.

### Cases where no natural single key exists in C7

The migrator has nothing obvious to copy in:

- **JOB, async batch operations** — async batch "set retries on N jobs" produces a single audit-log row with no jobId, no jobDefinitionId, no process IDs. The whole point is that it's a batch.
- **EXTERNAL_TASK, async batch retries** — same shape, the external-task equivalent.
- **JOB, history-cleanup** — the engine writes an audit-log row when it creates its internal history-cleanup job. Again, no specific ids.
- **JOB, suspend/activate by job-definition or process-key** — the user suspended/activated *many* jobs at once by a non-job key. There is no single job to point at, only the criterion used.
- **JOB, set-retries by job-definition** — same shape.
- **GROUP_MEMBERSHIP / TENANT_MEMBERSHIP** — one user-action emits up to 2-3 audit-log rows, each row representing a different facet (userId, groupId, tenantId). There's no single "membership id" — the row *is* the change.
- **AUTHORIZATION, permission admin** — granting or revoking permissions. C7 has no authorization id surfaced through the audit-log API; only the property changes (permissions, resourceId, etc.).
- **VARIABLE, set/modify/remove variable** — C7 variables are identified by *(scope, name)*, not by a stable id. The audit log row carries the variable name as a property change and the scope (execution or task) as a typed FK column.

### What this looks like to a customer

A customer who has been operating C7 in production for years will have all of these rows in their `ACT_HI_OP_LOG`. The bigger and longer-lived the C7 install, the more "varieties of null" they'll inherit. A small dev install with a single test process might only ever hit the JOB or PROCESS_INSTANCE cases. An enterprise install with custom identity-management automation might have piles of USER / GROUP / TENANT_MEMBERSHIP rows.

## What does the user experience?

When the C8 search API loads an audit-log row, it constructs an `AuditLogEntity` record. That record's constructor calls `Objects.requireNonNull(entityKey)`. With a NULL row, that throws NPE.

The search API returns rows in pages and materialises the whole page in one shot. So **one bad row breaks the whole page**, not just itself. Concretely: any UI or downstream consumer that pages through audit logs hits an internal error the moment a null-`entityKey` row enters the page being assembled.

A wrinkle: today, audit logs are *not* surfaced in Operate's UI — they're consumed by downstream auditing and compliance tools. So the immediate human impact tends to be "external auditing pipelines start failing after migration" rather than "Operate shows an error." That's still a real customer impact; it's just not on a screen that the customer looks at daily.

Worth noting that we have **not** observed this NPE in our own e2e environment yet — the EE Docker image's REST API doesn't authenticate callers, and C7 skips audit-log persistence for anonymous calls, so we couldn't produce the rows in the first place. The integration tests prove the migrator writes null at the database level; the runtime NPE follows from the C8 contract mechanically.

## Options to populate the field

Listed roughly from cheapest to most invasive. None of them is obviously correct for *every* entity type without a product call.

### Option A — narrow code fix for JOB and EXTERNAL_TASK single-entity ops

For the two cases with concrete failing tests (the JOB and EXTERNAL_TASK single-entity ops above), the migrator already looks up the C8 key — it just doesn't assign it to `entityKey`. Add the assignment.

- **Cost:** ~2 lines, one in the existing JOB branch, one in a new EXTERNAL_TASK branch (or widen the existing predicate).
- **Customer impact:** these specific rows become readable through the C8 API. All other entity types still produce nulls and still break pages.
- **Tradeoff:** ships today, unblocks the failing tests, doesn't address the broader gap.

### Option B — populate `entityKey` for every entity type, using whatever C7 makes available

Add a per-entity-type branch in the migrator that picks the most natural identifier from each row:
- USER / GROUP / TENANT: the property-change value (`userId` / `groupId` / `tenantId`).
- INCIDENT (annotation ops): the `incidentId` property change, mapped to the C8 incident key.
- DEPLOYMENT: the C7 deployment id, stored as a raw string (deployments are not migrated).
- Async batch / history-cleanup / set-retries-by-jobDefinitionId / suspend-by-non-job-key: a **sentinel** value, because no natural key exists. Candidates: the audit log's own key, a synthesised composite (`"job-definition:" + jobDefinitionId`), or the literal operation type.
- Group / tenant memberships: each of the 2-3 rows carries one facet; use that row's facet value.
- AUTHORIZATION: nothing is obviously *the* authorization id; closest candidate is the `resourceId` property change.
- VARIABLE: a composite like `executionId:variableName`, since C7 variables have no standalone key.

- **Cost:** larger — perhaps 50-150 lines across new per-type branches, plus a category test that asserts non-null `entityKey` for every allowlisted C7 entity type so future additions don't reintroduce the bug.
- **Customer impact:** every row becomes readable. For the cases without a natural key the value will be a sentinel, not a "real" entity id — downstream auditing tools that filter on `entityKey` need to know that.
- **Tradeoff:** preserves all the data, but commits to sentinel choices that are essentially product decisions. Some of them (VARIABLE, MEMBERSHIPs, AUTHORIZATION) need a UX call from whoever owns the audit-log experience.

### Option C — skip rows the migrator can't populate

For rows where no natural key exists (batches, suspensions by job-definition, etc.), drop the row rather than write a sentinel.

- **Cost:** small — broaden the existing skip-with-reason guard.
- **Customer impact:** data loss. The customer's audit trail for "I ran an async batch retry on 500 jobs last year" simply vanishes from C8. Compliance / auditing tools that consume audit logs may flag this as missing data.
- **Tradeoff:** defensible *if* the team's stance is "we don't fabricate audit-trail entries"; harmful if the customer needs the audit row as evidence-of-action.

### Option D — relax the C8 contract

Ask the C8 team to mark `entityKey` as nullable on `AuditLogEntity`.

- **Cost:** zero migrator-side code. External calendar — depends on the C8 team's view.
- **Customer impact:** all rows are preserved and readable. Downstream consumers that filter or group by `entityKey` need to handle nulls. UI tooling that displays `entityKey` needs to show *something* sensible when it's missing.
- **Tradeoff:** preserves all the data without fabricating anything, but pushes "what to show when there's no entity key" onto every consumer rather than answering it once at migration time. Worth at least asking, because audit logs by their nature can describe operations that don't map cleanly to a single entity (batches being the obvious example).

### Combinations are likely

Option A is cheap and unblocks the failing tests; it can ship today regardless of what happens with the rest. Options B, C, and D apply differently per entity type — e.g., the team might choose B for USER/GROUP/TENANT/INCIDENT (where a key is available) and either C or D for batches and authorizations.

## What's the right call?

This is partly a code decision and partly an audit-trail policy decision: **what should `entityKey` mean for a Camunda 7 audit log row whose underlying entity has no single id — a batch operation, a suspend-by-job-definition, a variable change?**

- If `entityKey` is a *strict* "real id of the entity touched", then Option C (skip) is honest for the no-key cases and Option B (populate) for the others.
- If `entityKey` is *whatever the consumer needs to filter by*, then Option B with sentinels covers everything and the sentinel choice becomes a downstream-API contract.
- If audit logs are *fundamentally* allowed to describe operations without a single entity, then Option D (nullable contract) is the most honest answer.

The migration-tooling team can pick A unilaterally and ship it. B/C/D should be a conversation with whoever owns the audit-log surface on the C8 side, because the sentinel choices and the nullable-contract decision affect both teams.

## Where to look next

- **Technical detail (per-claim, with code citations):** [NULLABILITY-entityKey.md](../../data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-entityKey.md).
- **Status catalogue across all 12 null-write cases:** [NULLABILITY-status.md](../../data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-status.md).
- **Failing tests:** `NullabilityContractTest.shouldNotProduceNullEntityKeyForJobAuditLog`, `NullabilityContractTest.shouldNotProduceNullEntityKeyForExternalTaskAuditLog`.
- **Related entityType / entityKey mismatch** (separate from this null issue): the migrator rewrites entityType in two cases (`RESOLVE` on `PROCESS_INSTANCE` → `INCIDENT`, `SET_VARIABLE(S)` → `VARIABLE`) but leaves `entityKey` carrying the original type's key. Worth flagging during the same design conversation, even though it's not a nullability bug.
