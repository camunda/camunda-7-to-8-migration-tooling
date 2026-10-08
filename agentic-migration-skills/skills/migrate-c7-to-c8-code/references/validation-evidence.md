# Migration Validation Evidence

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

The recorder runs commands and saves their arguments, exit codes, and output. The gate compares
those records with the Step 2 scope. It writes the result in `MIGRATION_REPORT.md`.

## Prepare the scope

When the user approves a full migration, save the in-scope Step 2 paths in
`.camunda-migration/validation/step2-inventory.json` before conversion:

```json
{"schema_version":1,"modules":["examples/web"],"models":["models/order.bpmn"]}
```

When Question 8 applies, also record `"test_run_mode": "run"` or
`"test_run_mode": "migrate_only"` in this inventory. Omit the field when Question 8 does not apply.

Where E1 fetches a model, add its original path after retrieval and before conversion.
Then start a new validation run before recording checks:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . init
```

`init` assigns a new run ID and marks previous readiness `NOT READY`. Run it again to invalidate old
check logs without replacing the source snapshot. Run `init` before code conversion to capture
due-date locations and operations in the Step 2 inventory. Do not change its scope, run ID, or
source snapshot during the migration. For deferred test verification, change `test_run_mode` from
`migrate_only` to `run` as described in `references/test-migration.md`. Keep the Test Inventory and
`test_suites` unchanged. The validator rejects any other change to them after Step 2. Do not run
`init` for this transition because it clears earlier validation checks. The gate detects retained operations in
the same source file even when arguments, line numbers, or formatting change.
After `init`, the source snapshot detects additions, changes, and removals of each source model's
sibling `converted-c8-*` copy, even outside selected modules.

When the user selects `Run tests`, add `test_run_mode: "run"` and `test_suites` to the Step 2
inventory:

```json
{
  "schema_version": 1,
  "modules": ["examples/web"],
  "models": ["models/order.bpmn"],
  "test_run_mode": "run",
  "test_suites": [
    {
      "module": "examples/web",
      "name": "unit",
      "command": ["mvn", "-B", "-pl", "examples/web", "test"],
      "test_ids": ["examples/web:com.example.OrderTest#testOrder"]
    }
  ]
}
```

Where deferred verification is planned, a **Migrate tests only** inventory declares `test_suites` in
Step 2. The validator rejects suites added later, because the source snapshot does not cover them.
Where deferred verification is not planned, a **Migrate tests only** inventory omits `test_suites`:

```json
{
  "schema_version": 1,
  "modules": ["examples/web"],
  "models": ["models/order.bpmn"],
  "test_run_mode": "migrate_only"
}
```

Set each suite's `module`, a `name` that is distinct within its module, the exact C7 `command`, and
`test_ids` from the Test Inventory. When `test_run_mode` is `run`, assign every migratable test to at
least one suite. The validator runs `command` without a shell for the C7 baseline.
Where the build uses custom JUnit report paths, set `reports` to a list of module-relative globs.
The default report paths are Maven Surefire, Maven Failsafe, and Gradle test-result XML files.
Where Camunda 7 coverage reports use another path, set `coverage_reports` on the matching suite to
module-relative globs. The default Camunda 7 coverage paths are
`target/process-test-coverage/**/report.json` and `target/process_test_coverage/**/*.json`.
Where a suite uses custom test source or resource directories, list each project-relative path in
`test_source_roots` or `test_resource_roots` before `init`. Each path must remain inside that suite's
module. Each configured root must exist as a directory when the freeze check runs.
Where a deferred suite uses custom test roots under `target` or `build`, declare those roots in the
initial `migrate_only` inventory so the source snapshot includes their files.
Where a configured root uses generated files under `target` or `build`, generate those files before
`init`.

When `test_run_mode` is `run`, capture the C7 baseline immediately after `init`, as
[Test checks](#test-checks) describes.

When starting a new migration from a restored C7 baseline, confirm the Step 2 scope and reset the
snapshot before conversion:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . init --reset-source-snapshot
```

Never reset the snapshot using converted source code. Where the project uses Git, add
`.camunda-migration/validation/` to its `.gitignore`. Never commit generated logs, manifests, or
summaries.

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
Never use a command that skips tests for a test check, checks only plugin help, or asserts only that
a test file exists.
Use a bounded test that starts the application or packaged JAR. The test must assert startup before
it stops the process.

### Test checks

When `test_run_mode` is `run`, record these checks. `test-migration.md` defines when the skill runs
each check and the decisions behind it. When `test_run_mode` is `migrate_only`, follow
[Migrate tests only](#migrate-tests-only) instead.

**C7 baseline.** Run each suite that contains a migratable test immediately after `init`, before
Step 3 changes any file:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target examples/web --kind c7_baseline --scenario unit -- mvn -B -pl examples/web test
```

Use the exact command recorded in `step2-inventory.json`. The validator rejects a different command.
The validator rejects a baseline run after any Step 2 source file changes. The Step 2 source
snapshot hashes each existing Test Inventory file and every file under each configured test source
or resource root, including roots under `target` and `build`.

The validator parses JUnit XML with Python's standard library. It reads Surefire files from
`target/surefire-reports/TEST-*.xml`, Failsafe files from `target/failsafe-reports/`, and Gradle
files from `build/test-results/**/`. It maps each invocation to
`<module>:<fully qualified class>#<method>`. The `method` part can be a framework display name,
including spaces and punctuation, such as a Cucumber scenario or Spock feature name. The validator
preserves a report name that exactly matches a C7 Test Inventory ID or mapped CPT test ID. It
removes parameter and repeat suffixes from other JUnit report names before matching them. This table
maps invocation results to the method result:

| Invocation results | Method result |
|---|---|
| Every invocation passed | `passed` |
| One or more invocations errored | `error` |
| No invocation errored and one or more failed | `failed` |
| No invocation errored or failed and one or more skipped | `skipped` |

The baseline check passes when it captures a fresh report for every suite Test ID, even when the C7
command exits nonzero. The validator copies JUnit reports to `.camunda-migration/validation/baseline/`
and stores one result for each Test Inventory ID in `.camunda-migration/validation/test-mapping.json`.
A failed or skipped C7 test remains visible in that ledger. Where the project uses Git, the validator
records the Step 2 Git commit. It matches each baseline-suite record against its recorded
`c7_baseline` check before it uses the record for parity. Where a fresh C7 report contains a
`Report only` test that the suite omits, the validator also records that test. Where Camunda 7
process-test-coverage reports exist, the validator copies and parses their JSON reports. It records
covered flow-node and sequence-flow IDs under each `modelKey` and maps those IDs to the covered
process.

When a suite cannot run, record it as blocked:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . block --type module --target examples/web --kind c7_baseline --scenario unit --reason "The suite requires an unavailable database."
```

Record the user's approval to continue without that baseline in `test-mapping.json`:

```json
{
  "baseline": {
    "continue_without_baseline": {
      "decision": "continue",
      "reason": "The approved database is unavailable.",
      "approved_by": "operator"
    }
  }
}
```

**Test parity ledger.** The validator owns `.camunda-migration/validation/test-mapping.json`. It
writes `c7_result` from JUnit reports. Never write `c7_result` by hand. Record each test mapping and
each approval in this shape:

```json
{
  "schema_version": 1,
  "baseline": {
    "commit": "<Step 2 Git commit or null>",
    "source_digest": "<Step 2 source snapshot digest>",
    "suites": [
      {
        "module": "examples/web",
        "suite": "unit",
        "command": ["mvn", "-B", "-pl", "examples/web", "test"],
        "result": "passed",
        "reports": ".camunda-migration/validation/baseline/<suite-id>/junit",
        "report_files": [
          ".camunda-migration/validation/baseline/<suite-id>/junit/target/surefire-reports/TEST-OrderTest.xml"
        ],
        "coverage_reports": null,
        "coverage_report_files": [],
        "coverage_available": false,
        "coverage_by_process": {},
        "test_results": {
          "examples/web:com.example.OrderTest#testOrder": {
            "result": "passed",
            "invocations": ["passed"],
            "invocation_count": 1
          }
        },
        "evidence_path": ".camunda-migration/validation/logs/<check-id>.json"
      }
    ],
    "coverage_available": false,
    "coverage": {},
    "continue_without_baseline": null
  },
  "tests": [
    {
      "c7_id": "examples/web:com.example.OrderTest#testOrder",
      "test_kind": "process test",
      "handling": "Migrate",
      "c7_result": "passed",
      "c8_ids": ["examples/web:com.example.OrderCptTest#testOrder"],
      "mocks": {
        "c7": ["Mocks.register(\"orderService\", mock)"],
        "c8": ["@MockitoBean OrderService"]
      },
      "status": "migrated"
    }
  ],
  "freeze": {"files": {}},
  "test_changes": [],
  "mock_changes": []
}
```

When a ledger edit changes a test check's digest, the validator ignores that stale record. Record
each still-required check again. The validator includes frozen test-file hashes and approved
`test_changes` in assertion-strength and mock-boundary review digests. When the hashes or approvals
change, record each required review again.
When all C7 tests are retired and no migrated or added `c8_ids` remain, the validator still checks
the C7 baseline and retired disposition. It does not require `test_freeze` or `test_repeat`. It
skips target coverage comparison because no CPT tests remain.

**Freeze.** After the test migration and before production-code migration, freeze test files and
resources:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind test_freeze
```

The validator hashes each existing file named by a migrated Test Inventory row. It also hashes every
file under `src/test/` in each module with a migrated or added CPT test. For each suite with migrated
or added CPT tests, it hashes every file under the configured `test_source_roots` and
`test_resource_roots`. The source snapshot also locks the configured root paths before migration
starts. The validator stores the original freeze digest in its `test_freeze` check log. Keep
`freeze.files` unchanged after the first freeze. If the ledger differs from the logged digest, then
the validator rejects it. The validator rejects each changed hash without a matching approval in
`test_changes`.

**Repeat runs.** For each migrated suite, run the CPT command twice with `test_repeat`:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target examples/web --kind test_repeat --scenario unit -- mvn -B -pl examples/web test
```

The validator parses and preserves each run's JUnit XML. It maps parameterized and repeated
invocations to their method IDs. It compares each method's result and invocation results across the
two runs. A difference marks the suite flaky. A failed command also fails the repeat check.

**Reviews.** Record one `assertion_strength` review per migrated test class:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type test --target examples/web:com.example.OrderTest --kind assertion_strength --note "Reviewed assertions for examples/web:com.example.OrderTest."
```

Record one `mock_boundary` review per migrated C7 test:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type test --target examples/web:com.example.OrderTest#testOrder --kind mock_boundary --note "Reviewed C7 test examples/web:com.example.OrderTest#testOrder and its CPT mocks."
```

| C7 mock boundary | CPT mock boundary | Verdict |
|---|---|---|
| C7 registers a domain-service mock. | CPT uses `@MockitoBean` for the same service. | Allowed. |
| C7 uses `autoMock("bpmn/sample.bpmn")`. | CPT mocks a job type declared in that model's converted copy. | Allowed. |
| C7 runs a component for real. | CPT adds a worker, child-process, decision, or Spring mock. | Requires user approval. |

The validator resolves each C7 `autoMock` resource to one source model. It allows only job types
declared in that model's converted copy. If the resource resolves to zero or multiple models, then
each CPT job-worker mock requires approval. The validator rejects an unapproved new mock. The
`cpt_test_id` of each `mock_changes` entry must identify a CPT test mapped from a migrated C7 test.
When one C7 test maps to multiple CPT tests, record `mocks.c8_by_test_id` as an object keyed by
every ID in `c8_ids`. List each CPT test's mocks under its ID. Set `mocks.c8` to the union of those
per-test lists. When a multi-ID test has any C8 mocks, the validator requires this map. Where
`mocks.c8` is empty, the map is optional.

**Parity.** After both runs and reviews, record the computed parity check:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind test_parity
```

The validator does not treat a skipped CPT test as a pass for parity. Each C7 test with
`c7_result: passed` must map to passing CPT tests in both runs. An approved retired test is the only
other passing disposition. A failed or skipped C7 baseline test is listed but is not required to
pass parity. The validator rejects a CPT test ID mapped from multiple migrated C7 tests.

**Coverage.** Record `coverage_parity` after the CPT suites run:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind coverage_parity
```

The validator reads C7 process coverage from `target/process-test-coverage/**/report.json`. It reads
CPT process coverage from `target/coverage-report/report.json` by default. Where a suite writes CPT coverage to another path, set `coverage_reports` to module-relative globs
on the matching module's `test_suites[]` entry in `validation-evidence.json`. The Step 2 inventory's
`test_suites[].coverage_reports` configures Camunda 7 coverage reports only.
See the [CPT Process Test Coverage documentation](https://docs.camunda.io/docs/apis-tools/testing/getting-started/#process-test-coverage).

The CPT 8.9.21 JSON report stores process entries in `coverages[]`. Newer CPT report schemas use
`processCoverages[]`. Both arrays contain `processDefinitionId`, `completedElements`, and
`takenSequenceFlows`. The validator compares the process IDs with C7-covered IDs. Where shared element IDs map a renamed process uniquely, the validator uses that mapping. If no converted process contains a C7-covered ID, then the validator ignores that ID. It checks each retained C7-covered ID against both CPT runs.
If one C7 process maps to multiple converted processes, then the gate reports ambiguity. If
multiple C7 process IDs map to the same CPT process ID, then the gate reports ambiguity.
If one C7 process ID appears in multiple source models and a covered element remains in a
converted model, then the gate reports ambiguity.
The CPT report identifies processes by ID, not by converted model path. If a covered process ID appears in more than one converted model, then the gate reports ambiguity.
The validator marks a process row without C7-covered elements as `CPT coverage`. This status reports
CPT coverage without claiming parity.

The validator also lists CPT decision coverage from `decisionCoverages[].decisionDefinitionId` and
`decisionCoverages[].matchedRuleIds`. Camunda 7 process-test-coverage does not provide a matching
decision baseline.

When no C7 coverage report exists, record `No Camunda 7 coverage baseline` in
`MIGRATION_REPORT.md`. The validator still records CPT coverage. It does not claim coverage parity.

**Report.** The validator writes and owns the Test Parity and Test Coverage sections of
`MIGRATION_REPORT.md`.
The Test Parity table lists every C7 test, its C7 result, mapped CPT tests, both CPT run results,
status, and notes. The Notes column lists approved retirement reasons. The section also lists
approved test changes and approved mock changes.

### Migrate tests only

When the user selects **Migrate tests only**, block every module test suite and Step 4 process
scenario with the exact reason `declined by user (Question 8)`:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . block --type module --target examples/web --kind tests --scenario unit --reason "declined by user (Question 8)"
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . block --type process --target models/converted-c8-order.bpmn#order-process --kind process_path --scenario normal --reason "declined by user (Question 8)"
```

In `migrate_only` mode, the validation script applies these rules:

- It refuses the `run` and `review` actions for `tests` and `process_path` checks.
- It rejects a `block` action with another reason.
- The gate rejects a passed or differently blocked `tests` or `process_path` check.
- It accepts module `compile` evidence only for a `test-compile` or `testClasses` command without
  `-Dmaven.test.skip`.
- It does not require `docker_info`.

The packaging rule for `migrate_only` mode in `test-migration.md` is the only exception to the rule
against commands that skip tests.

### Review checks

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
retains all caller mappings under the shared timer decision. The gate accepts a shared source helper
location for distinct callers of the same timer. It rejects a source location mapped to different
timers. A C7 due-date call that remains at a mapped source location blocks readiness. If the mapped
source file retains the same due-date operation, then the gate also blocks readiness after line or
argument changes.
When an unrelated non-timer call shares that file and operation, the gate cannot distinguish it.
Keep readiness blocked until the project separates those calls.

The `message_date_variable` identifies the date field in the message payload. The `date_variable`
identifies the variable read by the timer.

The `process_id` identifies the executable timer child process. List that process and its parent in
the model inventory. Mark the timer process as non-standalone and set `covering_test` to the parent
test. The timer process must use a direct `=<date_variable>` timer expression. The gate does not interpret
other FEEL expressions. The parent process must call it through the mapped `bpmn:callActivity`.
The call activity must input-map the date variable into the timer process.
Attach an interrupting message boundary event to that call activity.
Map the `message_date_variable` into the parent `date_variable` on the boundary event.
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
past both obsolete deadlines. Record `checked_after_old_deadline` and
`old_deadline_fire_count: 0` in each update. The check time must follow its old deadline and
precede the final deadline. The test publishes consecutive updates as
`deployment-and-timer-preflight.md` requires.
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
      "updates": [
        {
          "old_deadline": "2050-11-23T00:00:15Z",
          "new_deadline": "2050-11-23T00:00:05Z",
          "timer_active_before_update": true,
          "correlated": true,
          "checked_after_old_deadline": "2050-11-23T00:00:16Z",
          "old_deadline_fire_count": 0
        },
        {
          "old_deadline": "2050-11-23T00:00:05Z",
          "new_deadline": "2050-11-23T00:00:25Z",
          "timer_active_before_update": true,
          "correlated": true,
          "checked_after_old_deadline": "2050-11-23T00:00:06Z",
          "old_deadline_fire_count": 0
        }
      ],
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

### Blocked checks, failures, and required checks

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
| Project `.` | In `run` mode, require `docker_info` before the first Docker-dependent suite when any suite needs Docker. Use exactly `docker info`. |
| Each module | `compile`, `review`, and `active_timer_updates` review or blocker. |
| Each module test suite | In `run` mode, record one test check per declared suite. Use `test_repeat` for a suite with mapped migrated or added CPT tests. The validator runs that suite twice. Use `tests` for other suites. In `migrate_only` mode, block every `tests` check with the reason `declined by user (Question 8)`. |
| Each applicable Test Inventory suite | `c7_baseline` before Step 3 changes any file. |
| Each migrated test class | `assertion_strength` review. |
| Each migrated C7 test | `mock_boundary` review. |
| Project `.` in `Run tests` mode with C7 `Migrate` rows or mapped migrated CPT tests | `test_parity` and `coverage_parity`. Require `test_freeze` only while migrated or added CPT IDs remain. |
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
