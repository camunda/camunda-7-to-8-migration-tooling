# Validation evidence regression fixture

This test preserves the contradiction in #2926. It creates a nine-module, ten-model project and
synthetic evidence in a temporary directory. The fixture stores no logs, manifests, or reports.
The report claims `READY`, but configuration, tests, lint, deployment, and JAR launches fail.
Other model checks, a message assertion, and a timer preflight are absent.

The gate changes that claim to `NOT READY`.

The tests cover these cases:

- Complete and incomplete evidence scopes.
- Runtime modules, process assertions, Docker classification, and timer ordering.
- Cross-model process IDs, C7 by-key calls, C8 version selection, and interpolated-source blockers.
- Timer dispositions, observed deployment evidence, method references, and REST due-date paths.
- Repeated updates, cleanup evidence, stale source snapshots, path safety, and cwd-independent imports.
- Report replacement.

The tests use synthetic evidence. They do not access a Camunda cluster or deploy to a target.
A separate disposable Camunda 8.9.21 deployment observation appears in the
[deployment and timer preflight reference](../../skills/migrate-c7-to-c8-code/references/deployment-and-timer-preflight.md).
It is not part of this Python suite.

From the repository root, run:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```
