# Deployment and timer preflight

Every instruction is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Run after the Step 2 inventories, after acquiring more models, and before any target deployment or
readiness claim. Use the selected Camunda 8 version. Record findings, decisions, and tests in
`MIGRATION_REPORT.md`.
If a finding lacks an approved decision, required evidence, or required test, then set its report
item to `blocked`. Keep the migration incomplete until the finding is resolved.

## Deployment set

A deployment set includes models intended for the same target and tenant, even when modules deploy
separately. Confirm its target and tenant from Maven/Gradle resources, `@Deployment`, deployment
code, and commands. Where a deployment targets multiple tenants, assess each tenant separately.
When the target or tenant is unclear, ask the user. Never assume every repository model shares a
target or tenant.

At assessment, namespace-parse the original BPMN in each set. Before deployment, recheck the
converted copies that replace them; never count both copies as separate deployments. Record each
process ID, target tenant ID, module, and path. For every timer start, record its event ID and exact
date or cycle expression. For a cycle, record its interval and repetition count, or block on
unresolved expressions.
[Camunda 8 schedules timer starts on deployment](https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/).
Each firing creates an instance. A cycle without a repetition count runs indefinitely; deploying a
new version in the same tenant cancels the prior timer for that BPMN process ID.

Group definitions by process ID within each target tenant. Models in different tenants do not
collide. [Tenants isolate process definitions](https://docs.camunda.io/docs/components/concepts/multi-tenancy/).
Trace every start path: C7 by-key/by-ID calls, C8 BPMN ID/definition key calls, REST, message starts,
and call activities. Include `.latestVersion()` and IDs from constants or configuration. Record
each caller's selected tenant and version-selection behavior. Keep unknown IDs or tenants unresolved.

| Finding | Decision before deployment |
|---|---|
| Recurring timer start | Approve preserving, changing, or removing the exact cycle; record its automatic-start effect and any converted-copy change. |
| Duplicate process ID | Approve isolated targets, an explicit version, or a rename with old-to-new mappings and updated callers. |
| Unknown deployment boundary, target/caller tenant, cycle, or caller ID | Confirm it before deploying the affected models. |

Never silently change a timer or process ID. An unapproved decision blocks deployment and readiness.
Never deploy a recurring timer on a shared target just to check syntax. Before live deployment, get
approval for a bounded disposable-target test and its cleanup plan. Deploy the converted copy there,
observe its timer-created instances, then reset or destroy the target. Record the target version,
observed starts, and completed cleanup. Without this test, record `not run` and keep the recurring
deployment blocked.

## Active timer updates

Find C7 `ManagementService.setJobDuedate` and `recalculateJobDuedate` calls and method references,
REST `/job/{id}/duedate` and `/job/{id}/duedate/recalculate` calls, and their helpers. Check whether
the selected jobs are timers. For active timer updates, trace every caller, including repeated calls.
Match the selected job's tenant, process, and BPMN timer element to the value or expression
determining the new due date. For recalculation, record whether `creationDateBased` is true. Record
source locations, caller chains, timer expressions, and unresolved links as blocking open items.

Check the official API and timer documentation for the selected target version before proposing a
replacement. Record documented support and limitations. A C7 setter does not imply a C8 timer API.
For non-start timers, Camunda 8 evaluates the expression on activation. A later variable update
does not prove that the already-active timer is rescheduled.

| Finding | Required outcome |
|---|---|
| Approved, target-supported replacement | Test an already-active timer on a disposable target. Change its date twice; assert obsolete deadlines never fire and the final deadline fires once. Allow for late, never early, firing. |
| No verified or approved replacement, unknown timer link, or reachable throwing placeholder | Keep the affected flow blocked as manual work. Do not report it ready or substitute a no-op or unverified API. |

Record the chosen alternative and runtime evidence (including cleanup), or the `not run` blocker, in
`MIGRATION_REPORT.md`.
