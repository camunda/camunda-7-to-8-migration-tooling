# Project Documentation and CI Readiness

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Project documentation means README files, runbooks, deployment guides, form guides, and operator instructions for project components.

## Step 2: Assessment

1. Inspect root and module README files, operational guides, deployment instructions, and form instructions.
2. Search documentation for C7 dependency coordinates, `org.camunda.bpm`, `ProcessEngine`, and `RuntimeService`.
3. Search for `camunda:formKey`, `embedded:app:`, `/camunda/app/`, `/engine-rest`, and rollback assumptions.
4. Inspect application profiles, port settings, worker startup commands, form resources, and process-test commands.
5. Inspect every existing CI workflow and the build or test scripts that it calls.
6. Record workflow triggers, packaging commands, configuration checks, BPMN lint, process tests, and C8/Docker runtime setup.
7. Compare each document and workflow with the code and model inventories.

Do not classify a document from its title alone.
Do not edit files during assessment.
Do not record credentials, tokens, or private endpoint values in `MIGRATION_REPORT.md`.

Use this table to classify each document:

| Evidence | Scope | Required action |
|---|---|---|
| The document describes a component selected for migration. | In scope | Propose an update for user confirmation. |
| The document describes a deliberately retained C7-only example. | Retained C7-only | Leave it unchanged and record why. |
| The document covers migrated and retained components. | Mixed | Update only sections for migrated components. Keep retained C7 instructions distinct. |
| The document's maintainer or scope is unclear. | Unknown | Record an open item and ask the user before editing. |

For each in-scope document, update its run instructions or record why it remains unchanged.
Record deferred documentation as an open item with a responsible person and the required user decision.
Keep examples for intentionally retained C7 projects accurate.
Do not rewrite those examples as Camunda 8 guidance.

## Step 3: Documentation updates

When the user approves the migration plan, inspect each in-scope document.
Compare it with migrated code, converted models, accepted forms, and application configuration.
Update commands, module paths, profiles, ports, external-cluster setup, and health checks from project configuration or a user decision.
Name every required worker and record its startup command and required input values.
Describe the selected form path and any form behavior that remains unsupported or unverified.
Record parity gaps, including transaction or rollback changes, as open items.
Record profiles, ports, worker commands, form decisions, and parity gaps for each updated document in `MIGRATION_REPORT.md`.
Never guess a URL, profile, port, worker name, form mapping, or parity decision.
If the project does not document a value, ask the user or leave an open item.
Never publish credentials, tokens, or private endpoint values in documentation, CI logs, or `MIGRATION_REPORT.md`.

While the user selects assessment-only or analyze-only, leave project files unchanged.
Record proposed documentation updates in `MIGRATION_REPORT.md`.

## CI readiness

Inspect the existing CI workflow before adding a check.
When the user approves a CI change, select the workflow with this table:

| Existing workflow | Action |
|---|---|
| A pull-request workflow can run every readiness check. | Add the checks there. |
| No suitable pull-request workflow exists. | Create a project-specific pull-request workflow. |

When a workflow publishes an image or deploys the application, run the readiness job first.
Keep unrelated build and deployment jobs unchanged.
Never change deployment triggers, credentials, or release gates without explicit user approval.

The project-specific gate must cover each applicable check:

| Check | Pass condition |
|---|---|
| Packaging | The Maven package command succeeds for each migrated Maven module. Use the project's package task for Gradle modules. |
| Configuration | The project's configuration-binding test or validator passes for each documented profile. |
| BPMN lint | Every in-scope converted BPMN or DMN file passes lint for the selected target version. |
| Process tests | Relevant process tests pass with a working target-compatible C8 runtime and required Docker services. |

When no configuration-binding test or validator exists, add a check for each migrated profile.
Process tests must check application startup, deployed-resource matches, and required worker inputs.
Include form behavior when a migrated process uses a form.
Use existing project test tooling and runtime configuration.
Do not claim readiness from a package or compile result alone.
If the model inventory has no in-scope model, then mark BPMN lint as not applicable.
Record the inventory evidence for each not-applicable check.

If no relevant process test exists, then ask the user to approve adding a test or a manual alternative.
When the user approves a process test, add it before the readiness gate.
If CI cannot start the required C8/Docker runtime, then ask the user to approve a manual alternative.
If the user declines a CI change, then ask the user to approve a manual alternative.
If the user declines both options, then leave the readiness verdict at `needs review`.
Record the approver, responsible person, prerequisites, commands, result, and date for each approved manual alternative.
Run every required manual check before reporting it as passed.
A manual plan without a result does not establish readiness.

## `MIGRATION_REPORT.md` sections

Keep these sections in the report:

### Project documentation inventory

Use a table with these columns: Path, C7 evidence, Scope, Decision, Validation or open item.
List every in-scope, mixed, retained C7-only, and unknown document.
Record every update, retained document, user decision, or open item.

### CI inventory

Use a table with these columns: Workflow, Existing checks, Readiness gaps, Decision or action.
List every workflow that builds, tests, packages, publishes, or deploys migrated components.

### Project readiness

Use a table with these columns: Check, Command or workflow, Runtime, Result, Evidence.
Record every applicable packaging, configuration, BPMN lint, and process-test result.
Replace sensitive values with `<redacted>`.
Record a CI run link only when it does not expose private endpoints or credentials.
While the user selects assessment-only or analyze-only, record checks as `not run`.
Set the readiness verdict to `not assessed`.

Use this readiness verdict table:

| Verdict | Condition |
|---|---|
| `ready` | Every required check passes, and each in-scope document matches migrated assets and configuration. |
| `needs review` | A check is not run, a document is deferred or stale, scope is unclear, or a user decision is missing. |
| `blocked` | A required check fails or a required runtime dependency is unavailable. |
| `not assessed` | The user selects assessment-only or analyze-only and the migration checks do not run. |

Set the verdict to `ready` only after every required CI or approved manual check passes.
Keep failed, unavailable, and not-run checks visible in the report.
