# Migration Validation Evidence

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

An **evidence manifest** is the JSON file that records each required validation check. The validator
uses this file to produce the aggregate validation gate in `MIGRATION_REPORT.md`.

## Evidence files

1. Before conversion, save the confirmed Step 2 scope in `.camunda-migration/validation/step2-inventory.json`.
2. List every migrated code module directory once in the inventory `modules` array.
3. List every in-scope original model path in the inventory `models` array.
4. Keep the Step 2 inventory separate from the evidence manifest. The validator requires exact path-set equality.
5. Use one canonical path for each module directory. The validator resolves module paths before it checks uniqueness and inventory completeness.
6. Create `.camunda-migration/validation/validation-evidence.json` in the confirmed project root.
7. Capture command output under `.camunda-migration/validation/logs/`.
8. Use files under `.camunda-migration/validation/logs/` for every `evidence_path`.
9. Never use the inventory, manifest, generated summary, or `MIGRATION_REPORT.md` as evidence.
10. Remove credentials and secret values from every command and log.
11. List every independent test suite for each module.
12. List every process in each converted BPMN model, including non-executable processes.
13. List every repeating timer start in each converted BPMN model.
14. Use project-relative paths for every target and evidence file.

The validator checks process IDs and executable values against each converted BPMN model. The
validator checks each timer process ID and timer-start ID against the converted BPMN. It requires a
timer preflight for each repeating timer in an executable process.

Use `../scripts/validation-inventory.schema.json` for the Step 2 inventory. Use
`../scripts/validation-evidence.schema.json` for the evidence manifest.

Run this gate only for a full migration. Assessment-only and analyze-only runs do not claim
migration readiness.

The validator also checks required check coverage, project-relative targets, and evidence files.
Run it with Python 3:

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

Keep the evidence manifest, summary, and report in distinct files.
Keep the summary path distinct from the Step 2 inventory and `MIGRATION_REPORT.md`, even when
`--report` is omitted.
Keep the report path distinct from the Step 2 inventory.
The validator reserves the default evidence and summary paths from custom outputs.
Use each default path only for its matching output.
Keep the summary and report paths outside `.camunda-migration/validation/logs`.
Keep the summary and report paths distinct from every evidence file.
The validator rejects output paths that identify a manifest evidence file.
The validator checks file identity, including hard links, before it writes output.
The validator scans the evidence log directory independently of the manifest before it writes output.

Each check record gives its target type, target, check kind, scenario, method, command, exit code,
result, evidence path, reason, blocker reason, failure class, and environment. A timer preflight
record also gives its isolation or cleanup plan. Use these result values:

Set `command` to the exact invocation for each command check. Use one direct command. Do not use a
shell no-op, inline interpreter code, or shell operators. The validator rejects these forms for every
passed command check.

The validator rejects carriage returns and line feeds in passed commands. It matches module and
model targets by exact relative paths or absolute paths with exact target components at the end.
Path comparisons use platform case rules. The validator matches process IDs, timer-start IDs, and
process or timer check selectors by exact argument values.

For module tests, include the module path and test suite name. For model lint and deployment, include
the model path and operation. For process checks, include the process ID and check selector. For
timer preflights, include the process ID, timer-start ID, and check selector. The validator checks
these targets and selectors before it accepts a passed result.

For Maven tests, select a module with `-pl` or `--projects`, or select its `pom.xml` with `-f` or
`--file`. For Gradle tests, select a project with `-p` or `--project-dir`, or use a qualified test
task. Record the module suite scenario as an exact profile, scenario value, or test selector. A test
class selector can use the scenario name with `Test`, `Tests`, or `IT` appended. The validator accepts
a test pattern only when it selects a class name derived from the scenario. Omit the test selector
only for the default Maven `unit` suite.

Never mark a test, process, or timer check as passed when its command skips tests, excludes a test
task, requests a dry run, or ignores test failures. Maven suppression options include `-DskipTests`,
`-Dmaven.test.skip`, `-DskipITs`, and `-Dmaven.test.failure.ignore`. Gradle suppression options
include `-x`, `--exclude-task`, `--dry-run`, and `--test-dry-run`.

| Result | Meaning | Required evidence |
|---|---|---|
| `passed` | The check ran and passed. | A command check needs exit code 0 and a non-empty evidence file. A manual check needs a non-empty evidence file. |
| `failed` | The check ran and found a migration defect. | Failure class, reason, and a non-empty evidence file. A command check also needs an exit code. |
| `blocked` | An environment or dependency stopped the check. | Failure class, blocker reason, and captured output when a command ran. |
| `unknown` | The available evidence does not show whether the check passed. | Failure class and blocker reason. |
| `not_run` | The skill did not run a required check. | Blocker reason. |
| `not_applicable` | The check does not apply to this target. | A reason and no command result. |

Never mark a command check as `passed` when its command did not run. Use `blocked`, `unknown`, or
`not_run` when a command check did not run. Mark a check `not_applicable` only when its condition
does not apply. State the reason.

Set `method` to `manual` only for the checks in this table. Use `command` for every other check.
Pass a manual check only after completing its review and saving non-empty evidence.

| Target | Check kinds that allow manual evidence |
|---|---|
| Module | `migration_todos`, `business_keys`, `eventual_consistency`, `pagination`, `worker_adapters`, `deployment_resources` |
| Model | `findings_verdicts`, `generated_forms`, `form_references`, `form_binding`, `semantic_id_references`, `task_definition_types` |
| Process | `worker_input_inventory` |

Set `exit_code` to null for a manual check. Save the manual review evidence under
`.camunda-migration/validation/logs/`.

## Inventory fields

Use the fields below to build the required check set.

| Entry | Required values |
|---|---|
| Module | `path`, `runtime_mode`, and each `test_suites` name with its `requires_docker` value. |
| Model | Converted `path`, original `source_path`, `type`, `approach`, `deployable`, `source_has_di`, and `form_inventory`. |
| Process | Every process ID, its `executable` value, its standalone-entry-point value, direct-start scenarios, a reviewed worker-input inventory, and assertion applicability for each executable process. |
| Repeating timer | The process ID and timer-start ID for each repeating timer start. |
| Form | Every Generated Task Form, referenced form, and form-free owner for each model. |

Set `deployable` to `true` for every converted model. The full-migration gate requires safe deployment
evidence for every BPMN and DMN model. A false value cannot waive deployment.

Each model has a `form_inventory` array. Add one record for each source Generated Task Form,
referenced form, and form-free owner. Each record has `id`, `kind`, `accepted`, `schema_applicable`,
`form_js_applicable`, and `binding_required`. Set `kind` to `generated`, `referenced`, or
`form-free-owner`. Set form applicability fields from the source inventory and the accepted
migration decision. Set a `referenced` record ID to its source form key, form reference, or
form-definition ID. Set `generated` and `form-free-owner` record IDs to their BPMN owner IDs. Use a
unique ID for each record. Use an empty array when the model has no form records or form-free owners.

The validator parses every source and converted model as BPMN or DMN XML. It compares the declared
type with each detected definitions root.
The validator detects BPMN DI and form categories from each source BPMN. It compares the detected
values with `source_has_di` and `form_inventory` before it derives conditional checks.
The validator derives process assertion applicability from converted BPMN elements and accepted
form inventory. It rejects any manifest value that differs from the derived value. If it cannot
inspect a process, it requires every assertion to pass.

The validator derives conditional form checks from these records:

| Check kind | Applicable when |
|---|---|
| `accepted_forms`, `form_parsing`, `form_definition`, `form_deployment` | At least one record has `accepted: true`. |
| `form_schema` | At least one accepted record has `schema_applicable: true`. |
| `form_js` | At least one accepted record has `form_js_applicable: true`. |
| `form_references` | At least one record has `kind: referenced` or `kind: form-free-owner`. |
| `form_binding` | At least one accepted record has `binding_required: true`. |

An applicable form check must pass. A non-applicable form check must be `not_applicable` with a
reason. Set `accepted` and all applicability fields to `false` for a `form-free-owner` record. Set
`schema_applicable`, `form_js_applicable`, and `binding_required` to `false` when `accepted` is
`false`.

List every process, including non-executable processes. Set `executable` to `false` and give a
reason for a non-executable process. For each standalone executable process, list every scenario
that omits a worker input in `missing_worker_input_scenarios`. Use the same scenario names in
`direct_start_scenarios`, with `normal` included. Use an empty array when the inventory has no
missing-input scenarios.

Add one passing `worker_input_inventory` check for each standalone executable process. Review the
converted BPMN worker mappings and worker declarations. Save the input-to-scenario mapping in the
check evidence log. The validator requires direct-start checks for `normal`, every inventoried
scenario, and every additional declared scenario. It rejects an inventory scenario that is absent
from `direct_start_scenarios`.

For a process that is not a standalone entry point, list no direct-start or missing-worker-input
scenarios. Give its reason and covering test.

The validator detects each converted model type from its XML definitions root. Reject any `type`
value that differs from the detected type. For BPMN models, the `processes` array must match every
top-level `bpmn:process` ID and `isExecutable` value. Include non-executable process definitions.
Keep `processes` and `recurring_timer_starts` empty for DMN models.

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
Set `runtime_mode` to `spring-boot`, `external-launcher`, or `none` for every module.

| `runtime_mode` | Required launch-check results |
|---|---|
| `spring-boot` | Record the result for exactly one of `spring_boot_run` and `executable_jar`. Mark the other Spring Boot check and `external_launcher` as `not_applicable`. |
| `external-launcher` | Pass `external_launcher`. Mark both Spring Boot checks as `not_applicable`. |
| `none` | Mark all three launch checks as `not_applicable`. |

The validator rejects `runtime_mode: none` when `src/main` files declare a Java, Kotlin, Groovy, or
Scala `main` entry point.
The validator rejects it when `src/main` files contain a fully qualified or unqualified Spring Boot
startup annotation or `SpringApplication.run` call.
The validator rejects it when the module `pom.xml` configures a main class.
The validator rejects it when a JAR under `target/` declares `Main-Class` or `Start-Class` in its
manifest.

Run each selected runtime launch check only with local or non-production settings. Never start a
production worker as a validation probe.

Run each required check independently.
Give each executed check a distinct evidence file.
Give each test suite a distinct command.
The validator enforces this contract across all modules.
The validator adds `unit` for Maven modules with JVM test sources or an active
`maven-surefire-plugin`. It adds `test` for Gradle builds with a test-capable plugin or JVM test
sources. Use `unit` for the default Maven Surefire suite. Use `test` for the default Gradle task.
Use Maven Failsafe execution IDs and Gradle task names for other suites. The validator checks every declared
`test_suites` entry against detected suites. It rejects undeclared build suites and manifest suites
without matching build declarations. It detects explicit Gradle `Test` tasks declared with
`register`, `create`, or `named`, plus `JvmTestSuite` declarations. Use `integration` when a Failsafe
execution has no ID.
A failed Docker or Testcontainers suite does not stop the skill from running other suites or module
checks. Do not summarize all test failures as Docker failures.

Before a Docker-dependent suite, run `docker info` and record a project check with kind
`docker_info`. Record the probe even when Docker works. Use failure class `docker_unavailable` only
when the probe fails. Use `testcontainers` when the daemon works but Testcontainers cannot select a
valid environment. Keep check records in execution order. The Docker probe must precede its
dependent test suite. Record a direct `docker info` invocation as the command. A command that only
mentions these words does not satisfy the probe.

## Required model and process checks

The converted model path must resolve to a different file from its source path.
The validator rejects source and converted paths that resolve to or identify the same file.
The validator skips model files that resolve outside the project root, including paths reached
through symlinks.

Add one record for every model check kind in the following table. Use `not_applicable` with a reason
when a condition does not apply.

| Model check kind | Required evidence |
|---|---|
| `converted_copy` | The converted copy exists for the in-scope model. |
| `xml_parse` | The source and converted model parse. |
| `lint` | Target-compatible lint output for the model. |
| `deployment` | Safe deployment evidence for every converted BPMN and DMN model. |
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

For every standalone executable process, add one `direct_start` record for `normal` and each
scenario in `missing_worker_input_scenarios`. The `worker_input_inventory` check must pass before
the validator accepts these scenarios. Where a process is not a standalone entry point, record its
reason and a separate `process_coverage` check for its covering test. Do not add a `direct_start`
record for a process that is not a standalone entry point.

Add an `assertion_applicability` object with one boolean for every assertion below in each executable
process inventory entry. Set each value to the applicability that the validator derives from the
converted BPMN and form inventory. The validator requires each applicable assertion to pass. It
requires each non-applicable assertion to be `not_applicable` with a reason.

| Check kind | Required assertion | Applicable when |
|---|---|---|
| `user_task_type` | Assert the migrated user-task type and resulting user task. | The converted process contains a `bpmn:userTask`. |
| `downstream_message_instance` | Assert the message starts the expected downstream process instance. | The converted process contains a message event or a send/receive task with `messageRef`. |
| `branch_selection` | Assert each tested gateway condition selects the expected branch. | The converted process contains a BPMN gateway. |
| `worker_input_output` | Assert the actual worker input and output values. | The converted process contains a Zeebe `taskDefinition` or `ioMapping`. |
| `incident_behavior` | Assert the expected incident or BPMN error for each tested failure path. | The converted process contains a Zeebe job mapping or BPMN error event. |
| `form_resolution` | Assert each accepted or relinked form resolves for its owner. | The converted process has form metadata, or the source form inventory maps an accepted form to it. |

List every repeating timer start in `recurring_timer_starts`. Run a separate timer preflight before
deployment or process start. Record the target environment and a non-empty
`isolation_or_cleanup_plan` on its check record. State how the environment is isolated or how the
timer's deployment and instances are removed. If the skill cannot protect the project from repeated
timer starts, then block the check. Do not use a shared or production cluster for this preflight.
Keep its check record before deployment and process-start records in the manifest.

## Aggregate gate

The validator requires all check records derived from the module, model, process, and timer
inventories. It compares manifest paths with the independent Step 2 inventory. It rejects
duplicate, missing, unexpected, or malformed records. It rejects a passing
command without exit code 0 or a non-empty evidence file. It rejects generic passed commands that
do not invoke the declared target and check. It rejects manual evidence for an
unlisted check kind. It rejects evidence outside the validation log directory.
It rejects evidence files reused by executed checks and reused test-suite commands.
It rejects a timer preflight without an isolation or cleanup plan. It rejects an unsafe deployment
environment.

| Check result | Gate effect |
|---|---|
| Every required check passes or has a justified `not_applicable` result. | The gate reports `READY`. |
| Any required check fails, is blocked, is unknown, or was not run. | The gate reports `NOT READY`. |
| Any required record or evidence file is missing. | The gate reports `NOT READY`. |
| A manifest path differs from the Step 2 inventory. | The gate reports `NOT READY`. |
| A check is `not_applicable` without a reason. | The gate reports `NOT READY`. |

Run every independent check before the final gate. Fix the evidence manifest when the validator
reports a missing or malformed record. Re-run failed checks before changing their result to
`passed`.

The validator writes the aggregate gate block in `MIGRATION_REPORT.md`. Use that block as the
validation-readiness summary. The gate does not replace the migration exit criteria in `SKILL.md`.
Never report the migration as ready when the gate says `NOT READY`.
A validation-status line outside a complete marked gate is malformed.
The validator removes a markerless gate section when the next heading provides a safe boundary.
If no section boundary exists, then the validator removes only the status line.
The validator removes duplicate marker regions separately to preserve intervening report sections.
If unmatched markers do not provide a safe boundary, then the validator leaves the report
unchanged and writes a `NOT READY` summary.
