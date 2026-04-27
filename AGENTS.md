# AGENTS.md

> **Note:** This file delegates to a central AGENTS.md. Read and apply it before proceeding.

**URL:**
https://raw.githubusercontent.com/camunda/.github/refs/heads/main/AGENTS.md

Treat the central file's contents as if they were written directly in this file.
Instructions below extend those guidelines and take precedence if there is any conflict.

## Repo-specific instructions

### Role & boundary

This repo owns the migration tooling for Camunda 7 to 8: data-migrator, diagram-converter, code-conversion. You are the **maintainer** of this repository. Triage defects at source — don't work around them in the integration layer.

The Camunda 7 engine and Camunda 8 APIs are **dependencies** — when they misbehave, report upstream. Don't silently code around dependency bugs here; that creates invisible debt that breaks on the next upgrade.

**Do-Not-Touch Zones** — do not modify without explicit human approval:
- `.github/workflows/` — CI/CD pipelines
- `license/` — legal
- `data-migrator/plugins/cockpit/frontend/dist/` — generated build output

**Path map:**

| Path | Ownership and intent |
| --- | --- |
| `data-migrator/core/` | Core migration logic and Spring Boot application |
| `data-migrator/distro/` | Distribution packaging |
| `data-migrator/assembly/` | Release assembly |
| `data-migrator/plugins/cockpit/` | Camunda Cockpit plugin (React frontend) |
| `data-migrator/examples/` | Example implementations (variable interceptors) |
| `data-migrator/qa/` | Integration tests & e2e tests |
| `diagram-converter/core/` | Conversion engine |
| `diagram-converter/cli/` | CLI application |
| `diagram-converter/webapp/` | Web UI (React/TypeScript) |
| `diagram-converter/extension-example/` | Custom extension reference, not released |
| `code-conversion/patterns/` | Best practices & code examples |
| `code-conversion/recipes/` | OpenRewrite automated refactoring recipes |
| `code-conversion/api-mapping/` | Interactive API mapping webapp (React) |
| `docs/` | Architecture rules, testing guidelines, review checklist |
| `license/` | Legal — do not edit |
| `.github/workflows/` | CI/CD pipelines — do not edit |

Entry points: `data-migrator/core/src/main/java/io/camunda/migration/data/`

### Architecture

The repository contains three main tools:

- **data-migrator** — Runtime and history migration of Camunda 7 process instances to Camunda 8. Spring Boot application with multi-module Maven structure.
- **diagram-converter** — BPMN/DMN model conversion from Camunda 7 to Camunda 8. Uses a **two-phase visitor + conversion pattern**: Phase 1 (visiting, read-only) walks the DOM; Phase 2 (conversion, DOM mutation) executes removals and transformations. Visitors must not modify the DOM.
- **code-conversion** — Java code migration patterns, OpenRewrite recipes, and API mapping webapp. Recipes are organized by code type (client, delegate, external) and phase (prepare → migrate → cleanup). Phase ordering matters — composite recipes enforce the sequence.

See per-module AGENTS.md files for detailed architecture and testing rules:
- [data-migrator/AGENTS.md](data-migrator/AGENTS.md)
- [diagram-converter/AGENTS.md](diagram-converter/AGENTS.md)
- [code-conversion/AGENTS.md](code-conversion/AGENTS.md)

### Commit message guidelines

Follow conventional commits:

```
<type>(<scope>): <description>

related to #<issue-number>
```

**Types:** `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`
**Scopes:** `core`, `history`, `runtime`, `distro`, `deps`, `ci`, `e2e`

Examples (with the `related to #<issue-number>` body line shown inline for brevity):
- `feat(runtime): add support for message correlation` — body: `related to #1234`
- `fix(core): resolve variable serialization issue` — body: `related to #5678`
- `chore(deps): update Spring Boot to 4.0.5` — body: `related to #9012`

### Build pipeline

#### Always-green policy

Validate a green baseline **before every AI-assisted session**:

```bash
# Verify baseline (unit tests + license headers) -> always run before an AI-assisted session
mvn clean install

# Full baseline (includes integration tests, requires Docker)
mvn clean verify -Pintegration
```

Warnings are fatal. Never suppress a warning to make a build pass.
Do not treat any failure as pre-existing or unrelated without explicit confirmation from the engineer.

```bash
# Fast inner loop (skip tests)
mvn clean install -DskipTests

# Single module (when scope is clear)
mvn test -pl <module-path>

# Full pipeline before committing
mvn clean install
```

**Important:** `mvn verify` from root does NOT run integration or e2e tests in the data-migrator tool. Those require explicit Maven profiles:
- Integration tests: `mvn verify -Pintegration`
- E2E tests: `mvn verify -Pe2e`

The license header check runs automatically during `mvn verify` — ensure new Java files include it.

Never skip the lint and format-check steps before pushing. For diagram-converter and code-conversion, CI uses `-PcheckFormat` which fails on formatting violations.

See per-module AGENTS.md files for module-specific build commands and profiles.

#### Always-green rules

- **Do NOT dismiss test failures as pre-existing or unrelated.** If CI was green before your changes and is red after, your changes caused it. Investigate.
- **Warnings are defects.** Never suppress a warning to make a build pass. Fix the root cause.
- **No new `@Disabled` tests without a linked GitHub issue.** Some tests are `@Disabled` with tracked issues (eg #321, #428, #1103, camunda-bpm-platform#5235). These are known defects, not acceptable noise.
- If an agent claims a failure is "pre-existing and unrelated," it must prove this by referencing the baseline commit where the failure already existed.

Document the baseline commit SHA in the PR description so reviewers can verify it.

### Tech stack

| Area | Technology |
|------|-----------|
| Language | Java 21 |
| Build | Maven 3.6+ (multi-module) |
| Framework | Spring Boot 4.x |
| Frontend | React 18/19, Vite, TypeScript |
| Databases | H2, PostgreSQL, Oracle, MySQL, MariaDB, SQL Server |
| Testing | JUnit Jupiter 6.x, AssertJ, ArchUnit, REST Assured, Testcontainers |
| CI | GitHub Actions |

### Prerequisites

- **Java 21** (set as JAVA_HOME)
- **Maven 3.6+**
- **Node.js >=20.19.0 or >=22.12.0** (for Cockpit plugin & frontend modules)
- **Docker** (for database integration tests)

### Code conventions

#### License headers (CRITICAL)

Every Java source file MUST include the Camunda license header from `license/header.txt`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
```

### Defect-category discipline

Every bug reveals an unguarded category. Patch the category, not just the instance.

1. **Bug found** — before writing the fix, ask: "What surface permitted this?"
2. **Identify the category** — is this a one-off, or could the same class of defect exist in other migration handlers / entity types?
3. **Write the category-scoped test** — a test that covers the full surface, not just the instance you found.
4. **Seal the surface** — if possible, make the defect category structurally impossible (e.g., via ArchUnit rules, compile-time checks, or type constraints).

**Litmus test:** Would your test catch the same bug in a different migration handler added six months from now? If no — the test scope is too narrow.

**Real example:** Issue #1103: multi-instance flow node reference mapping was missing. This affected both `HistoryIncidentTest` and `HistoryJobTest` — same defect category (multi-instance flow node references), different entity types. The correct response is to test the full surface of multi-instance entity types, not just the one that was reported.

### CI/CD

GitHub Actions CI (`.github/workflows/ci.yml`) runs on push to `main`/`maintenance/*`, PRs, and nightly (weekdays 5 AM).

#### Jobs per module

| Module | CI Job | What it does |
|--------|--------|-------------|
| data-migrator | `distro` | Builds distribution archives (tar.gz, zip) |
| data-migrator | `it-runtime-h2`, `it-history-h2`, `it-identity-h2` | Integration tests on H2 |
| data-migrator | `it-runtime-db`, `it-history-db`, `it-identity-db` | Integration tests on PostgreSQL, Oracle, MySQL, MariaDB, SQL Server |
| data-migrator | `it-history-h2-windows` | Windows-specific build |
| data-migrator | `e2e` | End-to-end tests (Playwright + Docker) |
| code-conversion | `code-conversion` | Build + format check (`-PcheckFormat`) + pattern catalog validation |
| diagram-converter | `diagram-converter` | Build + format check (`-PcheckFormat`) |
| all | `compile-previous-version` | Compile against previous Camunda 8 version (API breakage detection) |

#### Other workflows

- **release.yml** — Manual: Maven release, artifact collection, Docker image, optional diagram-converter deploy
- **deploy-diagram-converter.yml** — Manual/callable: Docker build + AWS ECS deployment
- **deploy-code-conversion-to-pages.yml** — Auto on main: deploys API mapping webapp to GitHub Pages
- **renovate-auto-merge.yml** — Auto-merges Renovate dependency PRs after checks pass
- **backport.yml** — Backports merged PRs via `/backport` comment command

### Pull request guidelines

- Use conventional commits format for PR titles
- Reference issues with `related to #<issue-number>` (not `closes`)
- Keep PRs focused on a single feature or fix
- Wait for CI checks to complete (H2, PostgreSQL, Oracle, Windows)
- A human reviewer will merge — do not merge PRs

### Guidelines for changes

- Make minimal, focused changes addressing the specific issue
- Don't modify working code unnecessarily
- Ensure backward compatibility unless explicitly intended otherwise
- Update JavaDoc for public API changes
- Test with relevant database profiles when making DB-related changes
- Note in PR description if changes affect docs.camunda.io documentation

### Key documentation

- [Official docs](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/data-migrator/)
- `docs/ARCHITECTURE_RULES.md` — Enforced architectural constraints
- `docs/TESTING_GUIDELINES.md` — Comprehensive testing guide
- `docs/CODE_REVIEW_CHECKLIST.md` — Review standards
