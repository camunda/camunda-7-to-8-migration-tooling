# Test Migration and Verification

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference uses
(SHOULD). A permission uses (MAY).

The Step 2 Test Inventory is the source for the test kinds, handling decisions, test models, and test
IDs used here. Ask Question 8 from `references/interview-questions.md` only after that inventory is
complete.

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

For example:

```json
{
  "schema_version": 1,
  "modules": ["examples/web"],
  "models": ["models/order.bpmn"],
  "test_run_mode": "migrate_only"
}
```

Do not add `test_run_mode` when Question 8 does not apply.

When the user selects **Migrate tests only**, record each module `tests` check and each process
`process_path` check with the `block` action and the exact reason `declined by user (Question 8)`.
The validation gate must report `NOT READY` because the required test checks remain unrun.

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
