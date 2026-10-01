# Deployment and timer preflight

Every instruction is mandatory. "Never" means MUST NOT. Mark a preference with (SHOULD). Mark an option with (MAY).

Run after the Step 2 inventories, after acquiring more models, and before any target deployment or
readiness claim. Use the selected Camunda 8 version. Record findings, decisions, and tests in
`MIGRATION_REPORT.md`.
If a finding lacks an approved decision, required evidence, or required test, then set its report
item to `blocked`. Keep the migration incomplete until the project resolves the finding.
For a full migration, record the decisions through `references/validation-evidence.md`.
The gate checks the evidence structure and freshness. It cannot prove the meaning of a manual review
or inspect a target.

## Deployment set

A deployment set includes models intended for the same target, even when modules deploy separately.
Check its boundary in Maven/Gradle resources, `@Deployment`, deployment code, and commands.
When the boundary is unclear, ask the user. Never assume every repository model shares a target.

At assessment, namespace-parse the original BPMN in each set. Before deployment, recheck the
converted copies that replace them. Never count both copies as separate deployments.
Record every process ID, module, and path. For every process-level timer start, record the event ID
and exact date or cycle expression. A start event inside an event subprocess is not a deployment
schedule. For a retained ISO cycle, record its interval and repetition count.
For a cron cycle, record its exact expression and expected start schedule.
Block deployment when its expression remains unresolved. A removed source timer still needs an
approved removal, but no runtime observation.
The gate does not parse cron fields. Model lint and a disposable timer observation must pass.
[Camunda 8 schedules timer starts on deployment](https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/).
Each firing creates an instance. A cycle without a repetition count runs indefinitely.
Deploying a new version cancels the prior timer for that BPMN process ID.

Group definitions by process ID across the set. Trace C7 `startProcessInstanceByKey` and
`createProcessInstanceByKey` callers, C8 `bpmnProcessId` callers, and `.latestVersion()` calls.
Check method references, constants, configuration, and multiple calls on one source line.
Include Java, JavaScript, TypeScript, and their supported source extensions.
Record every caller of a duplicate ID and its version selection. Keep dynamic or unknown IDs
unresolved. The gate does not parse callers or certify inventory completeness.

| Finding | Decision before deployment |
|---|---|
| Recurring timer start | Approve `add`, `change`, `preserve`, or `remove` for the exact cycle. Record its automatic-start effect. |
| Duplicate ID with a retained recurring start | Isolate the target groups or rename the colliding IDs. An explicit caller version does not preserve the other model's timer schedule. |
| Duplicate ID without a retained recurring start | Approve explicit caller versions or a rename with old-to-new mappings and updated callers. |
| Source ID collision resolved in the converted copies | Record the old-to-new mappings and check every affected caller. |
| Unknown deployment boundary, cycle, or caller ID | Check it before deploying the affected models. |

Never silently change a timer or process ID. An unapproved decision blocks deployment and readiness.
Never deploy a recurring timer on a shared target just to check syntax. Before live deployment, get
approval for a bounded test on a disposable target and for its cleanup plan. Deploy the converted
copy there. Observe its timer-created instances. Then reset or destroy the target. Record the target
version, observed starts, and completed cleanup. Without this test, record `not run` and keep the
recurring deployment blocked. The gate requires one matching model-bound observation for each
retained timer start.

### Disposable Camunda 8.9.21 observation

An empty local target received two module BPMNs with process ID `Sample`. The first had an
`R/PT5S` start, and the second had no timer. Versions 1 and 3 started timer instances before
and around deployment of versions 2 and 4, which replaced their schedules.
By-ID starts selected the latest version.
Version 3 started instances at `11:36:32.503Z`, `11:36:37.837Z`, and `11:36:42.150Z`.
Version 4 deployed by `11:36:43.3Z`. No later timer starts appeared in the next 15 seconds.
The operator stopped the target. The operator checked that its port was closed. The operator
removed its state. This observation covers deployment behavior on 8.9.21 only. It does not test
changes to timers on active instances.

## Active timer updates

Find direct C7 `ManagementService.setJobDuedate` calls and method references, REST
`/job/{id}/duedate` and `/job/{id}/duedate/recalculate` calls, and their helpers. Check whether the
selected jobs are timers. For active timer updates, trace every caller, including repeated calls.
Match the process and BPMN timer element to the value supplying the new date. Record source
locations, caller chains, timer expressions, and unresolved links as blocking open items.
Review every module, including modules with no detected call. The gate's text scan only flags likely
calls. Inspect URI builders, concatenated paths, and helper methods yourself.
Classify a non-timer call with evidence tied to its source location. A detected active or
unclassified update keeps the gate `NOT READY`.

Check the official API and timer documentation for the selected target version before proposing a
replacement. Record documented support and limitations. A C7 setter does not imply a C8 timer API.
For non-start timers, Camunda 8 evaluates the expression when the timer activates. A later variable
update does not reschedule a timer that already waits.

| Finding | Required outcome |
|---|---|
| Project-approved message-rearm model | Map each due-date call to the executable timer process, parent call activity, timer, message, correlation key, and both date variables. Test two updates to an already-active timer on a disposable target. Include one earlier and one later deadline change. For consecutive publications, use a bounded TTL and a unique message ID, or wait for a rearm acknowledgement before publishing the next update. Verify the process remains active no more than five seconds before the final deadline. Assert that obsolete deadlines never fire and the final deadline fires once. Allow late firing, not early firing. |
| No verified or approved replacement, unknown timer link, or reachable throwing placeholder | Keep the affected flow blocked as manual work. Do not report it ready or substitute a no-op or unverified API. |

The repository fixture uses message-driven BPMN rearming. A parent call activity starts a separate
timer process. An interrupting message boundary event cancels that call and maps the received date
to the parent scope. The message branch passes through an exclusive converging gateway and starts a
new child process instance. The new timer reads the updated date.
This design requires a process-specific message, correlation key, and model change. A variable
update alone does not rearm the active timer.

The project must approve the mapping and record its reference. The gate requires the explicit
`message_rearm` decision and a disposable runtime test at the same target version. The test must
prove that the timer was active before both updates, both updates correlated, neither old deadline
fired, and the final deadline fired exactly once. The observation must also prove target cleanup.
Record the decision and runtime evidence, or the `not run` blocker, in `MIGRATION_REPORT.md`.

The repeatable acceptance fixture combines the two-module `Sample` deployment case and the active
timer test. Run `python3 agentic-migration-skills/fixtures/validation-evidence/run_live_timer_fixture.py`
from the repository root. Use Java 21, Maven, and Docker. The fixture uses Camunda 8.9.21 and checks
that its disposable container is removed.
