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

The tests reject source and converted paths that resolve to the same file. They reject converted
paths that alias another model's source or duplicate another converted path. They reject duplicate
module paths after path resolution. They reject manifests that omit Step 2 modules, models, or BPMN
processes, or misstate process executability. They reject timer inventories that omit or invent
repeating starts in converted BPMN. They also reject form-check waivers that conflict with the form
inventory, synthetic direct-start checks for non-standalone processes, waived applicable process
assertions, manual executable checks, evidence outside the logs directory, and suite logs or
commands reused across modules. They reject omitted Maven Failsafe and explicit Gradle test suites,
source BPMN form inventories that omit detected form categories, and Docker probe commands that only
mention `docker info`. They reject timer preflights without a safety plan and output paths that
identify the evidence input or each other.
The tests reject symlink and hard-link source aliases, XML type mismatches, and non-empty DMN
process or timer inventories. They reject `runtime_mode: none` when `src/main`, `pom.xml`, or a JAR
in `target/` exposes runtime entry-point markers. They accept either Spring Boot launch check when
the other is marked `not_applicable`.

From the repository root, run the regression test with Python 3:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```

The test copies the fixture before it runs the validator. The committed report keeps its original
contradictory claims.
