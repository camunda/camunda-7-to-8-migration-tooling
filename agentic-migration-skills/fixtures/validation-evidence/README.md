# Validation evidence regression fixture

This fixture reproduces a migration report that claims readiness without executable evidence. The
report describes nine modules and ten converted BPMN resources. Its independent Step 2 inventory
lists the original modules and model paths.

The fixture stores captured evidence under `.camunda-migration/validation/logs/`.

The recorded evidence contradicts several claims:

- Spring rejects the configured client mode, and two non-Docker web tests fail.
- Docker responds, but Testcontainers cannot select a valid environment.
- All ten converted BPMN resources fail compatibility lint. The order model also fails deployment.
- The message test does not assert that the downstream process instance starts.
- Two runtime JARs do not launch their entry point classes.
- Other required model, process, and timer checks are missing.

The tests reject source and converted paths that resolve to the same file. They reject manifest
inventories that omit Step 2 modules or models. They reject form-check waivers that conflict with
the form inventory and synthetic direct-start checks for non-standalone processes. They also reject
waived applicable process assertions, manual executable checks, evidence outside the logs directory,
reused suite logs, and timer preflights without a safety plan.

From the repository root, run the regression test with Python 3:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```

The test copies the fixture before it runs the validator. The committed report keeps its original
contradictory claims.
