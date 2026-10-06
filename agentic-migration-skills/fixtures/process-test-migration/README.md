# Engine-backed process-test migration fixture

This fixture covers Camunda 7 engine-backed process tests without Spring. It includes JUnit 3,
JUnit 4, and JUnit 5 test support. The expected Camunda 8 project uses CPT 8.9.22 for JUnit 5
process tests.

The process-test classes keep their names and method names. The expected tests deploy only
`converted-c8-*` resources. The fixture also keeps a non-process JUnit 4 test in both projects.
`junit-vintage-engine` must run that test in the expected project.

The Camunda 7 `camunda.cfg.xml` enables full history and disables its job executor. The tests do
not query history. The expected project removes that engine-only configuration and records the
setting in `expected-c8/MIGRATION_REPORT.md`.
The expected project disables only `camunda-compat/user-task-definition` because these tests
complete form-free user tasks through CPT. It does not suppress other BPMN rules.

## Run the fixture

Use Java 21 and Docker. From the repository root, run:

```bash
mvn clean test -f agentic-migration-skills/fixtures/process-test-migration/pom.xml
python3 -m unittest agentic-migration-skills/fixtures/process-test-migration/test_migration_guidance.py
```

The Maven command runs the Camunda 7 tests and the expected CPT tests. CPT starts a
Testcontainers runtime, so Docker must be available. The Python tests check test-method parity,
converted deployment paths, the dependency changes, and the required behavior examples.

Run the BPMN linter from `expected-c8`:

```bash
cd agentic-migration-skills/fixtures/process-test-migration/expected-c8
c8ctl bpmn lint src/test/resources/com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn
c8ctl bpmn lint src/test/resources/com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn
```

## Migration walkthrough

1. Copy `c7-source` to a temporary project and run the `migrate-c7-to-c8-code` skill.
2. Classify each engine-backed test without Spring as a process test. Migrate the JUnit 3 and JUnit
   4 tests to JUnit 5. Keep the class and method names.
3. Map the implicit deployment for `ImplicitDeploymentTest` to the converted copy
   `com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn`.
4. Map each explicit deployment to the converted copy
   `com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn`. Do not deploy either Camunda 7
   BPMN file.
5. Map the JUnit 3 `setUp()` and `tearDown()` overrides to `@BeforeEach` and `@AfterEach`.
6. Compare every migrated method with the same method in `expected-c8`.
7. Run both commands above. Confirm that the JUnit 4 `LegacyFormatterTest` still runs in
   `expected-c8`.

The async-continuation and failure tests use CPT job handlers for the converter-derived job types
`noopDelegate` and `failingDelegate`. One completes the no-op service task. The other creates an
incident. These test handlers do not define the general Camunda 7 mock migration.

The Diagram Converter created both converted copies. `expected-c8/MIGRATION_REPORT.md` records the
one manual change.
