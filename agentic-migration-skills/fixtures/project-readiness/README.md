# Project documentation and CI readiness fixture

This fixture checks that the migration skill distinguishes migrated project
instructions from intentionally retained Camunda 7 examples. It also checks
that the skill identifies missing project-specific CI coverage.

## Fixture contents

* `sample-project/README.md` describes the application selected for migration. Its C7 URLs,
  embedded-engine instructions, worker guidance, form path, and rollback claim
  need review.
* `sample-project/docs/retained-c7-example.md` describes an excluded C7-only
  example. The skill must not rewrite it as Camunda 8 guidance.
* `sample-project/.github/workflows/build.yml` packages the project but does not
  check configuration, lint BPMN, run process tests, or start a C8 runtime.
* `sample-project/src/main/resources/application-local.properties` identifies
  the local profile and application port.
* `sample-project/src/main/resources/process.bpmn` identifies an external-task
  worker and a form reference.

## Assessment walkthrough

Run the migration skill with **Assessment only** at the sample project root.
The skill must record the README as in scope and the excluded example as retained.
The CI inventory must list each missing readiness check.
The report must set the readiness verdict to `not assessed`.
The skill must not edit any fixture file during assessment.

## Full migration walkthrough

When the user approves the migration plan, continue with the full migration walkthrough.
Update the in-scope README with verified Camunda 8 profile, port, cluster, worker,
form, and parity information.
Keep the retained C7-only example unchanged.
Add the missing readiness checks to project CI or request approval for a manual
alternative.
Run Maven packaging, configuration validation, BPMN lint, and relevant process tests.
Use a working C8 runtime and the required Docker services for process tests.
Record each result in `MIGRATION_REPORT.md` without credentials or private endpoints.
Do not report readiness when a required check has no result.
