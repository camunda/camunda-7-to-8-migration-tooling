# Camunda 7 process-test migration fixture

This fixture contains a synthetic Camunda 7.24.0 project and its expected
Camunda 8.9.21 migration. The fixture covers embedded engine tests, Spring
Boot tests, DMN decisions, mocks, Camunda Platform Scenario, and remote-engine
tests. It also records tests that need manual redesign or are outside process
test migration.

## Run the fixture

Run these commands from the repository root with Java 21. Docker is required
for the remote-engine C7 test and the CPT runtime.

```bash
mvn -B -f agentic-migration-skills/fixtures/c7-process-tests/c7-source/pom.xml verify
mvn -B -f agentic-migration-skills/fixtures/c7-process-tests/expected-c8/pom.xml verify
mvn -B -f agentic-migration-skills/fixtures/c7-process-tests/expected-c8/pom.xml verify
python3 agentic-migration-skills/fixtures/c7-process-tests/test_migration_guidance.py
```

The two C8 runs must report the same test outcomes. The C7 run skips only
`SharedEngineSmokeIT` when `SHARED_ENGINE_URL` is not set.

Each C8 module writes its process-coverage report to
`expected-c8/<module>/target/coverage-report/report.html`.

## Walkthroughs

Start each walkthrough from a fresh copy of `c7-source`.

| ID | Walkthrough and tester's answer | Expected result |
|---|---|---|
| W1 | Choose **Assessment only** with target 8.9. | The Test Inventory matches `expected-assessment/test-inventory.md`. The skill changes no file except `MIGRATION_REPORT.md`. |
| W2 | Choose **Code + models**, target 8.8. Stop after the Step 2 Summary. | The inventory matches `expected-assessment-8.8/test-inventory.md`. The skill does not ask Question 8. |
| W3 | Choose **Code + models**, target 8.9, and **Run tests**. Approve retiring E9 with "CMMN has no Camunda 8 equivalent". Approve retiring E10 with "model built in Java, migrated by hand later". | The skill records the C7 baseline before editing files. It migrates selected process, decision, scenario, and remote-engine tests with available CPT procedures. The Test Parity table marks the PaymentWorker remote-engine test as migrated, keeps the shared-engine test manual because CPT deletes runtime data between tests, and matches `expected-run/test-parity.md`. The gate is `READY`. |
| W4 | Repeat W3 and decline both retirements. | The gate is `NOT READY`. The report names E9 and E10 as `manual`. |
| W5 | Stop Docker, then choose **Migrate tests only**. | Question 8 reports that `docker info` fails and still offers both options. The migrated tests compile, and no test command runs. Every test check is blocked with `declined by user (Question 8)`. The gate is `NOT READY`, and the readiness verdict is `needs review`. The report matches `expected-tests-only/MIGRATION_REPORT.md`. |
| W6 | Delete every `src/test` directory from the copy. Choose **Code + models** and stop after the Step 2 Summary. | The inventory has no in-scope tests. The skill does not ask Question 8. |
| W7 | Start from the result of W3. Apply each negative change separately, rerun the recorded checks, then rerun the gate. | N1, N2, and N3 produce the results in the table below. |

The `Signals` column records the source-derived `mocks` modifier for process and decision tests
with supported component mock signals. A user-provided marker or a dependency alone does not
trigger it.
A `ProcessScenario` harness mock alone does not trigger it. A Scenario stub that replaces a BPMN
component does.

## Negative cases

| ID | Change | Expected gate result |
|---|---|---|
| N1 | Delete the CPT test mapped to E1. | `NOT READY`; `test_parity` fails and names the E1 test ID. |
| N2 | Change an assertion in the frozen E2 test without an approved `test_changes` entry. | `NOT READY`; `test_freeze` fails and names the file. |
| N3 | Replace E1's real `Task_ChargePayment` worker with `mockJobWorker("charge-payment").thenComplete()` without an approved `mock_changes` entry. | `NOT READY`; `mock_boundary` fails and names the CPT test and job type. |

The `test_parity`, `test_freeze`, and `mock_boundary` check names come from the
parity and mock migration contracts. The fixture tests the names implemented by
those contracts.

## Layout

- `c7-source/` is the migration input. Its modules are independent of
  `expected-c8/`, but keep the same Maven coordinates.
- `c7-source/engine-tests-legacy/` runs only the shared Scenario tests against
  `camunda-bpm-assert-scenario` 1.1.1, isolated from the 2.x runner.
- `expected-c8/` contains the expected migrated project. It runs the shared C7
  Scenario tests once in `engine-tests`. The target omits `engine-tests-legacy`
  because that module has no unique tests. It uses converted copies named
  `converted-c8-*` and minimal `.form` files for converted user tasks.
- `expected-assessment/` and `expected-assessment-8.8/` contain target-specific
  Test Inventories.
- `expected-run/` contains the Test Parity table for a full migration.
- `expected-tests-only/` contains the report for a migration that does not run
  tests.
- `test_migration_guidance.py` checks the inventory, parity mapping, BPMN job
  types, and test-migration reference.

All projects and test data are synthetic. The Java source files use the
repository license header. The mockito and process-coverage dependencies use
their current `io.holunda.c7` coordinates because Maven relocates the original
community coordinates.
