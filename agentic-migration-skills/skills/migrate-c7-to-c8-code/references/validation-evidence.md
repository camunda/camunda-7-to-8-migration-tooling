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
Run `init` before code conversion. It records detected due-date locations in the Step 2 inventory
so the gate can compare original C7 callers with the migrated source.

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
     "processes": [{"id": "order-process", "standalone": true, "scenarios": ["normal"]}]}
  ],
  "deployment_sets": [
    {"name": "web", "modules": ["examples/web"],
     "models": ["models/converted-c8-order.bpmn"]}
  ],
  "checks": []
}
```

Use project-relative paths. List every migrated module and every in-scope original BPMN/DMN.
Include all independent test suites from each module's build. Set `requires_docker` for each suite.
Where a module uses a Camunda Spring Boot starter, include its real-client context test as a suite.
Use `spring-boot`, `external-launcher`, or `none` for `runtime_mode`. Never set `none` for a runtime
module to skip runtime checks.

Declare each target group in `deployment_sets`. Include every converted model in exactly one set.
List every migrated module that deploys or calls models in its set. Separate groups that deploy
to different targets. The gate compares module and original-model paths with the Step 2 inventory.
It reads all BPMN process IDs, including non-executable definitions, from the source and converted
copies. It reads repeating timer starts directly under each process, not inside event subprocesses.
Include every executable process ID in its model's `processes` array. Use an empty array for DMN.

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
`.camunda-migration/validation/logs/`. It returns 0 only when the command and its required
evidence pass. A failed command or invalid timer observation returns 1 and saves its output.
Continue with other modules, models, and suites.
Run recorder invocations sequentially. The default command timeout is five minutes. Use
`--timeout <seconds>` for checks that need a different limit.
Never use a command that skips tests, checks only plugin help, or asserts only that a test file exists.
Use a bounded test that starts the application or packaged JAR. The test must assert startup before
it stops the process.

Use `review` for review checks. Give a substantive note naming the reviewed files and decisions:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type model --target models/converted-c8-order.bpmn --kind review --note "The skill checked source integrity, converter findings, form decisions, and DI."
```

### Deployment and timer decisions

Follow `references/deployment-and-timer-preflight.md` for the caller inventory and decision rules.
Record every caller and version selection in `MIGRATION_REPORT.md`, including C7 by-key calls,
C8 `bpmnProcessId` calls, and `.latestVersion()` calls. Resolve IDs from constants and
configuration. The gate checks the review reference, not caller completeness.

Record one preflight review per deployment set:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type deployment_set --target web --kind preflight --reference MIGRATION_REPORT.md#deployment-set-web --note "Reviewed all process IDs, caller sites, and version selections in the web target."
```

When source or converted BPMNs share a process ID within a set, record its decision separately.
Use `explicit_version` only when no retained timer start shares the ID. Use `mapped_rename` only
after the converted IDs stop colliding. Cite the approved mapping or caller review:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type deployment_set --target web --kind duplicate_process_id --scenario Sample --disposition explicit_version --reference MIGRATION_REPORT.md#sample-callers --note "All Sample callers select an explicit version."
```

Review each process-level repeating timer found in the source or converted copy. Record its
`add`, `change`, `preserve`, or `remove` disposition and the approved exact cycle:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type timer --target models/converted-c8-order.bpmn#order-process#Start --kind disposition --disposition preserve --reference MIGRATION_REPORT.md#order-timer --note "Approved R/PT1H and its automatic starts."
```

Review every module for C7 due-date updates, REST endpoints, helpers, and all callers.
The source scan is a conservative hint, not a complete parser. Use `no_updates` only when
no direct hint remains and the manual review found no due-date calls:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type module --target examples/web --kind active_timer_updates --disposition no_updates --note "Reviewed due-date calls and their callers; no active timer update remains."
```

When every detected hint concerns a non-timer job, use `non_timer` with evidence for each
`path:line:column` location. If a source line has two hits, then provide two records.
Use `--non-timer-evidence-json '[{"location":"examples/web/Job.java:42:17","evidence":"MIGRATION_REPORT.md#non-timer-job"}]'`.
The gate rejects placeholders such as `not applicable` as evidence.
When the project approves message-driven timer rearming, add an `active_timer_update_decision`
object to `.camunda-migration/validation/validation-evidence.json`. Use the exact converted model,
timer process, parent process, parent call activity, timer, message name, correlation-key variable,
and message date variable. Put each caller-to-source mapping in `caller_mappings`:

```json
{
  "active_timer_update_decision": {
    "status": "approved",
    "strategy": "message_rearm",
    "reference": "MIGRATION_REPORT.md#active-timer-rearm",
    "target_version": "8.9.21",
    "updates": [
      {
        "model_path": "models/converted-c8-order.bpmn",
        "process_id": "order-timer-wait",
        "rearm_process_id": "order-process",
        "rearm_call_activity_id": "WaitForTermination",
        "timer_id": "TerminationTimer",
        "message_name": "TerminationDateChanged",
        "correlation_key_variable": "projectId",
        "date_variable": "terminationDate",
        "message_date_variable": "updatedTerminationDate",
        "caller_mappings": [
          {
            "module": "examples/web",
            "source_locations": ["examples/web/LegacyTerminationService.java:42:9"],
            "migrated_caller_location": "examples/web/TerminationService.java:52:13"
          }
        ]
      }
    ]
  }
}
```

Each `caller_mappings` entry must identify one migrated caller and its module. Every
`source_locations` entry must match the Step 2 inventory captured by `init`. The
`migrated_caller_location` must identify current source code in the mapped module. Inspect each
caller and confirm that it sends the mapped message, correlation key, and date variable. Use a
separate mapping for each migrated caller, even when several callers rearm the same timer. The gate
retains all caller mappings under the shared timer decision. A C7 due-date call that remains at a
mapped source location blocks readiness. For backward compatibility, the gate also accepts the
former top-level `module`, `source_locations`, and `migrated_caller_location` fields as one mapping.

The `message_date_variable` identifies the date field in the message payload. The `date_variable`
identifies the variable read by the timer.

The `process_id` identifies the executable timer child process. List that process and its parent in
the model inventory. Mark the timer process as non-standalone and set `covering_test` to the parent
test. The timer process must use the mapped date variable in its timer expression. The parent
process must call it through the mapped `bpmn:callActivity`. The call activity must input-map the
date variable into the timer process. Attach an interrupting message boundary event to that call
activity. Map the `message_date_variable` into the parent `date_variable` on the boundary event.
Route the message branch through an exclusive converging gateway before the call activity is
entered again. Each call creates a fresh child process instance with the updated date.
The mapped module and model must appear together in at least one deployment set.

Record the module review with the same approval reference. Include every migrated caller location
for that module in the review note:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type module --target examples/web --kind active_timer_updates --disposition message_rearm --reference MIGRATION_REPORT.md#active-timer-rearm --note "Inspected examples/web/TerminationService.java:52:13. It sends TerminationDateChanged with projectId and maps updatedTerminationDate to terminationDate."
```

Run the required `active_instance_reschedule` check after the module review, converted-model lint
and review, and deployment-set preflight. Use the approved target version and a disposable local
target:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type timer --target models/converted-c8-order.bpmn#order-timer-wait#TerminationTimer --kind active_instance_reschedule --environment local --target-disposable --target-version 8.9.21 --isolation-plan "Remove the disposable target and all test state." -- <bounded-two-update-test-command>
```

The command must end with one JSON object. Its `observation.active_timer` object must identify the
mapped model, timer process, parent process and call activity, timer, strategy, message, correlation
key, and both date variables. It must record
that the timer was active before both updates. Each update must contain its old and new deadlines.
The two updates must move the deadline both earlier and later.
Each update must set `timer_active_before_update` and `correlated` to `true`. The test must advance
past both obsolete deadlines and record a zero fire count for each.
For consecutive publications, use a bounded TTL and a unique message ID, or wait for a rearm
acknowledgement before publishing the next update.
When the test confirms the process is still waiting immediately before the final deadline, record
the current test-clock time as `final_deadline_last_active_at`. Keep this timestamp no more than five
seconds before the deadline. Advance the test clock to the final deadline after this check. When the
test observes completion, record the current test-clock time as `final_deadline_fired_at`. This
timestamp must be at or after the final deadline. The outer object must identify the disposable
deployment and prove cleanup:

```json
{
  "deployment": {
    "performed": true,
    "reference": "MIGRATION_REPORT.md#active-timer-deployment",
    "environment": "local",
    "target_disposable": true,
    "target_version": "8.9.21"
  },
  "observation": {
    "active_timer": {
      "model_path": "models/converted-c8-order.bpmn",
      "process_id": "order-timer-wait",
      "rearm_process_id": "order-process",
      "rearm_call_activity_id": "WaitForTermination",
      "timer_id": "TerminationTimer",
      "strategy": "message_rearm",
      "message_name": "TerminationDateChanged",
      "correlation_key_variable": "projectId",
      "date_variable": "terminationDate",
      "message_date_variable": "updatedTerminationDate",
      "timer_was_active_before_first_update": true,
      "updates": [
        {
          "old_deadline": "2050-11-23T00:00:15Z",
          "new_deadline": "2050-11-23T00:00:05Z",
          "timer_active_before_update": true,
          "correlated": true
        },
        {
          "old_deadline": "2050-11-23T00:00:05Z",
          "new_deadline": "2050-11-23T00:00:25Z",
          "timer_active_before_update": true,
          "correlated": true
        }
      ],
      "obsolete_deadlines": [
        {"deadline": "2050-11-23T00:00:15Z", "fire_count": 0},
        {"deadline": "2050-11-23T00:00:05Z", "fire_count": 0}
      ],
      "advanced_past_obsolete_deadlines": true,
      "final_deadline": "2050-11-23T00:00:25Z",
      "final_deadline_fire_count": 1,
      "final_deadline_last_active_at": "2050-11-23T00:00:24Z",
      "final_deadline_fired_at": "2050-11-23T00:00:26Z"
    }
  },
  "cleanup": {
    "completed": true,
    "evidence_reference": "MIGRATION_REPORT.md#active-timer-cleanup"
  }
}
```

For modules that contain both active-timer and non-timer updates, use `mixed`. Supply
`--non-timer-evidence-json` for every non-timer source location. If the project has no approved
replacement, a timer link remains unknown, or manual review finds a call the scan missed, then
record a `block` check. A passing review cannot bypass an active-timer runtime check.

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
| Each module | `compile`, one `tests` check per declared suite, `review`, and `active_timer_updates` review or blocker. |
| Spring Boot runtime module | `configuration`, `spring_boot_run`, and `executable_jar`, in addition to module checks. |
| External runtime module | `configuration` and `external_launcher`, in addition to module checks. |
| Each converted BPMN/DMN | `lint`, `review`, then `deployment`. The recorder also checks XML parsing and source separation. |
| Each deployment set | A preflight review with an approved reference. |
| Each duplicate process ID in a set | An explicit-version or mapped-rename review with an approved reference. |
| Each approved active timer update | A mapped module review and `active_instance_reschedule` command on a disposable target. |
| Each standalone executable process | `worker_input_inventory` review and `process_path` for `normal` and each declared scenario. |
| Each non-standalone executable process | `process_path` with the `covering_test` as its scenario. |
| Each applicable behavior | A command check named in the assertion table below. |
| Each repeating timer start directly under a process | An approved `disposition` review. For a retained timer, a separate runtime `preflight` before deployment or process execution. |

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
Run each retained repeating timer preflight on a disposable local or non-production target.
The command must deploy the converted copy, observe a timer-created instance, and complete
cleanup. Supply the selected Camunda 8 patch version and a cleanup plan:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type timer --target models/converted-c8-order.bpmn#order-process#Start --kind preflight --environment local --target-disposable --target-version 8.9.21 --isolation-plan "Destroy the disposable target." -- <bounded-disposable-test-command>
```

After cleanup, the command must print one JSON object on its last stdout line.
Replace the sample evidence references with records from the actual test:

```json
{"deployment":{"performed":true,"reference":"deployment-record","environment":"local","target_disposable":true,"target_version":"8.9.21"},"observation":{"model_path":"models/converted-c8-order.bpmn","process_id":"order-process","start_id":"Start","cycle":"R/PT1H","instances_started":1},"cleanup":{"completed":true,"evidence_reference":"cleanup-record"}}
```

The gate checks the model path, process ID, event ID, exact cycle, observed starts, target,
and completed cleanup. It cannot inspect the remote target. Never substitute a print-only
command for the disposable test. If no safe target exists, then block the preflight.
Do not deploy or start that model.

Each check stores a fingerprint of the declared modules, source and converted models, root build
configuration, deployment-set membership, and active timer decision. Changes make old checks stale.
Refresh stale checks before a dependent command or readiness claim. The recorder rejects a
dependent command before it runs when its review, lint, or timer preflight is stale.

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
