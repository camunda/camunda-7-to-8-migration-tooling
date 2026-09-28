# Migration Validation Evidence

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

The recorder runs commands and saves their arguments, exit codes, and output. The gate compares
those records with the Step 2 scope. It writes the result in `MIGRATION_REPORT.md`.
Run the gate only after a full migration. Assessment-only and analyze-only runs do not claim
readiness.

## Prepare the scope

After the user confirms a full migration, save the in-scope Step 2 paths in
`.camunda-migration/validation/step2-inventory.json` before conversion:

```json
{"schema_version":1,"modules":["examples/web"],"models":["models/order.bpmn"]}
```

Where E1 fetches a model, add its original path after retrieval and before conversion.

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

For each standalone process, list `normal` and every missing-worker-input scenario in `scenarios`.
Record its worker-input review before starting it. For a non-standalone process, set
`standalone: false`, `scenarios: []`, and `covering_test` to the parent process test name.
Run that test as the process-path check. Document the reason it is not a standalone entry point in
`MIGRATION_REPORT.md`.

The recorder maintains `checks`. Never write a `passed` command result into that array manually.
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

Use `review` for the three review kinds. Give a substantive note naming the reviewed files and decisions:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type model --target models/converted-c8-order.bpmn --kind review --note "The skill checked source integrity, converter findings, form decisions, and DI."
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
| Spring Boot runtime module | `configuration`, `spring_boot_run`, and `executable_jar`, in addition to module checks. |
| External runtime module | `configuration` and `external_launcher`, in addition to module checks. |
| Each converted BPMN/DMN | `lint`, `review`, then `deployment`. The recorder also checks XML parsing and source separation. |
| Each standalone executable process | `worker_input_inventory` review and `process_path` for `normal` and each declared scenario. |
| Each non-standalone executable process | `process_path` with the `covering_test` as its scenario. |
| Each applicable behavior | A command check named in the assertion table below. |
| Each repeating timer start in an executable process | A separate `preflight` before model deployment or process execution. |

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
Run each repeating timer preflight on an isolated local cluster. Include
`--isolation-plan "<cleanup or isolation steps>"` and `--environment local`.
If no safe plan exists, then block the preflight and do not deploy or start that model.

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
