# Remote-engine test migration

This fixture migrates a Camunda 7 payment test that deploys and starts a process through Engine REST.
The Camunda 7 process runs a real `@ExternalTaskSubscription` worker from the Spring context.
The expected Camunda 8 test uses a real `@JobWorker` with the CPT-managed Testcontainers runtime.
Both tests assert process completion and the `charged` variable.

## Run the fixture

Run the Camunda 7 baseline from the repository root with Java 21, Maven, and Docker:

```sh
mvn -f agentic-migration-skills/fixtures/remote-engine-tests/c7-source/pom.xml test
```

If the Camunda 7 image or engine cannot start or respond, record the baseline as `not run` in
`MIGRATION_REPORT.md`. Do not claim parity from the expected project alone.

Run the expected Camunda 8 test with Java 21, Maven, and Docker:

```sh
mvn -f agentic-migration-skills/fixtures/remote-engine-tests/expected-c8/pom.xml test
```

CPT starts and manages its Testcontainers runtime. The expected project does not configure CPT
remote mode or a Camunda 7 Engine REST URL.

Run the skill guidance regression test from the repository root:

```sh
python3 -m unittest discover \
  -s agentic-migration-skills/fixtures/remote-engine-tests \
  -p 'test_migration_guidance.py'
```

## Shared-engine case

`shared-engine/c7-source` contains an opt-in test that starts the deployed `payment` process,
runs its `charge-payment` worker against the configured shared engine, and checks process
completion with `charged=true`.
By default, JUnit skips this test. The configured URL uses the reserved `.invalid` domain.
Compile the test without connecting to an engine:

```sh
mvn -f agentic-migration-skills/fixtures/remote-engine-tests/shared-engine/c7-source/pom.xml test
```

Run the test only when a shared engine with the `payment` process is available. Replace the
example URL with that engine's Engine REST URL:

```sh
mvn -f agentic-migration-skills/fixtures/remote-engine-tests/shared-engine/c7-source/pom.xml test \
  -Dshared-engine.test.enabled=true \
  -Dtest.engine-rest-url=http://localhost:8080/engine-rest
```

`expected-shared-engine/MIGRATION_REPORT.md` records this test as `manual` in the parity ledger.
The report-only reason is: "CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime."
Follow `skills/migrate-c7-to-c8-code/references/test-migration.md` for the scope rules, REST mapping, and runtime policy.
