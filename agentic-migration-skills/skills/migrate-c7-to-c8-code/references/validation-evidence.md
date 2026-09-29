# Migration Validation Evidence

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

The recorder runs commands and saves their arguments, exit codes, and output. The gate compares
those records with the Step 2 scope. It writes the result in `MIGRATION_REPORT.md`.
Run the gate only after a full migration. Assessment-only and analyze-only runs do not claim
readiness.

## Prepare the scope

When the user approves a full migration, save the in-scope Step 2 paths in
`.camunda-migration/validation/step2-inventory.json` before conversion:

```json
{"schema_version":1,"modules":["examples/web"],"models":["models/order.bpmn"]}
```

Where E1 fetches a model, add its original path after retrieval and before conversion.
Then start a new validation run before recording checks:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . init
```

`init` assigns a new run ID to the Step 2 inventory and marks previous readiness `NOT READY`.
Run it again for a new migration, even if the paths have not changed. Old check logs cannot
make the new gate `READY`. Where the project uses Git, add `.camunda-migration/validation/` to
its `.gitignore`. Never commit generated logs, manifests, or summaries.

After conversion, create `.camunda-migration/validation/validation-evidence.json`:

```json
{
  "schema_version": 1,
  "modules": [
    {"path": "examples/web", "runtime_mode": "none",
     "test_suites": [{"name": "unit", "requires_docker": false}]}
  ],
  "models": [
    {"source_path": "models/order.bpmn", "path": "models/converted-c8-order.bpmn",
     "module": "examples/web", "deployment_set": "web",
     "processes": [{"id": "order-process", "standalone": true, "scenarios": ["normal"]}]}
  ],
  "deployment_sets": [
    {"name": "web", "modules": ["examples/web"],
     "models": ["models/converted-c8-order.bpmn"]}
  ],
  "active_timer_update_decision": {
    "status": "unresolved",
    "approval_reference": null,
    "alternative_evidence_reference": null,
    "target_version": null
  },
  "checks": []
}
```

Use project-relative paths. List every migrated module and every in-scope original BPMN/DMN.
Include all independent test suites from each module's build. Set `requires_docker` for each suite.
Where a module uses a Camunda Spring Boot starter, include its real-client context test as a suite.
Use `spring-boot`, `external-launcher`, or `none` for `runtime_mode`. Never set `none` for a runtime
module to skip runtime checks.

The gate compares module and original-model paths with the Step 2 inventory. It parses each
converted copy to find executable processes and repeating timer starts. Include every executable
process ID in its model's `processes` array. Use an empty array for DMN.
Deployment-time repeating starts are only `startEvent` elements directly owned by a
`bpmn:process`. Timer starts in event subprocesses are not deployment schedules.
The separate active-timer inventory includes timer events nested in process scopes, including
subprocesses, boundary events, and intermediate catch events.

Declare each intended target group once in `deployment_sets`. Include every converted BPMN/DMN
deployed to that target and every module that deploys or calls those models.
Give each model its owning `module` and matching `deployment_set`.
Each converted model must belong to exactly one set.
Put isolated target groups in separate sets.
The gate compares all BPMN process IDs within each set, including non-executable definitions.
The gate blocks duplicate IDs until callers select explicit versions or converted copies no longer share an ID.
The gate blocks a mapped rename while converted models still share the old ID.
The scan accepts static JavaScript and TypeScript template literals as process IDs. An interpolated
ID remains unknown and cannot satisfy caller coverage.

The deployment-set `preflight` review needs a complete caller inventory. The module scan detects
`startProcessInstanceByKey`, `createProcessInstanceByKey`, `bpmnProcessId`, and `.latestVersion()` calls.

| Caller form | Required operation | Version selection |
|---|---|---|
| C7 `startProcessInstanceByKey` or `createProcessInstanceByKey` | Record the detected operation. | `latest_version`, even without `.latestVersion()`. |
| C8 `bpmnProcessId` | `bpmnProcessId` | Record the call-site selection. Use `unknown` when the source does not show it. |
| `.latestVersion()` with no detected process call | `other` | `latest_version` |

Each caller record includes its module, project-relative source location with line number, process
ID, operation, and version selection. Use operation `startProcessInstanceByKey`,
`createProcessInstanceByKey`, `bpmnProcessId`, or `other`.
Include every detected process call. A duplicate process ID needs at least one matching caller
record. Its inventory cannot be absent or empty. Keep unknown IDs or version selections unresolved.
The gate blocks interpolated strings that contain process or timer call patterns.

Review each repeating timer's exact disposition before deployment. Use `add`, `change`, `preserve`,
or `remove`. The gate records both cycles, the interval, repetition count, module, deployment set,
and automatic-start effect. Run a disposable-target preflight for each retained repeating timer.
A removed timer needs a disposition review but no runtime preflight.

The gate requires an `active_timer_updates` review for each module. Review all direct C7
`ManagementService.setJobDuedate` and REST due-date calls, method references, helpers, callers, and
repeated updates. REST detection includes literal and template-literal paths.
It also includes concatenated paths such as `"/job/" + jobId + "/duedate"`.
The scan detects URI-builder paths such as
`pathSegment("job").pathSegment(jobId).pathSegment("duedate")`.
Record `no_updates` only after reviewing the module.
When the scan finds an update, keep it blocked until an approved, target-supported alternative
passes runtime validation.
Do not guess a C8 replacement or use a no-op, fake, or throwing placeholder.

| Decision evidence | Gate requirement |
|---|---|
| Approved status, concrete decision and alternative evidence references, and target version | Require a verified review and later runtime check. |
| Missing, pending, unresolved, or placeholder value | Keep the finding blocked and the gate `NOT READY`. |

The top-level `active_timer_update_decision` object stores this decision. The review log snapshots
it. The runtime check must follow the review.
The runtime check must match the decision snapshot and target version.
Keep the status `unresolved` until the project approves and documents a supported alternative.
This repository has no approved C8 replacement.
When updates are detected and an alternative is approved, include `--affected-timers-json` in the
review. Its array must map every detected source location to an existing timer in a converted BPMN
model. Each entry uses `model_path`, `process_id`, `timer_id`, and `source_locations`.
The runtime observation must match that inventory exactly.
It cannot omit or add timers or source locations.

Every check records a SHA-256 fingerprint of the declared models and caller-relevant source and
configuration files under the declared modules. The snapshot scans only those modules and models.
It excludes `.git`,
`.camunda-migration` evidence output, and common build directories. When an in-scope file changes,
the gate marks prior checks stale. Rerun the required checks or initialize a new run.
Before a dependent command runs, the recorder checks whether its prerequisites are current.
Timer preflights require a current timer disposition.
Active-timer runtime checks require a current approved review.
Model deployments require current model lint.
Deployments and process starts require current deployment-set preflight, duplicate process ID
decisions, and retained-timer checks.
Where a process has a worker-input review, its start command requires current evidence.
If a prerequisite is stale, refresh it before running its dependent command.
The recorder allows you to rerun a stale prerequisite.

For each standalone process, list `normal` and every missing-worker-input scenario in `scenarios`.
Record its worker-input review before starting it. For a non-standalone process, set
`standalone: false`, `scenarios: []`, and `covering_test` to the parent process test name.
Run that test as the process-path check. Document the reason it is not a standalone entry point in
`MIGRATION_REPORT.md`.

The recorder maintains `checks`. For a new run, start with an empty `checks` array.
Never write a `passed` command result into that array manually.
Use Python 3.9 or later. On Windows, replace `python3` with `py -3`.

## Capture checks

Run each command through `run`. Supply its actual arguments after `--`:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target examples/web --kind tests --scenario unit -- mvn -pl examples/web test
```

The recorder runs without a shell. It stores JSON evidence under
`.camunda-migration/validation/logs/`. It returns 0 only when the command exits 0. A failed
command returns 1 and still saves its output. Continue with other modules, models, and suites.
Run recorder invocations sequentially. The default command timeout is five minutes. Use
`--timeout <seconds>` for checks that need a different limit.
Never use a command that skips tests, checks only plugin help, or asserts only that a test file exists.
Use a bounded test that starts the application or packaged JAR. The test must assert startup before
it stops the process.

Use `review` for each review check. Give a substantive note naming the reviewed files and decisions:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type model --target models/converted-c8-order.bpmn --kind review --note "The skill checked source integrity, converter findings, form decisions, and DI."
```

For a deployment-set preflight, supply a JSON caller inventory. Each caller location must exist
inside its declared module:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type deployment_set --target web --kind preflight --note "Reviewed all process IDs, callers, and version selection." --caller-inventory-json '[{"module":"examples/web","location":"examples/web/src/main/java/org/example/Starter.java:42","process_id":"order-process","operation":"startProcessInstanceByKey","version_selection":"latest_version"}]'
```

For a duplicate process ID, record `explicit_version` only after every caller selects an explicit
version. A `mapped_rename` decision needs a complete mapping and no remaining collision.
When the module scan finds no active timer update calls, record `no_updates` after review:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type module --target examples/web --kind active_timer_updates --disposition no_updates --note "Reviewed setter and REST calls, helpers, callers, and timer links."
```

When detected updates remain unresolved, record the blocking finding:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . block --type module --target examples/web --kind active_timer_updates --reason "Active timer updates remain unsupported or unverified for the selected target."
```

When the project approves an alternative, record its exact timer inventory with the review.
Set the top-level `active_timer_update_decision` to approved before recording `verified`:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type module --target examples/web --kind active_timer_updates --disposition verified --affected-timers-json '[{"model_path":"models/converted-c8-order.bpmn","process_id":"order-process","timer_id":"PaymentDue","source_locations":["examples/web/src/main/java/TimerUpdates.java:42"]}]' --note "Mapped each update site to its BPMN timer after verifying the approved alternative."
```

If a check cannot run, record `block` with a reason. Never substitute a review for an executable
check:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . block --type module --target examples/web --kind tests --scenario unit --reason "The test dependency is unavailable."
```

After a command fails, inspect its JSON log. Use `classify` to distinguish application,
Testcontainers, Docker, compatibility, and other infrastructure failures. Leave the failure
`unknown` when its cause is not established. Classification never turns a failure into a pass.

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . classify --type module --target examples/web --kind tests --scenario unit --failure-class application --reason "A migrated assertion failed."
```

| Target | Required checks |
|---|---|
| Project `.` | `docker_info` before the first Docker-dependent suite, when any suite needs Docker. Use exactly `docker info`. |
| Each module | `compile`, one `tests` check per declared suite, and `review`. |
| Each module or code-free project | `active_timer_updates` review. Detected unresolved updates remain blocked. |
| Spring Boot runtime module | `configuration`, `spring_boot_run`, and `executable_jar`, in addition to module checks. |
| External runtime module | `configuration` and `external_launcher`, in addition to module checks. |
| Each converted BPMN/DMN | `lint`, `review`, then `deployment`. The recorder also checks XML parsing and source separation. |
| Each deployment set | `preflight` review with a complete caller inventory. |
| Each duplicate process ID within a deployment set | `duplicate_process_id` review with an explicit-version or mapped-rename decision. |
| Each standalone executable process | `worker_input_inventory` review and `process_path` for `normal` and each declared scenario. |
| Each non-standalone executable process | `process_path` with the `covering_test` as its scenario. |
| Each applicable behavior | A command check named in the assertion table below. |
| Each repeating timer in original or converted BPMN | A `disposition` review. |
| Each repeating timer retained in converted BPMN | A separate `preflight`. |

The module review covers the code checks in Step 4 of `SKILL.md`: dependencies and their
compatibility, imports, TODOs, business keys, client usage, queries, adapters, and packaged resources.
The model review covers source integrity, converter findings, accepted and referenced forms, BPMN
DI, and task-definition types. Where M1 applies, review the selected CLI artifact and listener
findings. Record unresolved decisions as failures or blockers, not as a passing review.

The worker-input review records the expected inputs and all direct-start scenarios. A process-path
command must assert the process ID and behavior. An active initiating instance does not prove that
a message started the downstream process.

| Assertion kind | Required when the converted process contains |
|---|---|
| `user_task_type` | A user task. Assert its type and resulting user task. |
| `downstream_message_instance` | A message event or message reference. Assert the downstream instance. |
| `branch_selection` | An exclusive, inclusive, or event-based gateway. Assert the selected branch. |
| `worker_input_output` | A Zeebe task definition or I/O mapping. Assert actual input and output values. |
| `incident_behavior` | A Zeebe task definition, I/O mapping, or BPMN error event. Assert the expected failure path. |
| `form_resolution` | A Zeebe form definition. Assert that the accepted form resolves. |

Run additional assertion checks when code or source-form metadata makes them relevant. The gate
derives the minimum set from the converted BPMN. It cannot infer every behavior from XML alone.

Before any deployment, inspect the configured connection for a local or non-production
cluster. Supply `--environment local` or `--environment non-production` to deployment and process
commands. The flag records the selected environment. It does not inspect the remote cluster.
Use the user-authorized profile and selected target version from
`model-migration-approaches.md`. Include accepted forms with their owning BPMN in
the deployment request. Block resources whose deployment names collide.
Use the same restriction for runtime checks. Never start a production worker as a validation
probe. Deploy every converted model. Never deploy a validation probe to production.

[Timer starts schedule work on deployment](https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/#timer-start-events).
Record a timer's disposition before its runtime preflight. Run each retained repeating timer
preflight on an explicitly disposable local or non-production target. Add these options:

- `--target-disposable`
- `--target-version <version>`
- `--cleanup-plan "<cleanup or isolation steps>"`
- `--environment local` or `--environment non-production`
- `--timer-observation-json '<JSON object>'`

A successful command and a cleanup plan alone cannot pass the preflight. The JSON object must record:

| Object | Required evidence |
|---|---|
| `deployment` | `performed: true`, a reference, the matching environment, a disposable target, and the matching target version. |
| `observation` | The expected process ID, start ID, cycle, and at least one started instance. |
| `cleanup` | `completed: true` and a cleanup evidence reference. |

Example object:

```json
{
  "deployment": {
    "performed": true,
    "reference": "observed-deployment-id",
    "environment": "local",
    "target_disposable": true,
    "target_version": "8.9.21"
  },
  "observation": {
    "process_id": "order-process",
    "start_id": "Start",
    "cycle": "R/PT1H",
    "instances_started": 1
  },
  "cleanup": {"completed": true, "evidence_reference": "cleanup-record"}
}
```

Replace every example value with evidence from the actual run. The gate checks the record's
structure and consistency. It cannot inspect the remote target. Never use a print-success command
or a cleanup plan instead of deployment, observation, and completed cleanup. If no safe target is
available, block the preflight. Do not deploy or start that model.

An approved active-timer alternative needs a separate `active_timer_update_runtime` check after its
passing review. Run it on an explicitly disposable local or non-production target.
Supply `--target-disposable`, `--target-version`, `--cleanup-plan`, the environment flag, and
`--active-timer-update-observation-json`.

The observation object must record:

| Object | Required evidence |
|---|---|
| `deployment` | `performed: true`, a reference, the matching environment, a disposable target, and the approved target version. |
| `observation` | An evidence reference and a non-empty `timers` array. Each timer record names `model_path`, `process_id`, and `timer_id`; its `source_locations` must exactly match the reviewed inventory. The records must contain exactly the reviewed timers and all detected update locations. Each record proves an active timer before updates, two updates, zero obsolete-deadline firings, and one final-deadline firing. |
| `cleanup` | `completed: true` and a cleanup evidence reference. |

Each timer record uses `model_path`, `process_id`, `timer_id`, `source_locations`,
`active_before_updates`, `updates_applied`, `obsolete_deadlines_fired`, and `final_deadline_fired`.

Readiness remains `NOT READY` until the alternative passes runtime validation.
If no safe plan exists, block the preflight. Do not deploy or start that model.

When a Docker probe fails, block its Docker-dependent suites and run the other suites. When Docker
responds but Testcontainers fails, classify the suite as `testcontainers`, not
`docker_unavailable`. Never attribute an unrelated test failure to Docker.

## Generate the gate

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . report
```

The gate compares the Step 2 scope, required checks, XML-derived process and timer checks, and
recorded results. It writes `.camunda-migration/validation/validation-summary.json` and replaces the
gate section in `MIGRATION_REPORT.md`. It returns 0 for `READY` and 1 for `NOT READY`.
Missing, failed, blocked, unknown, or unsupported evidence cannot produce `READY`.
Run failed checks again after fixing the problem. The recorder replaces the previous result for
that check.

The gate verifies recorded execution and coverage, not the meaning of arbitrary commands or
manual reviews. Inspect the commands, assertions, logs, and cluster target before claiming
migration readiness. Never claim readiness elsewhere in `MIGRATION_REPORT.md` when the gate says
`NOT READY`.

The project-readiness verdict in `project-readiness.md` is separate. Report a complete
migration only when that verdict is `ready` and the evidence gate reports `READY`.
