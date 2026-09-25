# Deployment and Timer Preflight

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## When to run

Run this procedure during Step 2 after the code and model inventories.
Repeat it after migration and before each target deployment or readiness claim.
Use the confirmed deployment set and the selected Camunda 8 target version.
Record the inventories, decisions, tests, and open items in `MIGRATION_REPORT.md`.

## Identify the deployment set

1. Inspect Maven or Gradle modules that contain BPMN resources.
2. Inspect packaged resource directories, `@Deployment` patterns, deployment code, and deployment commands.
3. Record the modules and model paths that the user intends to deploy together.
4. Ask the user when the deployment boundary is unclear. Keep the deployment gate open until it is confirmed.
5. Do not treat every model in the repository as one deployment set without evidence.

Record each deployment set with its module, model path, packaging rule, deployment mechanism, and target.

## Inventory timer starts and process IDs

Parse each original BPMN in the deployment set with a namespace-aware XML parser.
Record each process ID, source path, and module.
Record each timer start event with its process ID, event ID, timer type, and exact source expression.
Classify a timer start as recurring when its timer definition contains `bpmn:timeCycle`.
Record the interval and repetition count from the exact cycle expression.

The [Camunda timer events documentation](https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/) states that deployment schedules each timer start event. Each timer trigger creates a process instance. A cycle without a repetition count repeats indefinitely. The prior version's scheduled timer is canceled based on the BPMN process ID.

Search the complete code inventory for callers that start each process ID.
Trace C7 `startProcessInstanceByKey` calls and Camunda 8 `bpmnProcessId` calls.
Record every caller that selects `.latestVersion()`.
Resolve constants and configuration before matching a call to a process ID.
Keep a caller unresolved when its process ID cannot be determined.

Group all process definitions in the intended deployment set by process ID.
Record every process ID that appears more than once.
Match each duplicate group to all known start callers, especially `latestVersion()` callers.
Record an unresolved duplicate group as a deployment blocker.

Use these tables in `MIGRATION_REPORT.md`:

| Inventory | Required columns |
|---|---|
| Timer starts | Module, source path, process ID, start-event ID, timer type, exact expression, interval, repetitions, automatic-start effect |
| Process IDs | Process ID, module, source path, deployment set, matching start callers, version-selection behavior |

## Obtain deployment decisions

Ask for an explicit decision for every recurring timer start and duplicate process ID before deployment.
Do not change a recurring timer or process ID before the user accepts the change.
Record the decision, user, date, affected files, and target version in `MIGRATION_REPORT.md`.

| Finding | User decision | Required evidence |
|---|---|---|
| Recurring timer start | Preserve the exact cycle, change it to an approved expression, or remove the timer start | Record the approved behavior and the exact converted-copy change |
| Duplicate process ID | Isolate the deployments, select an explicit process version, or rename the IDs | Record the chosen deployment behavior. For renamed IDs, record every old-to-new mapping and update every caller |
| Unresolved process ID or deployment boundary | Confirm the deployment set or resolve the caller | Keep the gate open until the module, definition, and caller are known |

If a required decision is missing, then stop before deployment.
Keep the related `MIGRATION_REPORT.md` open item at status `open`.
Do not report the migration as ready.

## Test recurring timer behavior safely

Use a BPMN parser and target-compatible linter to validate syntax.
Never deploy a recurring timer start to a shared target only to validate syntax.

When a runtime test is needed, use a disposable target only.
Before deployment, record the target version, deployment set, test window, and cleanup plan.
Include a plan to remove every timer-created instance and destroy or reset the disposable target.
Deploy only after the user approves the test and the timer decision.
Observe the timer behavior for a bounded interval.
Record every automatically created process instance and the deployment identifier.
Complete and record cleanup before the test passes.

If a disposable target or a complete cleanup plan is unavailable, do not deploy the recurring timer.
Record the runtime test as `not run` and keep the deployment gate open.

## Trace active timer due-date updates

Search the code inventory for direct Camunda 7 timer due-date updates.
Include `ManagementService.setJobDuedate`, REST paths `/job/{id}/duedate` and
`/job/{id}/duedate/recalculate`, and user-defined helper methods.
Search the complete repository for callers of every helper.
Trace direct callers, transitive callers, method references, listeners, and repeated invocations.
Record each source path, line, method, and full caller chain.

Parse the original BPMN for every related process ID.
Record each intermediate, boundary, or start timer that the helper can affect.
Record the timer element ID, timer type, attached activity when present, and due-date expression.
Record the code value that supplies the new date.
Keep the finding blocking when the code-to-process or code-to-timer link is unknown.

Read the official documentation and API for the selected target version.
Record the version, links, supported operation, and limitations.
Do not infer a Camunda 8 timer operation from the Camunda 7 setter name.
Do not claim that changing a variable reschedules an active timer unless the selected target documents that behavior.
The source setter does not define a Camunda 8 API mapping.

The timer events documentation states that Camunda 8 timers can fire later than their due date, but never earlier.
Use this timing rule when the selected target version supports the approved alternative.

| Evidence | Required action | Readiness |
|---|---|---|
| The selected target documents a supported alternative | Ask the project to approve the alternative, implement it, and test an already-active timer | Blocked until the repeated-change test passes |
| No supported alternative is documented or approved | Mark the flow as blocking manual work and create an open item | Blocked |
| A reachable `UnsupportedOperationException` replaces the C7 behavior | Trace every caller and affected timer. Do not report readiness. | Blocked |

Do not leave a reachable throwing placeholder while reporting the flow as migrated or ready.
Do not replace an update with a no-op, an unverified endpoint, or a variable-only change.
Record the selected alternative or the blocking manual decision in `MIGRATION_REPORT.md`.

## Test an already-active timer

Run the test only after the project approves a target-supported alternative.
Use a disposable target and record its cleanup plan before deployment.
Start a process instance and confirm that the termination timer is already active.
Record the process instance, deployment, target version, timer ID, and original due date.
Change the due date once and observe the old and new deadlines.
Change the due date again on the same active instance and observe both deadlines.
Assert that an obsolete deadline does not fire after each accepted change.
Assert that the timer follows the final approved deadline and fires only once.
Allow for asynchronous execution after the due date.
Record observed times, assertions, command results, and cleanup in `MIGRATION_REPORT.md`.

If the project selects blocking manual work, do not invent a replacement test.
Record the C8 test as `not run` because no supported alternative was approved.
Keep the active timer due-date update open and blocking.
