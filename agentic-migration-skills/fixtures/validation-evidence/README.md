# Validation evidence regression fixture

This test preserves the contradiction in #2926. It creates a nine-module, ten-model project and
synthetic evidence in a temporary directory. The fixture stores no logs, manifests, or reports.
The report claims `READY`, but configuration, tests, lint, deployment, and JAR launches fail.
Other model checks, a message assertion, and a timer preflight are absent.

The gate changes that claim to `NOT READY`.

The tests cover these cases:

- Complete and incomplete evidence scopes.
- Runtime modules, process assertions, Docker classification, and timer ordering.
- Cross-model process IDs, per-deployment-set callers, C7 by-key calls, same-line version selections,
  static, constant-resolved, and dynamic IDs, and caller blockers.
- Configuration apostrophes, timer dispositions, cron cycles, malformed ISO cycles,
  event-subprocess starts, method references, custom Scala interpolators, and
  direct/concatenated/builder REST paths.
- Non-timer and mixed due-date classifications, source-module timer scope, and pre-execution
  timer-observation guards.
- Exact affected-timer observations, repeated updates, early-deadline rejection, cleanup evidence,
  and stale dependent-command guards.
- Semantically invalid caller inventories blocking deployment and process commands before execution.
- Stale source snapshots, path safety, and cwd-independent imports.
- Report replacement.

The tests use synthetic evidence. They do not access a Camunda cluster or deploy to a target.
A separate disposable Camunda 8.9.21 deployment observation appears in the
[deployment and timer preflight reference](../../skills/migrate-c7-to-c8-code/references/deployment-and-timer-preflight.md).
It is not part of this Python suite.

From the repository root, run:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```
