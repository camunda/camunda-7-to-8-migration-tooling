# Validation evidence regression fixture

This test preserves the contradiction in #2926. It creates a nine-module, ten-model project and
synthetic evidence in a temporary directory. The fixture stores no logs, manifests, or reports.
The report claims `READY`, but configuration, tests, lint, deployment, and JAR launches fail.
Other model checks, a message assertion, and a timer preflight are absent.

The gate changes that claim to `NOT READY`.

The other tests cover a complete passing scope, independent failures, missing records, runtime
modules, process assertions, Docker classification, path safety, and report replacement.
They also cover two-module `Sample` collisions, recurring starts, model-bound timer observations,
due-date blockers, and stale prerequisite checks. The Python tests use synthetic evidence and do
not access a Camunda cluster.

The `live-timer-fixture/` Maven reactor runs both deployment-set and active-timer acceptance
scenarios against a disposable Camunda 8.9.21 container. One Process Test deploys both `Sample`
definitions, verifies recurring-start replacement and latest-version-by-ID start, then rearms an
already-active timer twice. It proves that both old deadlines stay unfired, the final deadline
fires once, and the container is removed. The runner prints the runtime observation as its final
JSON line and verifies Docker create and destroy events.

Use Java 21, Maven, and a running Docker daemon. From the repository root, run:

```sh
JAVA_HOME=/path/to/jdk-21 PATH=/path/to/jdk-21/bin:$PATH \
  python3 agentic-migration-skills/fixtures/validation-evidence/run_live_timer_fixture.py
```

The fixture pins its process-test runtime to 8.9.21. It refuses to run when a matching Camunda
container is already active. See the [deployment and timer preflight](../../skills/migrate-c7-to-c8-code/references/deployment-and-timer-preflight.md)
for the approved active-timer strategy and the validation evidence schema.

From the repository root, run:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```
