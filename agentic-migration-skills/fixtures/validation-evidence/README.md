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
definitions, verifies recurring-start replacement and latest-version-by-ID start, then publishes
two updates to an already-active timer. The first update moves the deadline earlier, and the second
moves it later. Each publication uses a bounded TTL and a unique message ID. A parent call activity
starts a separate timer process for each wait. The test holds negative observations stable across
bounded intervals. It verifies that the process is still waiting immediately before the final
deadline. It then advances the test clock to the deadline and records when completion is observed.
The test proves that both old deadlines stay unfired and the final deadline fires once. A second
Process Test queues two consecutive updates before the message subscription opens and verifies that
buffered publication applies both updates.

The runner captures Docker create and destroy events. It also reads the Testcontainers session
label and reconciles the matching Camunda containers after Maven exits. Cleanup does not depend on
capturing every create event and does not remove containers from other Testcontainers sessions.
An unexpected event-stream exit fails the fixture after owned containers are cleaned up. After a
Maven failure, the runner removes session-owned fixture containers before it reports the original
failure.

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
