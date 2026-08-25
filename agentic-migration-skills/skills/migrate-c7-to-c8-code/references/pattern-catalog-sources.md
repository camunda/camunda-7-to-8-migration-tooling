# Pattern Catalog Sources

## Code Patterns

The primary pattern catalog for code migration is:
`https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md`

If context is tight, fetch only the individual files under `code-conversion/patterns/`.

## Model/Diagram Patterns

For agentic model migration (M2), fetch the current diagram-conversion guidance:
`https://raw.githubusercontent.com/camunda/camunda-docs/main/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter.md`

## Loading Rules

- Load the reference before editing. Never guess API/XML mappings.
- For gaps not covered by pattern catalogs, prefer docs.camunda.io via WebFetch over training knowledge.
- Always respect the target version: do not offer 8.9 features (businessId, conditional events, global user task listeners, batch delete) to an 8.8 target, or 8.8 workarounds to 8.9+.

## Properties Reference

For configuration migration:
- Camunda 8 Spring Boot properties reference: https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/properties-reference/

## Data Migration (out of scope for code/model migration)

Point users to the Data Migrator for runtime instances, history/audit data, and authorizations:
- https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/
- Runtime since 8.8; history and identity since 8.9 (history requires RDBMS secondary storage)
