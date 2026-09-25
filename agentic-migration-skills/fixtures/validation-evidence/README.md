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
inventory, synthetic direct-start checks for non-standalone processes, and process applicability
values that conflict with converted BPMN or accepted source forms. They reject manual executable
checks, evidence outside the logs directory, and suite logs or commands reused across modules. They
reject omitted default Maven Surefire, Maven Failsafe, and
Gradle `test` suites. They reject explicit Gradle test suites and manifest suites without matching
build configuration. They reject source BPMN form inventories that omit detected form categories
and Docker probe commands that only mention `docker info`. They reject timer preflights without a
safety plan and output paths that identify the evidence input, each other, evidence logs, or
reserved validation files. They reject converted model symlinks that resolve outside the project
root without parsing the linked files.
They reject summary and report paths inside the evidence log directory, including when the manifest is malformed.
They reject summary and report hard links to log files when the manifest is malformed or its `checks` field is invalid.
They reject `deployable: false` waivers and passed test, lint, deployment, process, or timer commands
that omit their target or check selector.
They reject target and selector values that match only by substring, altered path punctuation, or
an unrelated command option.
They reject commands with CR/LF and test commands that skip or exclude tests, use dry-run options,
or ignore test failures.
They also reject standalone processes without passing worker-input inventory evidence, omitted
missing-input direct-start scenarios, and malformed report gates that leave stale `READY` claims.
The tests reject executed checks that reuse evidence files.
They reject unmarked readiness claims.
They preserve report sections between duplicate gates.
They leave unmatched gates without safe boundaries untouched.
The tests reject mismatched source form IDs, runtime-scan symlinks outside the project root, and
module suite discovery after path validation fails.
The tests reject symlink and hard-link source aliases, XML type mismatches, and non-empty DMN
process or timer inventories. They reject `runtime_mode: none` when `src/main`, `pom.xml`, or a JAR
in `target/` exposes runtime entry-point markers, including qualified Spring Boot annotations and
calls. They reject malformed source BPMN and DMN XML. They reject `source_has_di` declarations that
disagree with source BPMN DI. They detect Gradle `Test` tasks declared with `tasks.named`. They
accept either Spring Boot launch check when the other is marked `not_applicable`.

From the repository root, run the regression test with Python 3:

```sh
python3 -m unittest discover -s agentic-migration-skills/fixtures/validation-evidence -p 'test_validate_migration_evidence.py'
```

The test copies the fixture before it runs the validator. The committed report keeps its original
contradictory claims.
