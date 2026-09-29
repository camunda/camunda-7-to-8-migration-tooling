# Validation evidence regression fixture

This test preserves the contradiction in #2926. It creates a nine-module, ten-model project and
synthetic evidence in a temporary directory. The fixture stores no logs, manifests, or reports.
The report claims `READY`, but configuration, tests, lint, deployment, and JAR launches fail.
Other model checks, a message assertion, and a timer preflight are absent.

The gate changes that claim to `NOT READY`.

The other tests cover a complete passing scope, independent failures, missing records, runtime
modules, process assertions, Docker classification, path safety, and report replacement.
They also cover two-module `Sample` collisions, recurring starts, model-bound timer observations,
due-date blockers, and stale prerequisite checks. These tests use synthetic evidence.
They do not access a Camunda cluster. A separate disposable-target observation appears in the
[deployment and timer preflight](../../skills/migrate-c7-to-c8-code/references/deployment-and-timer-preflight.md).

From the repository root, run:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```
