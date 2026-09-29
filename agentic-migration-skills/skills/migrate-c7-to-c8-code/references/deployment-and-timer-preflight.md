# Deployment and timer preflight

Every instruction is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Run after the Step 2 inventories, after acquiring more models, and before any target deployment or
readiness claim. Use the selected Camunda 8 version. Record findings, decisions, and tests in
`MIGRATION_REPORT.md`.
If a finding lacks an approved decision, required evidence, or required test, then set its report
item to `blocked`. Keep the migration incomplete until the finding is resolved.
Also record deployment-set, caller, timer-disposition, and active-timer-update evidence in
`.camunda-migration/validation/validation-evidence.json` and through
`validate_migration_evidence.py`. A report note alone does not satisfy the machine-readable gate.

## Deployment set

A deployment set includes models intended for the same target, even when modules deploy separately.
Check its boundary from Maven/Gradle resources, `@Deployment`, deployment code, and commands.
When the boundary is unclear, ask the user. Never assume every repository model shares a target.

At assessment, namespace-parse the original BPMN in each set. Before deployment, recheck the
converted copies that replace them. Never count both copies as separate deployments. Record each
process ID, module, and path. For every timer start, record its event ID and exact date or cycle
expression. For a cycle, record its interval and repetition count, or block on unresolved expressions.
The deployment-scheduled timer inventory includes only start events directly owned by a
`bpmn:process`. A timer start inside an event subprocess is not a deployment schedule.
The active-timer inventory includes timer events nested in process scopes.
[Camunda 8 schedules timer starts on deployment](https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/).
Each firing creates an instance. A cycle without a repetition count runs indefinitely.
Deploying a new version cancels the prior timer for that BPMN process ID.

### Reported disposable Camunda 8.9.21 test

A disposable c8run test used two module directories with process ID `Sample`. Module A had an
`R/PT5S` timer start. Module B had no timer. The cluster was empty before deployment.

The first run deployed A as version 1 and B as version 2. Starts by
`processDefinitionId=Sample` selected version 2. Version 1 created timer instances about every
5.5 seconds before and around the version 2 deployment. No further version 1 timer instances
appeared during the next 24 seconds.

A repeat run deployed the timer model as version 3 and the model without a timer as version 4.
Version 3 created instances at `11:36:32.503Z`, `11:36:37.837Z`, and `11:36:42.150Z`. Version 4
deployment completed by `11:36:43.3Z`. No later version 3 starts appeared during the next
15 seconds. Starts by `processDefinitionId=Sample` selected version 4.

The test operator shut down the cluster, confirmed port 8081 was closed, and removed the c8run
state directory. This test supports the observed deployment behavior for Camunda 8.9.21 only.
It does not validate active timer due-date updates. No C8 active-timer reschedule alternative is
approved. Keep detected active updates blocked and readiness `NOT READY`.

Group definitions by process ID across the set. Trace C7 `startProcessInstanceByKey` and C8
`bpmnProcessId` callers, including `.latestVersion()` and IDs from constants or configuration.
For each deployment set, record detected callers whose process IDs belong to that set.
Do not include callers to process IDs in another set.
The gate records set membership and callers in validation evidence. It scans declared module source
for `startProcessInstanceByKey`, `createProcessInstanceByKey`, `bpmnProcessId`, and `.latestVersion()` calls.
Record every caller of a duplicate ID and its version selection.

| Caller form | Gate requirement |
|---|---|
| C7 by-key calls | Record `startProcessInstanceByKey` or `createProcessInstanceByKey` as `latest_version`, even without `.latestVersion()`. |
| C8 `bpmnProcessId` | Record the version selection from the call site. Use `unknown` when the source does not show it. |
| `.latestVersion()` | Include its source location in a caller record with `latest_version`. |

Each caller record names its module, source location with line number, process ID, operation, and
version selection. A duplicate process ID needs at least one matching caller record.
Missing or empty inventories cannot pass. A preflight record also becomes stale when its source or
model changes. Static JavaScript and TypeScript template literals are read as process IDs.
When a simple identifier or member expression supplies an ID or version, trace the constant or
configuration value and record its resolved value at the same location and operation.
Dynamic expressions and interpolated IDs remain unresolved and keep readiness `NOT READY`.

| Finding | Decision before deployment |
|---|---|
| Recurring timer start | Approve one exact-cycle disposition. Record its automatic-start effect and any converted-copy change. |
| Duplicate process ID | Approve isolated targets, an explicit version, or a rename with old-to-new mappings and updated callers. |
| Unknown deployment boundary, cycle, or caller ID | Resolve it before deploying the affected models. |

Never silently change a timer or process ID. An unapproved decision blocks deployment and readiness.
Never deploy a recurring timer on a shared target just to check syntax. Before live deployment, get
approval for a bounded disposable-target test and its cleanup plan. Deploy the converted copy there,
observe its timer-created instances, then reset or destroy the target. Record the target version,
observed starts, and completed cleanup. Without this test, record `not run` and keep the recurring
deployment blocked.
The gate requires a disposition review for each repeating timer. It also requires a runtime preflight
for each repeating timer retained in a converted model. It records ISO 8601 cycles with an interval
and repetition count. It rejects ISO cycles without a numeric component or with an empty time
section after `T`. It records cron cycles without fixed interval or repetition values.
Record the target version and cleanup plan. Pass a structured `--timer-observation-json` record to
the preflight check. Deployment and process commands revalidate the timer disposition and observation
before they execute.

| Evidence | Required values |
|---|---|
| Deployment | `performed: true`, a reference, the selected environment, a disposable target, and the selected version. |
| Observation | The expected process ID, start-event ID, cycle, and at least one started instance. |
| Cleanup | `completed: true` and an evidence reference. |

The gate checks that the record matches the model inventory. It cannot inspect a remote target.
Record only facts observed during the actual test. A successful command and a cleanup plan alone
cannot pass the preflight.

## Active timer updates

Find direct C7 `ManagementService.setJobDuedate` calls and method references.
Find REST `/job/{id}/duedate` and `/job/{id}/duedate/recalculate` calls.
The scan detects literal paths, template paths, and concatenated paths such as
`"/job/" + jobId + "/duedate"`.
It also detects URI-builder chains such as
`pathSegment("job").pathSegment(jobId).pathSegment("duedate")`.
Check whether the selected jobs are timers.
Classify every detected due-date call as an active timer update or a non-timer use.
Record non-timer evidence by source location and update kind with
`--non-timer-update-evidence-json`. Use `non_timer` only when every call is classified as non-timer.
For mixed results, map active timer locations to BPMN timers and keep every unclassified call blocked.
For active timer updates, trace every caller, including repeated calls.
Match the process and BPMN timer element to the value supplying the new date. Record source
locations, caller chains, timer expressions, and unresolved links as blocking open items.
Record an `active_timer_updates` review for each module. Record `no_updates` only after you review
the module and find no due-date calls. Block detected updates when their timer, callers, or supported
target alternative remain unresolved. Keep repeated update references blocked.
When updates are detected and an alternative is approved, pass `--affected-timers-json` to the
review. Each entry identifies an existing timer in a converted BPMN model by `model_path`,
`process_id`, and `timer_id`, and lists its `source_locations`. Together, the entries must map all
active timer update locations. Each timer must belong to a deployment set that includes the source
module. A timer from an unrelated set cannot satisfy the inventory.

| Decision evidence | Required gate result |
|---|---|
| Approved status, concrete approval and alternative evidence references, target version | Require a passing review and later runtime check. |
| Missing, pending, unresolved, or placeholder evidence | Keep the active-timer finding blocked and readiness `NOT READY`. |

The top-level `active_timer_update_decision` object holds this decision. The review records a
snapshot of it. The runtime check must follow the review and use the same target version. This
repository has no approved C8 alternative. Keep its decision unresolved.
The runtime check also needs `--active-timer-update-observation-json`.
Record each affected model, process, and timer in its own `timers` entry. Include the same
`model_path`, `process_id`, `timer_id`, and `source_locations` as the approved review inventory.
The runtime observation must match that inventory exactly: it cannot omit or add timers or source
locations. Map every detected update source location to one or more entries.
Each entry must show an active timer before two updates, zero obsolete deadline firings, and one final deadline firing.
Record the active `process_instance_id`, requested final deadline, and observed firing time in the
same entry. Use timezone-qualified timestamps. The firing time must not precede the requested
deadline.
Record completed cleanup in the cleanup entry.
See `references/validation-evidence.md` for the required fields.

Check the official API and timer documentation for the selected target version before proposing a
replacement. Record documented support and limitations. A C7 setter does not imply a C8 timer API.
For timer catch events, Camunda 8 evaluates the expression when the event activates. A later
variable update alone does not prove that the active timer is rescheduled.

| Finding | Required outcome |
|---|---|
| Approved, target-supported replacement | Record approval. Test an active timer twice on a disposable target. Change its date twice. Assert obsolete deadlines never fire and the final deadline fires once. Allow late firing, not early firing. |
| No verified or approved replacement, unknown timer link, or reachable throwing placeholder | Keep the affected flow blocked as manual work. Do not report it ready or substitute a no-op or unverified API. |

Record the chosen alternative and runtime evidence (including cleanup), or the `not run` blocker, in
`MIGRATION_REPORT.md`.
