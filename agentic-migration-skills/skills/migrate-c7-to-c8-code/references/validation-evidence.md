# Migration Validation Evidence

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

An **evidence manifest** is the JSON file that records each required validation check. The validator
uses this file to produce the aggregate validation gate in `MIGRATION_REPORT.md`.

## Evidence files

1. Create `.camunda-migration/validation/validation-evidence.json` in the confirmed project root.
2. Capture command output under `.camunda-migration/validation/logs/`.
3. Remove credentials and secret values from every command and log.
4. List every migrated code module and every in-scope model from the Step 2 inventories.
5. List every independent test suite for each module.
6. List every executable process and every repeating timer start.
7. Use project-relative paths for every target and evidence file.

Run this gate only for a full migration. Assessment-only and analyze-only runs do not claim
migration readiness.

Use the contract in `../scripts/validation-evidence.schema.json`. The validator also checks
required check coverage, project-relative targets, and evidence files. Run it with Python 3:

On macOS or Linux, run:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . --evidence .camunda-migration/validation/validation-evidence.json --report MIGRATION_REPORT.md
```

On Windows PowerShell, run:

```powershell
py -3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . --evidence .camunda-migration/validation/validation-evidence.json --report MIGRATION_REPORT.md
```

The validator writes its full JSON summary to
`.camunda-migration/validation/validation-summary.json`. A ready gate returns exit code 0. A
not-ready gate returns exit code 1. With `--report`, the validator writes the matching gate block.
An unreadable manifest returns exit code 1. Do not treat a nonzero exit code as a successful
validation.

Each check record gives its target type, target, check kind, scenario, method, command, exit code,
result, evidence path, reason, blocker reason, failure class, and environment. Use these result
values:

| Result | Meaning | Required evidence |
|---|---|---|
| `passed` | The check ran and passed. | Exit code 0 and a non-empty evidence file. |
| `failed` | The check ran and found a migration defect. | Failure class, reason, exit code, and a non-empty evidence file. |
| `blocked` | An environment or dependency stopped the check. | Failure class, blocker reason, and captured output when a command ran. |
| `unknown` | The available evidence does not show whether the check passed. | Failure class and blocker reason. |
| `not_run` | The skill did not run a required check. | Blocker reason. |
| `not_applicable` | The check does not apply to this target. | A reason and no command result. |

Never mark a check as `passed` when its command did not run. Use `blocked`, `unknown`, or `not_run`.
Mark a check `not_applicable` only when its condition does not apply. State the reason.

## Inventory fields

Use the fields below to build the required check set.

| Entry | Required values |
|---|---|
| Module | `path`, `runtime_mode`, and each `test_suites` name with its `requires_docker` value. |
| Model | Converted `path`, original `source_path`, `type`, `approach`, `deployable`, and `source_has_di`. |
| Process | Every process ID, its `executable` value, its standalone-entry-point value, and direct-start scenarios. |
| Repeating timer | The process ID and timer-start ID for each repeating timer start. |

List every process, including non-executable processes. Set `executable` to `false` and give a
reason for a non-executable process. List `normal` and every missing-worker-input scenario in
`direct_start_scenarios`. For a process that is not a standalone entry point, list no direct-start
scenarios. Give its reason and covering test.

## Required module checks

Add one record for each check kind below for every migrated code module. Add one `tests` record for
each suite in that module's `test_suites` inventory. Use the suite name as the scenario.

| Check kind | Evidence |
|---|---|
| `compile` | Module compile command and result. |
| `c7_dependencies` | Search for Camunda 7 dependencies. |
| `c7_imports` | Search for Camunda 7 imports. |
| `migration_todos` | Search and review migration TODOs. |
| `legacy_client` | Search for the legacy Camunda 8 client. |
| `business_keys` | Review every remaining business-key use. |
| `configuration` | Run the configuration validation. |
| `tests` | Run one independent module test suite. |
| `eventual_consistency` | Search every migrated search factory and record open items. |
| `pagination` | Review every count and pagination use. |
| `worker_adapters` | Compare every worker declaration with the original Java inventory. |
| `deployment_resources` | Compare packaged resources with every deployment pattern. |
| `spring_boot_run` | Run `mvn spring-boot:run` for a Spring Boot runtime module. |
| `executable_jar` | Run `java -jar` on a Spring Boot runtime module's artifact. |
| `external_launcher` | Run the recorded external launcher for an externally launched module. |

If a module has no test suite, then add a `tests` record with scenario `no-tests`. Set its result to
`blocked` or `unknown` and state why no test ran. The gate cannot report readiness without tests.
Set `runtime_mode` to `spring-boot`, `external-launcher`, or
`none` for every module. Record the launch check that matches that mode. Mark the other launch
checks `not_applicable`. If a module has no runtime entry point, then set all three launch checks to
`not_applicable`. Run each runtime launch check only with local or non-production settings. Never
start a production worker as a validation probe.

Run each module and test suite independently. A failed Docker or Testcontainers suite does not stop
the skill from running other suites or module checks. Do not summarize all test failures as Docker
failures.

Before a Docker-dependent suite, run `docker info` and record a project check with kind
`docker_info`. Record the probe even when Docker works. Use failure class `docker_unavailable` only
when the probe fails. Use `testcontainers` when the daemon works but Testcontainers cannot select a
valid environment. Keep check records in execution order. The Docker probe must precede its
dependent test suite.

## Required model and process checks

Add one record for every model check kind in the following table. Use `not_applicable` with a reason
when a condition does not apply.

| Model check kind | Required evidence |
|---|---|
| `converted_copy` | The converted copy exists for the in-scope model. |
| `xml_parse` | The source and converted model parse. |
| `lint` | Target-compatible lint output for the model. |
| `deployment` | Safe deployment evidence for each deployable model. |
| `source_preservation` | Evidence that the original model remains unchanged. |
| `resource_packaging` | Evidence that findings reports are not packaged as application resources. |
| `findings_verdicts` | Complete findings and their category/impact verdicts. |
| `generated_forms` | Inventory and status of every source Generated Task Form. |
| `accepted_forms` | Evidence that each accepted form uses the Camunda 8 form format. |
| `form_parsing` | Parser result for each accepted form. |
| `form_schema` | Target-compatible schema result where a schema exists. |
| `form_js` | Form-js import or render result where compatible tooling exists. |
| `form_definition` | Link check for each accepted form. |
| `form_deployment` | Deployment check for every accepted form and its BPMN. |
| `form_references` | Decision for each referenced form and form-free owner. |
| `form_binding` | Binding decision for every relinked or rebuilt form. |
| `converter_cleanup` | Search for converter annotations and leftover C7 constructs. |
| `bpmn_di` | Diagram interchange preservation check for each BPMN model. |
| `source_di_provenance` | Evidence that a source without DI did not gain layout data. |
| `semantic_id_references` | Evidence for every changed semantic ID referenced by DI. |
| `task_definition_types` | Source-to-target type check for the M2 approach. |

Lint every in-scope BPMN and DMN model. Use the target-compatible ruleset. Do not lint only the
models that the skill edited.

Deploy models only to a local or non-production environment. Record that environment on every
deployment check. Never deploy to production to validate a migration.

Run process-start and behavior checks only in a local or non-production environment. Record the
target environment on each applicable check.

For every executable process, add one `direct_start` record for each declared start scenario. Include
the normal inputs and each worker input that the process may not receive. If a process is not a standalone entry point, then record its reason and a separate
`process_coverage` check for its covering test.

Add each assertion below for every executable process. If a process has no such behavior, then mark
its check `not_applicable` and state why.

| Check kind | Required assertion |
|---|---|
| `user_task_type` | Assert the migrated user-task type and resulting user task. |
| `downstream_message_instance` | Assert the message starts the expected downstream process instance. |
| `branch_selection` | Assert each tested gateway condition selects the expected branch. |
| `worker_input_output` | Assert the actual worker input and output values. |
| `incident_behavior` | Assert the expected incident or BPMN error for each tested failure path. |
| `form_resolution` | Assert each accepted or relinked form resolves for its owner. |

List every repeating timer start in `recurring_timer_starts`. Run a separate timer preflight before
deployment or process start. Record the target environment and the isolation or cleanup plan. If the
skill cannot protect the project from repeated timer starts, then block the check. Do not use a
shared or production cluster for this preflight. Keep its check record before deployment and
process-start records in the manifest.

## Aggregate gate

The validator requires all check records derived from the module, model, process, and timer
inventories. It rejects duplicate, missing, unexpected, or malformed records. It rejects a passing
command without exit code 0 or a non-empty evidence file. It rejects an unsafe deployment
environment.

| Check result | Gate effect |
|---|---|
| Every required check passes or has a justified `not_applicable` result. | The gate reports `READY`. |
| Any required check fails, is blocked, is unknown, or was not run. | The gate reports `NOT READY`. |
| Any required record or evidence file is missing. | The gate reports `NOT READY`. |
| A check is `not_applicable` without a reason. | The gate reports `NOT READY`. |

Run every independent check before the final gate. Fix the evidence manifest when the validator
reports a missing or malformed record. Re-run failed checks before changing their result to
`passed`.

The validator writes the aggregate gate block in `MIGRATION_REPORT.md`. Use that block as the
validation-readiness summary. The gate does not replace the migration exit criteria in `SKILL.md`.
Never report the migration as ready when the gate says `NOT READY`.
