# Pattern Catalog Sources

## Code Patterns

The code pattern catalog lives in the [GitHub directory](https://github.com/camunda/camunda-7-to-8-migration-tooling/tree/main/code-conversion/patterns/).
Fetch individual catalog files from
`https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/<catalog-path>`.

The catalog is the source of truth for every API mapping, artifact id, and version-specific
workaround. CI regenerates `code-conversion/patterns/README.md` and
`code-conversion/patterns/ALL_IN_ONE.md` during validation, then checks that they
match the generated catalog. CI does not commit generated changes. The catalog and this skill can
change together.
Where the catalog and this skill disagree, the catalog wins. Report the disagreement.

**Fetch only the files the code inventory needs.** `ALL_IN_ONE.md` concatenates the whole catalog into
one 12,000-word file. Fetching it whole costs more context than a migration usually needs.
`code-transform-checklist.md` names the catalog path for each of its items 1 to 6. Map the Type column
of the code inventory to those items, then fetch that set:

| Inventory Type | Catalog path |
|---|---|
| Config, dependencies | `10-general/dependencies.md` |
| Any variable handling | `10-general/process-variables.md` |
| Client code | `20-client-code/10-process-engine/README.md` |
| JavaDelegate | `30-glue-code/10-java-spring-delegate/README.md` |
| External task worker | `30-glue-code/20-java-spring-external-task-worker/README.md` |
| Listener | `30-glue-code/30-java-spring-listeners/listeners.md` |
| HTTP connector code | `30-glue-code/outbound-http-rest-connector.md` |
| Test code | `40-test-assertions/10-assertions/README.md` |

Fetch `ALL_IN_ONE.md` only when the inventory spans most of the catalog, or when a path above returns
404 and you need to find the moved file. (MAY)

## Model/Diagram Patterns

For agentic model migration (M2), fetch the current diagram-conversion guidance:
`https://raw.githubusercontent.com/camunda/camunda-docs/main/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter.md`

## Generated Task Forms

Load these references before generating or linking a form:

- Camunda 7 Generated Task Forms, field types, and built-in validators:
  https://docs.camunda.org/manual/7.24/user-guide/task-forms/#generated-task-forms
- Camunda 7 `formData`, `formField`, `formProperty`, `constraint`, and `value` XML:
  https://docs.camunda.org/manual/7.24/reference/bpmn20/custom-extensions/extension-elements/
- Camunda 8 form concepts and JSON/form-js model:
  https://docs.camunda.io/docs/apis-tools/frontend-development/forms/introduction-to-forms/
- Camunda 8 form components and validation:
  https://docs.camunda.io/docs/components/modeler/forms/form-element-library/forms-element-library/
- Camunda 8 form data binding:
  https://docs.camunda.io/docs/components/modeler/forms/configuration/forms-config-data-binding/
- Camunda 8 user-task form linkage:
  https://docs.camunda.io/docs/components/modeler/bpmn/user-tasks/#user-task-forms
- form-js schema examples:
  https://github.com/bpmn-io/form-js/blob/develop/docs/FORM_SCHEMA.md

Use `form-migration.md` as the mapping contract. Documentation is not evidence that two similarly named validation properties have identical runtime semantics. Preserve its user-decision gates.

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
- Runtime since 8.8. History and identity since 8.9 (history requires RDBMS secondary storage)
