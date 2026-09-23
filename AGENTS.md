# AGENTS.md

> **Note:** This file delegates to the central Camunda agent guidance. Read and apply it before proceeding.

**Central guidance (intentionally follows the org-level `main` branch):**
https://github.com/camunda/.github/blob/main/AGENTS.md

The central guidance covers general agent behavior, contribution standards, testing discipline, and licensing. The repository-specific guidance below covers this repository's tools and build.

## Repository scope

This repository provides migration tooling for Camunda 7 to Camunda 8:

| Path | Purpose |
| --- | --- |
| `data-migrator/` | Runtime and history migration of Camunda 7 process instances and related data |
| `diagram-converter/` | BPMN/DMN analysis and conversion from Camunda 7 to Camunda 8 |
| `code-conversion/` | Java migration patterns, OpenRewrite recipes, and API mapping webapp |
| `agentic-migration-skills/` | Agent Skills package for interactive Camunda 7 code and BPMN/DMN migration |
| `docs/` | Repository architecture, testing, and review guidance |

The root [README](README.md) is the user-facing catalog of available tools and installation instructions.

### Do-not-touch zones

Do not modify these paths without explicit human approval:

- `.github/workflows/` - CI/CD pipelines
- `license/` - legal files
- `data-migrator/plugins/cockpit/frontend/dist/` - generated frontend output

## Work tracking and change evidence

Every implementation task must be linked to a GitHub issue or pull request before
the first code or documentation change. Find an existing tracking item or create
one; a commit message alone or an unlinked branch is not sufficient. Put the link
in the pull request description; the branch name may also identify the work, but
it does not replace the PR link. For a tracking issue, use `Closes #N` only when the
PR delivers the issue's full scope, and use `Refs #N` for a partial
implementation. For a tracking PR, use `Refs #N` or the PR URL; `Closes` does
not auto-close pull requests.

### Claim work before editing

Before editing, the implementer must make ownership and active work visible:

1. Assign the tracking item (issue or PR) to yourself and comment with the
   branch or PR that will carry the work.
2. Add the tracking item (issue or PR) to the repository's GitHub project
   (project `182`, owned by `camunda`) if it is not already present.
3. Set the project's `Status` field to `In Progress`.

The API-backed commands below show the issue workflow. When the tracking item is
a PR, replace `gh issue` with `gh pr`, `ISSUE` with the PR number, and
`ISSUE_URL` with the PR URL.

```bash
REPO=camunda/camunda-7-to-8-migration-tooling
ISSUE=1234
ISSUE_URL=https://github.com/$REPO/issues/$ISSUE

gh issue edit "$ISSUE" --repo "$REPO" --add-assignee @me
gh issue comment "$ISSUE" --repo "$REPO" \
  --body "Starting work on branch \`<branch-name>\`."
gh project item-add 182 --owner camunda --url "$ISSUE_URL"  # only if absent
gh project item-edit 182 --owner camunda --url "$ISSUE_URL" \
  --field Status --value "In Progress"
```

Confirm the state through the GitHub API before implementation and when handing
off the PR:

```bash
gh issue view "$ISSUE" --repo "$REPO" --json url,assignees,projectItems
```

For a machine-checkable guard, fail when the current actor is not assigned or
the issue is not active on the project board:

The guard requires the GitHub CLI (`gh`) and `jq` to be installed and available
on `PATH`.

```bash
ISSUE_STATE=$(gh issue view "$ISSUE" --repo "$REPO" --json assignees,projectItems)
ACTOR=$(gh api user --jq .login)
printf '%s\n' "$ISSUE_STATE" | jq -e \
  --arg actor "$ACTOR" \
  'any(.assignees[]; .login == $actor) and
   any(.projectItems[]; .status.name == "In Progress")' >/dev/null
```

An open implementation PR remains `In Progress` until it is merged. The tracking
item owner, project status, and linked PR must identify who is actively
responsible for the work; do not rely on an informal comment alone.

### Exceptions and bug evidence

An implementation without a linked issue or PR requires an explicit documented
exception in the PR's **Work tracking** section. The exception must state the
reason, scope, owner, and approver. Emergency work is not exempt from recording
this information, and a commit-only reference is never an exception.

Before fixing a bug, identify the defect class and capture a deterministic red
reproducer: a failing test, command, API check, or other observable evidence.
The PR must link that evidence, show the post-change green result, and explain
the category surface covered. Use one of these classes (or explain why `Other`
is necessary):

- Boundary or input validation
- Mapping or conversion
- Persistence or serialization
- Lifecycle, state, or concurrency
- Compatibility or external integration
- Configuration or observability
- Test, build, or CI infrastructure
- Other

The reproducer must cover the defect category, not only the reported instance.
For a non-bug change, mark the bug-evidence fields as not applicable rather than
inventing a classification.

## Architecture

- **data-migrator** is a Spring Boot, multi-module application that migrates runtime, history, and identity data.
- **diagram-converter** uses a two-phase visitor and conversion design. Visitors inspect the DOM without mutation; conversion steps then apply transformations and removals.
- **code-conversion** provides migration patterns and OpenRewrite recipes organized by asset type and phase (prepare, migrate, cleanup). Composite recipes preserve phase ordering.
- **agentic-migration-skills** packages `migrate-c7-to-c8-code` in the Agent Skills format. It combines deterministic OpenRewrite and Diagram Converter workflows with AI-assisted cleanup or model migration, and includes fixtures for regression coverage. Keep its `README.md`, `plugin.json`, `skills/`, and fixtures consistent when changing the skill.

See the module-specific instructions for implementation and testing details:

- [data-migrator/AGENTS.md](data-migrator/AGENTS.md)
- [diagram-converter/AGENTS.md](diagram-converter/AGENTS.md)
- [code-conversion/AGENTS.md](code-conversion/AGENTS.md)
- [agentic-migration-skills/AGENTS.md](agentic-migration-skills/AGENTS.md) - writing conventions for skill text

When you change any file under `agentic-migration-skills/`, read and apply
[agentic-migration-skills/AGENTS.md](agentic-migration-skills/AGENTS.md) before editing. Open the pull
request against `main`.

## Build pipeline

### Prerequisites

- Java 21 (`JAVA_HOME`)
- Maven 3.6+
- Docker for integration and e2e tests
- Node.js for frontend modules is installed by Maven and controlled by `pom.xml` (currently `v24.15.0`)

### Commands

```bash
# Full build and unit tests
mvn clean install

# Compile and package without tests
mvn clean install -DskipTests

# Unit tests and repository verification
mvn verify
```

`mvn verify` does not run data-migrator integration or e2e tests. Run them explicitly:

```bash
mvn verify -Pintegration
mvn verify -Pe2e
```

For scoped changes, use `-pl <module-path>` rather than rebuilding the entire reactor. The `checkFormat` profile is required for diagram-converter and code-conversion changes:

```bash
mvn verify -PcheckFormat -pl diagram-converter
mvn verify -PcheckFormat -pl code-conversion
```

## CI and release lines

GitHub Actions runs on pull requests, pushes to `main` and `maintenance/*`, and scheduled builds. `CI Summary Gate` (`ci-summary`) is the merge-blocking aggregate; the remaining compatibility matrix jobs are asynchronous unless explicitly enabled.

Use this release-line mapping for backports:

| Maintenance branch | Camunda release line |
| --- | --- |
| `maintenance/0.2` | Camunda 8.8 |
| `maintenance/0.3` | Camunda 8.9 |

For diagram-converter, the target version is an input independent of the libraries used to build a release line. Camunda 8.10 is the current stable/default target; keep the webapp selector, backend default, and regression tests aligned when changing that policy.

## Documentation

- [Migration Guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/)
- [Data Migrator](data-migrator/README.md)
- [Diagram Converter](diagram-converter/README.md)
- [Code Conversion](code-conversion/README.md)
- [Agentic Migration Skills](agentic-migration-skills/README.md)
