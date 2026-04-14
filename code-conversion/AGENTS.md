# Code Conversion - Agent Instructions

See also the root [AGENTS.md](../AGENTS.md) for repo-wide conventions (commit messages, PR guidelines, CI, license headers, always-green policy, defect-category discipline).

## Overview

The code-conversion module helps migrate Camunda 7 Java code to Camunda 8. Three submodules: `recipes/` (OpenRewrite automated refactoring), `patterns/` (markdown migration pattern catalog), `api-mapping/` (React webapp mapping C7→C8 API endpoints).

## Build Commands

```bash
# Recipes: build + test
cd code-conversion && mvn clean install

# Recipes: format check (what CI runs)
mvn verify -PcheckFormat

# Patterns: regenerate catalog (must be in sync or CI fails)
cd code-conversion/patterns && node generate-catalog.js && node generate-all-in-one.js

# API-mapping: build frontend
cd code-conversion/api-mapping && npm ci && npm run build
```

Key profiles: `distro` (default, all modules), `central-release` (recipes only, for Maven Central). `checkFormat` enforces Spotless formatting.

## Recipes Module

OpenRewrite recipes for automated Java code refactoring. Organized by code type and migration phase:

- **Code types:** client (ProcessEngine), delegate (JavaDelegate), external (ExternalTaskHandler)
- **Phases:** prepare (add C8 deps, inject stubs) → migrate (refactor code) → cleanup (remove C7 code/imports)

Phase ordering matters — composite recipes enforce this sequence.

Recipes are declared in YAML at `src/main/resources/META-INF/rewrite/*.yml`. YAML files use Maven resource filtering for `${version.camunda-8}` substitution.

Recipes are WIP — expected to work out-of-the-box only in simple scenarios.

## Patterns Module

Markdown-based migration pattern catalog. Content is organized in numbered directories (10-general, 20-client-code, 30-glue-code, 40-test-assertions).

**CI enforces catalog freshness:** `generate-catalog.js` and `generate-all-in-one.js` regenerate README.md and ALL_IN_ONE.md from the directory structure. CI runs these scripts then checks `git diff --exit-code` — if output doesn't match committed files, the build fails. Always run the generation scripts after editing pattern content.

## API-Mapping Module

React 19 + Vite SPA deployed to GitHub Pages at https://camunda.github.io/camunda-7-to-8-migration-tooling/.

Mapping data is written as JSX each mapping file in `src/mappings/` exports endpoint mapping arrays with rich HTML rendering via react-markdown. OpenAPI specs live in `src/openapi/`.