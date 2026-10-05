# DMN decision-test migration fixture

This fixture pairs standalone Camunda 7 DMN tests and an engine-backed `DecisionService` test with
their Camunda Process Test (CPT) equivalents. The models and test data are synthetic.

The standalone test covers a multi-output decision, a no-match result, and an explicit `null` input.
The `DecisionService` test covers a DRD with a required decision, a `COLLECT` table, and a `UNIQUE`
hit-policy violation. Both CPT tests deploy converted DMN copies.

Run the Camunda 7 baseline:

```sh
mvn -f agentic-migration-skills/fixtures/dmn-decision-tests/c7-source/pom.xml verify
```

Run the migrated tests:

```sh
mvn -f agentic-migration-skills/fixtures/dmn-decision-tests/expected-c8/pom.xml verify
```

The Camunda 7 tests use an in-process DMN engine and an in-memory process engine. CPT needs a
Camunda 8 runtime. CPT starts a local runtime with Docker by default. Configure a remote runtime
instead when Docker is unavailable.

Run the static guidance checks with Python's standard library:

```sh
python3 agentic-migration-skills/fixtures/dmn-decision-tests/test_migration_guidance.py
```
