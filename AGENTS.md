# AGENTS.md – AI Agent Onboarding Guide

This file provides instructions for AI coding agents (GitHub Copilot, Claude, Cursor, etc.) working in this repository. Read this **before** making any changes.

---

## Repository Overview

This is the **Camunda 7 to 8 Migration Tooling** monorepo. It contains tools that help organizations migrate Camunda 7 process instances and data to Camunda 8.

**Key production-readiness milestones:**
- Runtime migrator → Camunda 8.8
- History migrator → Camunda 8.9

---

## Module Structure

The repository is a Maven multi-module project:

```
camunda-7-to-8-migration-tooling/
├── data-migrator/          # Core migration application (Java/Spring Boot)
│   ├── core/               # Migration logic (runtime, history, identity)
│   ├── plugins/cockpit/    # Camunda Cockpit plugin (React frontend)
│   ├── examples/           # Example variable/entity interceptors
│   ├── qa/                 # Integration and architecture tests
│   ├── distro/             # Distribution assembly configuration
│   └── assembly/           # Final packaging (tar.gz, zip)
├── diagram-converter/      # Converts C7 BPMN diagrams to C8 format
│   ├── core/               # Core conversion logic
│   ├── cli/                # CLI wrapper
│   ├── webapp/             # Web UI for diagram conversion
│   └── extension-example/  # Example custom element conversion
├── code-conversion/        # Code conversion utilities
│   ├── patterns/           # Conversion pattern catalog (Markdown)
│   ├── recipes/            # OpenRewrite recipes for code migration
│   └── api-mapping/        # React webapp mapping C7→C8 APIs
└── docs/                   # Architecture rules, testing guidelines, review checklist
```

---

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21 (required) |
| Maven | 3.8.6+ |
| Spring Boot | 4.x |
| Node.js | 20.x (for frontend modules) |
| Databases tested | H2, PostgreSQL, Oracle |

---

## Build Commands

> **Note:** The build requires Camunda Nexus credentials. These are configured via Vault in CI. For local builds without Nexus, dependencies must already be cached in your local Maven repository, or you can build modules that don't require private Camunda dependencies.

### Full builds

```bash
# Build and test everything (requires Nexus access)
mvn clean install

# Build without running tests
mvn clean install -DskipTests

# Build excluding Cockpit plugin (skips Node.js requirement)
mvn clean install -pl '!data-migrator/plugins/cockpit'
```

### Module-specific builds

```bash
# Data migrator only
cd data-migrator && mvn clean install -DskipTests

# Diagram converter only
cd diagram-converter && mvn verify -Pdistro,checkFormat --batch-mode

# Code conversion only
cd code-conversion && mvn verify -PcheckFormat --batch-mode
```

### Format checks (no tests needed)

```bash
# Check code format (fails if formatting is wrong)
cd data-migrator && mvn validate -PcheckFormat --batch-mode

# Auto-fix formatting
cd data-migrator && mvn validate -Pformat --batch-mode
```

### Frontend (Cockpit plugin and API mapping webapp)

```bash
# Cockpit plugin
cd data-migrator/plugins/cockpit/frontend
npm install && npm run build

# API mapping webapp
cd code-conversion/api-mapping
npm ci && npm run build

# Pattern catalog (code-conversion)
cd code-conversion/patterns
npm install
node generate-catalog.js
node generate-all-in-one.js
```

---

## Test Commands

```bash
# All unit tests
mvn test

# All tests including integration
mvn verify

# Architecture tests only (fast, catches structural violations)
mvn test -Dtest=ArchitectureTest -pl data-migrator/qa

# Integration tests with H2 (runtime migration)
cd data-migrator && mvn verify -Pintegration,runtime-only

# Integration tests with H2 (history migration)
cd data-migrator && mvn verify -Pintegration,history-only

# Integration tests with H2 (identity migration)
cd data-migrator && mvn verify -Pintegration,identity-only

# Integration tests with PostgreSQL
cd data-migrator && mvn verify -Pintegration,postgresql

# Integration tests with Oracle
cd data-migrator && mvn verify -Pintegration,oracle
```

---

## License Headers

**Every source file must include the Camunda license header.** The Maven build enforces this and will fail without it.

```
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
```

The exact text lives in `license/header.txt`. The Maven license plugin automatically checks all source files during the build.

---

## Code Conventions

### Java

- Follow standard Java conventions
- Use **`protected`** (not `private`) for fields, methods, and constructors in `io.camunda.migration.data..` classes — this is enforced by architecture tests
- Use SLF4J for logging; **never** use `System.out.println()` or `e.printStackTrace()` in production code
- Log messages must live in dedicated `*Logs` classes (e.g., `HistoryMigratorLogs`) as `public static final` constants
- `@Component`/`@Service` classes must reside in the `impl` sub-package or at the top-level `io.camunda.migration.data` package
- `@Configuration` classes must reside in the `..config..` sub-package
- Add JavaDoc for all public API changes

### Testing

- All tests in `qa/` must follow **black-box testing**: verify behavior through observable outputs (logs, C8 API queries) — **not** internal state
- Test class names must end with `Test`
- Test method names must start with `should`
- Tests must extend the appropriate abstract base class:
  - `RuntimeMigrationAbstractTest` for runtime tests
  - `HistoryMigrationAbstractTest` for history tests
  - `AbstractMigratorTest` for general tests
- Use `LogCapturer` + log constants (`*Logs` classes) for log assertions — never raw string literals
- Integration tests go in the `qa/` module; unit tests live next to the class under test
- See `docs/TESTING_GUIDELINES.md` for detailed examples and rules

### Commit messages

Follow conventional commits:

```
<type>(<scope>): <description>

[optional body]
related to #<issue-number>
```

**Types:** `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`  
**Scopes:** `core`, `runtime`, `history`, `identity`, `cockpit`, `diagram-converter`, `code-conversion`, `distro`, `deps`

---

## ⛔ Do-Not-Touch Zones

These areas **require explicit human approval** before modification:

| Path | Reason |
|------|--------|
| `.github/workflows/` | CI workflows; changes must be reviewed by a human maintainer |
| `license/` | License text; must not be altered |
| `data-migrator/plugins/cockpit/frontend/dist/` | Pre-built frontend artifact; regenerate via `npm run build` instead |

If your task touches these areas, **stop and ask** rather than making changes automatically.

---

## Common Patterns

### Adding a new migrator entity type

1. Add the entity type to `IdKeyMapper.IDENTITY_TYPES` (if it's an identity entity)
2. Implement the migration logic in `data-migrator/core/src/main/java/io/camunda/migration/data/impl/`
3. Add a corresponding `*Logs` class for log constants
4. Add integration tests in `data-migrator/qa/`

### Variable/entity interceptors

- Implement the `VariableInterceptor` or `EntityInterceptor` interface
- See `data-migrator/examples/variable-interceptor/` and `data-migrator/examples/entity-interceptor/` for reference
- Package as a standalone JAR placed on the classpath

### Multi-instance flow node IDs

Use `ConverterUtil.sanitizeFlowNodeId()` to strip the `#multiInstanceBody` suffix (`MigratorConstants.C7_MULTI_INSTANCE_BODY_SUFFIX`) from C7 activity IDs before querying C8 flowNodeId.

### Database-specific code

- Always test with H2, PostgreSQL, and Oracle profiles
- Use JDBC standards to maintain cross-database compatibility
- Add profile-specific tests in `data-migrator/qa/`

---

## CI/CD

GitHub Actions workflows run on every push and pull request:

| Job | Description |
|-----|-------------|
| `distro` | Compiles all modules, assembles distribution |
| `code-conversion` | Builds code-conversion, validates pattern catalog |
| `diagram-converter` | Builds and tests diagram converter |
| `compile-previous-version` | Compiles against the previous C8 version (API compatibility) |
| `it-runtime-h2` | Runtime migration integration tests (H2) |
| `it-history-h2` | History migration integration tests (H2) |
| `it-identity-h2` | Identity migration integration tests (H2) |
| `it-*-postgresql` | Integration tests with PostgreSQL |
| `it-*-oracle` | Integration tests with Oracle |
| `windows-h2` | Windows-specific build and tests |

**You cannot merge pull requests.** Address CI failures and wait for human review.

---

## Additional Resources

- [Official Documentation](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/data-migrator/)
- [Architecture Rules](docs/ARCHITECTURE_RULES.md)
- [Testing Guidelines](docs/TESTING_GUIDELINES.md)
- [Code Review Checklist](docs/CODE_REVIEW_CHECKLIST.md)
- [Issue Tracker](https://github.com/camunda/camunda-bpm-platform/issues?q=is%3Aissue+state%3Aopen+label%3Ascope%3Adata-migrator)
