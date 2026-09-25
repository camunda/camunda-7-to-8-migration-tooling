# Active domain dependency fixture

This fixture uses a fake license-generator library with the Maven group
`org.camunda.bpm`. It does not use a Camunda 7 engine API. It contains no valid license keys or
credentials. Its `fixture-result-*` strings are inert test sentinels. `TYPE_A` and `TYPE_B` stand in
for the two license types because issue #2940 does not name them.

## Run the regression tests

From the repository root, run:

```bash
mvn test -f agentic-migration-skills/fixtures/domain-license-dependency/pom.xml
python3 agentic-migration-skills/fixtures/domain-license-dependency/test_migration_guidance.py
```

The Maven fixture builds the fake library, the Camunda 7 source project, and the expected Camunda 8
project. The tests in `expected-c8` cover both fixture license types in the create and update paths.
They also verify that both paths update directory membership.

## Compatible library case

1. Copy `c7-source` to a temporary project.
2. Migrate the copy with the `migrate-c7-to-c8-code` skill.
3. Compare the result with `expected-c8`.
4. Confirm that the migration keeps the compatible library and both active call paths.
5. Run the Maven fixture tests.

## No approved replacement case

Tell the skill that the library is incompatible with the target runtime and that no replacement has
project-owner approval. Compare the generated `MIGRATION_REPORT.md` with
`expected-blocked/MIGRATION_REPORT.md`. The report must record the dependency's uses, target
compatibility, classification, and decision. It must mark both call sites as `blocked` and record a
manual follow-up. The skill must not replace either path with an exception or fabricated license
material.
