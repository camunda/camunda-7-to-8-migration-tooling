# Validation evidence regression fixture

This fixture preserves the contradiction in #2926. `MIGRATION_REPORT.md` claims `READY` for nine
modules and ten converted BPMN models. The evidence shows a working Docker daemon, failing
Testcontainers and non-Docker tests, an invalid client configuration, a failed order-model lint
and deployment, and two non-executable runtime JARs. Evidence for the remaining models, the
downstream message assertion, and a repeating timer preflight is absent.

The test copies the fixture into a temporary project and creates small model files with the
recorded process IDs. It then runs the gate. The gate changes the old claim to `NOT READY`.

The other tests cover a complete passing scope, independent failures, missing records, runtime
modules, process assertions, Docker classification, timer ordering, path safety, and report
replacement. They exercise the command recorder without a Camunda cluster.

From the repository root, run:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```
