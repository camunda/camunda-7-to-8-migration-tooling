# Validation evidence regression fixture

This fixture reproduces a migration report that claims readiness without executable evidence. The
report describes nine modules and ten converted BPMN resources.

The recorded evidence contradicts several claims:

- Spring rejects the configured client mode, and two non-Docker web tests fail.
- Docker responds, but Testcontainers cannot select a valid environment.
- All ten converted BPMN resources fail compatibility lint. The order model also fails deployment.
- The message test does not assert that the downstream process instance starts.
- Two runtime JARs do not launch their entry point classes.
- Other required model, process, and timer checks are missing.

From the repository root, run the regression test with Python 3:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```

The test copies the fixture before it runs the validator. The committed report keeps its original
contradictory claims.
