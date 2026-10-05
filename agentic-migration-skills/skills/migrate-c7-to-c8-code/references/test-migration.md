# Test Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

Camunda Process Test (CPT) is the Camunda 8 test library. The Camunda 7 (C7) baseline is the
original suite result set recorded before Step 3.

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

Use `run` or `migrate_only` for `test_run_mode`. Set `test_ids` to Test Inventory IDs in that suite.
Assign every `Migrate` test to at least one suite. Use a distinct `name` for each suite in a module.
The validator uses `command` for the Camunda 7 baseline. It runs the command without a shell.
Keep the Test Inventory unchanged after Step 2. Record CPT mappings in `test-mapping.json`.

Set `reports` to a list of project-relative globs when the build uses custom JUnit report paths.
The default report paths are Maven Surefire, Maven Failsafe, and Gradle test-result XML files.
Set `coverage_reports` to project-relative globs when Camunda 7 coverage reports use another path.
The default Camunda 7 coverage paths are `target/process-test-coverage/**/report.json` and
`target/process_test_coverage/**/*.json`.

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

For a retired test, set `retirement.reason` and `retirement.approved_by`. The validator rejects a
retired test without both values.

For an added test, set `c8_ids`. The validator requires each added CPT test to pass in both runs.

## Freeze migrated tests

After test migration, run the freeze check:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind test_freeze
```

The validator hashes every file under `src/test/` in each module with an in-scope `Migrate` test.
This includes test source files and test resources.

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
pass parity.

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
| C7 uses `autoMock` for the model. | CPT mocks each job worker. | Allowed. |
| C7 runs a component for real. | CPT adds a worker, child-process, decision, or Spring mock. | Requires user approval. |

Record each approved new CPT mock in `mock_changes` with `cpt_test_id`, `mock`, `reason`, and
`approved_by`. The validator rejects an unapproved new mock.

## Coverage parity

The `coverage_parity` check compares C7 and CPT coverage:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind coverage_parity
```

The validator reads C7 process coverage from `target/process-test-coverage/**/report.json`. It reads
CPT process coverage from `target/process-test-coverage/report.json` by default. Set
`coverage_reports` in the corresponding suite entry when a project uses another path.
See the [CPT Process Test Coverage documentation](https://docs.camunda.io/docs/apis-tools/testing/getting-started/#process-test-coverage).

The CPT JSON report records `processCoverages[].processDefinitionId`,
`processCoverages[].completedElements`, and `processCoverages[].takenSequenceFlows`. The validator
compares these IDs with C7-covered IDs. It maps a renamed process by shared element IDs when that
mapping is unique. It ignores a C7-covered ID when no converted process contains it. It checks each
retained C7-covered ID against both CPT runs. The gate reports an ambiguity when several converted
processes contain the same renamed-process IDs.

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
