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

For diagram-converter, the target version is an input independent of the libraries used to build a release line. Camunda 8.9 is the current stable/default target; keep the webapp selector, backend default, and regression tests aligned when changing that policy.

## Documentation

- [Migration Guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/)
- [Data Migrator](data-migrator/README.md)
- [Diagram Converter](diagram-converter/README.md)
- [Code Conversion](code-conversion/README.md)
- [Agentic Migration Skills](agentic-migration-skills/README.md)
