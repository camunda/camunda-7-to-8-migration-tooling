# Validation evidence regression fixture

This test preserves the contradiction in #2926. It creates a nine-module, ten-model project and
synthetic evidence in a temporary directory. The fixture stores no logs, manifests, or reports.
The report claims `READY`, but configuration, tests, lint, deployment, and JAR launches fail.
Other model checks, a message assertion, and a timer preflight are absent.

The gate changes that claim to `NOT READY`.

The other tests cover a complete passing scope, independent failures, missing records, runtime
modules, process assertions, Docker classification, path safety, and report replacement.
They also cover two-module `Sample` collisions, recurring starts, model-bound timer observations,
due-date blockers, stale prerequisite checks, JUnit baselines, test parity, frozen files, repeated
CPT runs, mock approvals, and coverage drops. The Python tests use synthetic evidence and do not
access a Camunda cluster.

The `live-timer-fixture/` Maven reactor runs both deployment-set and active-timer acceptance
scenarios against a disposable Camunda 8.9.21 container. One Process Test deploys both `Sample`
definitions, verifies recurring-start replacement and latest-version-by-ID start, then publishes
two updates to an already-active timer. The first update moves the deadline earlier, and the second
moves it later. Each publication uses a bounded TTL and a unique message ID. A parent call activity
starts a separate timer process for each wait. The test holds negative observations stable across
bounded intervals. It verifies that the process is still waiting immediately before the final
deadline. It then advances the test clock to the deadline and records when completion is observed.
The test proves that both old deadlines stay unfired and the final deadline fires once. A second
update occurs after the first rearm, while the replacement timer is active.

The runner reads the Testcontainers session ID and removes only matching Camunda 8.9.21 containers
after Maven exits, including on failure or interruption. It verifies that none remain and does not
remove containers from other sessions. If it cannot identify the session or confirm cleanup, it
fails rather than reporting a successful disposable test.
If cleanup fails or is interrupted, the runner keeps the session ID. On the next run, it retries
cleanup for that session before starting a new fixture.

Use Java 21, Maven, and a running Docker daemon. From the repository root, run:

```sh
JAVA_HOME=/path/to/jdk-21 PATH=/path/to/jdk-21/bin:$PATH \
  python3 agentic-migration-skills/fixtures/validation-evidence/run_live_timer_fixture.py
```

The fixture pins its process-test runtime to 8.9.21 and verifies that no Camunda container from
its session remains. Other sessions can remain active.
See the [deployment and timer preflight](../../skills/migrate-c7-to-c8-code/references/deployment-and-timer-preflight.md)
for the approved active-timer strategy and the validation evidence schema.

From the repository root, run:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```
