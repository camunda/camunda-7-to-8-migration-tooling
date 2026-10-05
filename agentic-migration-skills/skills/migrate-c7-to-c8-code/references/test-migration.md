# Test Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

The Step 2 Test Inventory is the source for the test kinds, handling decisions, test models, and test
IDs used here. Ask Question 8 from `references/interview-questions.md` only after that inventory is
complete.

Camunda Process Test (CPT) is the Camunda 8 test library. The Camunda 7 (C7) baseline is the
original suite result set recorded before Step 3.

## Test Execution Choice

Question 8 applies only to code migration with approach A or B, target Camunda 8.9 or later, and at
least one test with handling **Migrate**. It does not apply to Assessment only, Models only,
approach C, target 8.8, or an inventory without a test marked **Migrate**.

When the user selects Assessment only and the inventory includes a test with handling **Migrate**,
explain both Question 8 options in `MIGRATION_REPORT.md`. Do not ask Question 8 or record a selected
test run mode.

Before showing the options, show the number of tests to migrate per test kind and every test with
handling **Report only**, with its reason. Show the test commands found for each module in project
documentation and the CI inventory. Do not invent a command when none was found.

CPT starts the Camunda 8 runtime in Docker through Testcontainers by default. Run `docker info` and
state whether it succeeds. A remote CPT runtime is an alternative. Offer both Question 8 options
when Docker is unavailable.

| User choice | Before migration | After migration | Test verification |
|---|---|---|---|
| **Run tests** | Run each Camunda 7 test command before Step 3 changes any file. Record the baseline. | Migrate the tests, run each CPT test command, and apply the parity, freeze, mock-boundary, repeat-run, and coverage safeguards. | Report `verified` only when every required test check passes. |
| **Migrate tests only** | Do not run a Camunda 7 test command. | Migrate the tests in the same way as the Run tests path. Compile test sources. Do not run CPT suites or Step 4 process scenarios. | Record every skipped test check as blocked with `declined by user (Question 8)`. Report `not verified (Migrate tests only)`. |

When the user selects **Migrate tests only**, compile each module's test sources with `mvn
test-compile` or the Gradle `testClasses` task. A main-source-only compile does not count.

Record the user's Question 8 answer in the `MIGRATION_REPORT.md` decision log.
When the user selects **Migrate tests only**, record `test_run_mode: "migrate_only"` in
`.camunda-migration/validation/step2-inventory.json`. When the user selects **Run tests**, record
`test_run_mode: "run"` there. The validation script reads this field.

## Step 3 order

When `test_run_mode` is `run` and the Test Inventory has a test with handling `Migrate`, use these
phases in this order:

| Phase | Action |
|---|---|
| C7 baseline | Run each Camunda 7 suite that contains an in-scope test. Run it before Step 3 changes any file. |
| Models | Convert the model copies, including test models. |
| Tests | Migrate the in-scope tests. Review recipe changes before accepting them. |
| Freeze | Record hashes for test source files and test resources. |
| Production code | Migrate production code until the frozen tests pass. |

When the selected mode is `migrate_only`, do not run a test command. Follow the declined-test path
in `validation-evidence.md`.

Keep the Test Inventory in `MIGRATION_REPORT.md`. Keep the machine-readable test mode and suite
commands in `.camunda-migration/validation/step2-inventory.json`:

For a **Run tests** run, include `test_suites`:

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

For a **Migrate tests only** run, the inventory can omit `test_suites`:

```json
{
  "schema_version": 1,
  "modules": ["examples/web"],
  "models": ["models/order.bpmn"],
  "test_run_mode": "migrate_only"
}
```

Do not add `test_run_mode` when Question 8 does not apply.

Use `run` or `migrate_only` for `test_run_mode`. Set `test_ids` to Test Inventory IDs in that suite.
Assign every `Migrate` test to at least one suite. Use a distinct `name` for each suite in a module.
The validator uses `command` for the Camunda 7 baseline. It runs the command without a shell.
Keep the Test Inventory unchanged after Step 2. Record CPT mappings in `test-mapping.json`.

Set `reports` to a list of project-relative globs when the build uses custom JUnit report paths.
The default report paths are Maven Surefire, Maven Failsafe, and Gradle test-result XML files.
In the Step 2 inventory, set `coverage_reports` on the matching `test_suites[]` entry to
project-relative globs when Camunda 7 coverage reports use another path.
The default Camunda 7 coverage paths are `target/process-test-coverage/**/report.json` and
`target/process_test_coverage/**/*.json`.
Where a suite uses custom test source or resource directories, list each project-relative path in
`test_source_roots` or `test_resource_roots`. Each path must remain inside that suite's module.
Each configured root must exist as a directory when the freeze check runs.

## Camunda 7 baseline

Run each suite that contains a test marked `Migrate` in the Test Inventory:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target examples/web --kind c7_baseline --scenario unit -- mvn -B -pl examples/web test
```

Use the exact command recorded in `step2-inventory.json`. The validator rejects a different
command. The validator rejects a baseline run after any Step 2 source file changes.

The validator parses JUnit XML with Python's standard library. It reads Surefire files from
`target/surefire-reports/TEST-*.xml`. It reads Failsafe files from `target/failsafe-reports/`.
It reads Gradle files from `build/test-results/**/`.

The validator maps each invocation to `<module>:<fully qualified class>#<method>`. It removes
parameter and repeat suffixes from the method name. It marks a method `passed` only when every
invocation passes.

The baseline check passes when it captures a fresh report for every suite test ID. A failed or
skipped C7 test remains visible in the ledger. A C7 test that did not pass is not required to pass
parity. The C7 command can exit nonzero while the baseline check records valid reports. The ledger
retains the individual test results.

| Invocation results | Method result |
|---|---|
| Every invocation passed | `passed` |
| One or more invocations errored | `error` |
| No invocation errored and one or more failed | `failed` |
| No invocation errored or failed and one or more skipped | `skipped` |

The validator copies JUnit reports to `.camunda-migration/validation/baseline/`. It records one
result for each Test Inventory ID in the suite. It records the Step 2 Git commit when the project
uses Git. It stores the C7 test results in `test-mapping.json`.
The validator also records a Report only test found in a fresh C7 report even when the suite omits
its ID.

Where Camunda 7 process-test-coverage reports exist, the validator copies and parses their JSON
reports. It records covered flow-node and sequence-flow IDs under each `modelKey`. It maps those IDs
to the covered process.

When a suite cannot run, record it as blocked:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . block --type module --target examples/web --kind c7_baseline --scenario unit --reason "The suite requires an unavailable database."
```

Ask the user whether to continue without that baseline. Record approval in
`test-mapping.json`:

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

Do not invent test results. Without a C7 baseline, report parity as `not verified`. The validation
gate remains `NOT READY`.

## Test parity ledger

The validator owns `.camunda-migration/validation/test-mapping.json`. It writes `c7_result` from
JUnit reports. Never write `c7_result` by hand.

The skill records each test mapping and each approval in the ledger:

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

Use these ledger statuses:

| Status | Meaning |
|---|---|
| `migrated` | The C7 test maps to one or more CPT tests. |
| `retired` | The user approved removal of the C7 test. Record a reason and approver. |
| `manual` | The Test Inventory marks the test `Report only`. The test is not verified. |
| `added` | The migration added a CPT test without a C7 source test. |

When a `Report only` test passed in the C7 baseline, its `manual` status does not satisfy parity.
Migrate it or record an approved retirement before claiming `READY`.

When a ledger edit changes a test check's digest, the validator ignores that stale record. Record each still-required check again.
The validator includes frozen test-file hashes and approved `test_changes` in assertion-strength and
mock-boundary review digests. Record each required review again when either the hashes or approvals
change.

For a retired test, set `retirement.reason` and `retirement.approved_by`. The validator rejects a
retired test without both values.

For an added test, set `c8_ids`. The validator requires each added CPT test to pass in both runs.
Keep `c8_ids` distinct within each ledger row. Never assign one CPT ID to multiple migrated or
added test rows.

## Freeze migrated tests

After test migration, run the freeze check:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind test_freeze
```

The validator hashes each existing file named by a `Migrate` Test Inventory row. It also hashes
every file under `src/test/` in each module with an in-scope `Migrate` test.
For suites with migrated tests, it hashes every file under configured `test_source_roots` and
`test_resource_roots`.
The source snapshot locks these root settings before migration starts.
The validator stores the original freeze digest in its `test_freeze` check log. Keep `freeze.files`
unchanged after the first freeze. If the ledger differs from the logged digest, then the validator
rejects it.

Do not edit a frozen test file while migrating production code. Ask the user before a test file must
change. Record each approved change with its path, reason, old hash, new hash, and approver:

```json
{
  "file": "examples/web/src/test/java/com/example/OrderCptTest.java",
  "reason": "The user approved a required assertion update.",
  "old_hash": "sha256:<old hash>",
  "new_hash": "sha256:<new hash>",
  "approved_by": "operator"
}
```

Use `null` for a missing old hash when adding a file. Use `null` for a missing new hash when
removing a file. The validator rejects each changed hash without a matching approval.

## CPT repeat and parity checks

The `test_repeat` check runs each migrated CPT suite twice:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target examples/web --kind test_repeat --scenario unit -- mvn -B -pl examples/web test
```

The validator parses and preserves each run's JUnit XML. It compares each test method's result and
invocation results across the two runs. A difference marks the suite flaky. A failed command also
fails the repeat check.

After both runs and reviews pass, record the computed parity check:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind test_parity
```

The validator does not treat a skipped CPT test as a pass for parity. Each C7 test with
`c7_result: passed` must map to passing CPT tests in both runs. An approved retired test is the only
other passing disposition. A failed or skipped C7 baseline test is listed but is not required to
pass parity. The validator rejects a CPT test ID mapped from multiple migrated C7 tests.

Record assertion-strength reviews once per migrated test class:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type test --target examples/web:com.example.OrderTest --kind assertion_strength --note "Reviewed assertions for examples/web:com.example.OrderTest."
```

Compare each C7 assertion with its CPT assertion. Keep equal or stronger assertions. Record a
reason when a CPT test cannot retain an assertion.

Record one mock-boundary review per migrated C7 test:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . review --type test --target examples/web:com.example.OrderTest#testOrder --kind mock_boundary --note "Reviewed C7 test examples/web:com.example.OrderTest#testOrder and its CPT mocks."
```

| C7 mock boundary | CPT mock boundary | Verdict |
|---|---|---|
| C7 registers a domain-service mock. | CPT uses `@MockitoBean` for the same service. | Allowed. |
| C7 uses `autoMock("bpmn/sample.bpmn")`. | CPT mocks a job type declared in that model's converted copy. | Allowed. |
| C7 runs a component for real. | CPT adds a worker, child-process, decision, or Spring mock. | Requires user approval. |

The validator resolves each `autoMock` resource to one source model. It allows only job types
declared in that model's converted copy. If the resource resolves to zero or multiple models, then
each CPT job-worker mock requires approval.

Record each approved new CPT mock in `mock_changes` with `cpt_test_id`, `mock`, `reason`, and
`approved_by`. The `cpt_test_id` must identify a CPT test mapped from a migrated C7 test. The
validator rejects an unapproved new mock.

## Coverage parity

The `coverage_parity` check compares C7 and CPT coverage:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind coverage_parity
```

The validator reads C7 process coverage from `target/process-test-coverage/**/report.json`. It reads
CPT process coverage from `target/process-test-coverage/report.json` by default. Set
`coverage_reports` on the matching module's `test_suites[]` entry in `validation-evidence.json`
when a suite writes CPT coverage to another path. The Step 2 inventory's
`test_suites[].coverage_reports` configures Camunda 7 coverage reports only.
See the [CPT Process Test Coverage documentation](https://docs.camunda.io/docs/apis-tools/testing/getting-started/#process-test-coverage).

The CPT 8.9.21 JSON report stores process entries in `coverages[]`. Newer CPT report schemas use
`processCoverages[]`. Both arrays contain `processDefinitionId`, `completedElements`, and
`takenSequenceFlows`. The validator compares the process IDs with C7-covered IDs. It maps a renamed
process by shared element IDs when that mapping is unique. It ignores a C7-covered ID when no
converted process contains it. It checks each retained C7-covered ID against both CPT runs.
The gate reports ambiguity when one C7 process maps to multiple converted processes. It also reports
ambiguity when multiple C7 process IDs map to the same CPT process ID.
The gate reports ambiguity when one C7 process ID appears in multiple source models and a covered
element remains in a converted model.
The CPT report identifies processes by ID, not by converted model path. The gate reports ambiguity
when a covered process ID appears in more than one converted model.

The validator also lists CPT decision coverage from `decisionCoverages[].decisionDefinitionId` and
`decisionCoverages[].matchedRuleIds`. Camunda 7 process-test-coverage does not provide a matching
decision baseline.

When no C7 coverage report exists, record `No Camunda 7 coverage baseline` in
`MIGRATION_REPORT.md`. The validator still records CPT coverage. It does not claim coverage parity.

## Report

The validator writes a Test Parity table to `MIGRATION_REPORT.md`. It lists every C7 test, its C7
result, mapped CPT tests, both CPT run results, status, and notes.

The Notes column lists approved retirement reasons. The section also lists approved test changes
and approved mock changes. The validator owns the Test Parity and Test Coverage sections.

When the user selects **Migrate tests only**, block test checks with the exact reason
`declined by user (Question 8)`. See `validation-evidence.md` for the required recorder commands.

When no required check failed and no required runtime dependency is unavailable, the project-readiness
verdict is `needs review`, not `blocked`. Follow `references/project-readiness.md`.

## Verification Plan for a Deferred Test Run

When the user selects **Migrate tests only**, add a **Verify the test migration** section to
`MIGRATION_REPORT.md`. List concrete commands from the Test Inventory and the project documentation
and CI inventory. Replace each placeholder below with the actual baseline commit, worktree path, and
module commands:

1. Create a separate worktree from the Step 2 baseline with
   `git worktree add ../c7-baseline <baseline-commit>`.
2. Run each Camunda 7 suite in that worktree with its recorded command, such as `mvn test`.
3. Start Docker or configure the remote CPT runtime. Run each migrated suite with its recorded
   command, such as `mvn test`.
4. Record both runs with the validation recorder. Regenerate the gate with
   `python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . report`.

The verification plan is not test evidence. Do not report the tests as verified until both test runs
are recorded and every required test check passes.

When the user later asks the skill to verify a **Migrate tests only** run, run the Camunda 7 suite
from the Step 2 baseline commit in a separate worktree. Never rebuild the baseline from migrated code.
(MAY)
