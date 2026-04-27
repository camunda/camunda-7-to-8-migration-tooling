# Data Migrator - Agent Instructions

See also the root [AGENTS.md](../AGENTS.md) for repo-wide conventions (commit messages, PR guidelines, CI, license headers).

## Overview

The data-migrator handles runtime and history migration of Camunda 7 process instances to Camunda 8. It is a Spring Boot application with a multi-module Maven structure.

## Module Structure

```
data-migrator/
├── core/                   # Core migration logic and application
├── distro/                 # Distribution packaging
├── assembly/               # Release assembly
├── plugins/cockpit/        # Camunda Cockpit plugin (React frontend)
├── examples/               # Example implementations (variable interceptors)
└── qa/
    ├── integration-tests/  # Database integration tests
    └── e2e-tests/          # End-to-end tests (Playwright)
```

## Build Commands

```bash
# Build data-migrator only
cd data-migrator && mvn clean install

# Build without tests
cd data-migrator && mvn clean install -DskipTests

# Exclude Cockpit plugin (requires Node.js)
cd data-migrator && mvn clean install -pl '!plugins/cockpit'

# Database-specific integration-test profiles
mvn verify -Pintegration -Ppostgresql
mvn verify -Pintegration -Poracle
mvn verify -Pintegration -Pmysql
```

### Integration & E2E Tests

The `qa/integration-tests` and `qa/e2e-tests` modules are **not included in the default build**. They require explicit Maven profiles:

```bash
# All integration tests (runtime + history + identity) on H2
mvn verify -Pintegration

# Runtime integration tests only
mvn verify -Pintegration,runtime-only

# History integration tests only
mvn verify -Pintegration,history-only

# Identity integration tests only
mvn verify -Pintegration,identity-only

# Integration tests on a specific database (requires Docker)
mvn verify -Pintegration -Ppostgresql
mvn verify -Pintegration -Poracle
mvn verify -Pintegration -Pmysql
mvn verify -Pintegration -Pmariadb
mvn verify -Pintegration -Psqlserver

# Combine: e.g. runtime tests on PostgreSQL
mvn verify -Pintegration,runtime-only -Ppostgresql

# E2E tests (requires Docker - pulls Camunda 7 & 8 images)
mvn verify -Pe2e

# Both integration and e2e
mvn verify -Pintegration -Pe2e
```

### Frontend (Cockpit Plugin)

```bash
cd data-migrator/plugins/cockpit/frontend
npm install
npm run dev    # dev build with watch
npm run build  # production build
```

## Java Conventions (data-migrator specific)

These rules are enforced by ArchUnit tests in `qa/integration-tests` (scans `io.camunda.migration.data` only):

- **No private methods, fields, or constructors** - use `protected` for extensibility
- Components go in `impl` package or top-level `io.camunda.migration.data`
- Configuration classes in `..config..` package
- Log classes named `*Logs` with only `public static final` fields
- Use SLF4J for logging - no `System.out`/`System.err`
- Error Prone enabled for compile-time checks

Run architecture validation: `mvn test -Dtest=ArchitectureTest -pl data-migrator/qa`

## Always-Green Policy

See root [AGENTS.md](../AGENTS.md#always-green-policy) for the full policy. Key points:
- Validate green baseline before AI-assisted work: `mvn clean install` (fast) or `mvn clean verify -Pintegration` (full)
- Some tests are `@Disabled` with tracked issues (eg #321, #428, #1103, camunda-bpm-platform#5235) — do not add more without a linked issue
- Never dismiss failures as pre-existing without proof

## Testing Rules

Read `docs/TESTING_GUIDELINES.md` for the full guide. Key principles:

- **Black-box testing** - verify observable behavior (logs, API responses), not internal state
- Tests MUST NOT access `..impl..` packages
- Use `LogCapturer` for behavioral verification
- Use log constants from `*Logs` classes (no string literals)
- Test class names end with `Test`
- Test method names start with `should`
- Follow Given-When-Then structure
- Keep comments to a minimum and do not add unnecessary comments when the code is self-explanatory
- Base classes: `AbstractMigratorTest`, `RuntimeMigrationAbstractTest`, `HistoryMigrationAbstractTest`

## Key Documentation

- `docs/ARCHITECTURE_RULES.md` - Enforced architectural constraints
- `docs/TESTING_GUIDELINES.md` - Comprehensive testing guide
- `docs/CODE_REVIEW_CHECKLIST.md` - Review standards
- `data-migrator/README.md` - Features, setup, and dev environment
