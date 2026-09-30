# OpenRewrite recipes refactoring code from Camunda 7 to Camunda 8 

> [!NOTE]  
> The recipes remain under development. Expect them to work out of the box only in simple scenarios.
> You may need to extend them for your codebase.
>
> **For users:** See the [official documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/code-conversion/#refactoring-recipes-using-openrewrite) for how to use the recipes in your project.

## Overview

This directory contains [OpenRewrite recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes) for automated refactoring of Camunda 7 Java code to Camunda 8.

The recipes help automatically refactor:
- Client code using the Camunda 7 Java API
- Java delegates (glue code)
- External task workers
- Unit tests (work in progress)

Transformation examples can be found in the [code conversion patterns](../patterns/).

## Choose a migration path

Use recipes as an optional first pass, not as proof that a migration is complete. Compare both code
paths on representative classes when practical. Prefer an AI-first, pattern-guided migration for
semantic, mixed delegate/client, or complex code when a capable coding model is available.

| Recipe effect | Where it applies |
|---|---|
| Helps | Repeated, supported, primarily syntactic Java transformations and a deterministic first diff. |
| Can hurt | Semantic or mixed delegate/client code that needs context across APIs or business behavior. Generated scaffolding can add cleanup. |
| Neutral | Domain behavior, eventual consistency, transaction boundaries, architectural separation, and validation. |

Expect generated worker methods, generated names, TODOs, and cleanup after a recipe run. Compare each
generated worker with its source before you delete or rename legacy logic. Confirm its business logic,
inputs, outputs, exception behavior, and job type. A successful compilation does not confirm behavior.

### RepositoryService deployments

`AllClientRecipes` converts complete, standalone `RepositoryService.createDeployment()` chains
to `CamundaClient` deployment commands. It supports classpath resources, tenant IDs that can be
safely moved after resources, simple deployment names, and input-stream or string resources whose
arguments can be safely reordered. Deployment names have no Camunda 8 command equivalent and are
removed. Unsupported deployment chains and RepositoryService queries are kept with a TODO for
manual migration.

## Extending recipes

Some projects may need recipe extensions. For example, a delegate can extend a custom base class
such as `org.acme.MyJavaDelegate` that implements
`org.camunda.bpm.engine.delegate.JavaDelegate`. The out-of-the-box recipes do not detect that
inherited implementation.

Please read:
- [Developer Guide](developer_guide.md)

### 🏗️ Building

```bash
mvn clean install
```

### 🧪 Testing

```bash
mvn verify
```

## Available Recipes

The recipes are organized by code type and transformation phase:

| Type of Change | Client Code | Java Delegate | External Worker |
|----------------|-------------|---------------|-----------------|
| **Prepare** | AllClientPrepareRecipes | AllDelegatePrepareRecipes | AllExternalWorkerPrepareRecipes |
| **Migrate** | AllClientMigrateRecipes | AllDelegateMigrateRecipes | AllExternalWorkerMigrateRecipes |
| **Cleanup** | AllClientCleanupRecipes | AllDelegateCleanupRecipes | AllExternalWorkerCleanupRecipes |
| **Combined** | AllClientRecipes | AllDelegateRecipes | AllExternalWorkerRecipes |

### Camunda Spin variables

`AllClientMigrateRecipes` adds TODOs for used Spin types, `JSON(...)`, `XML(...)`, and
`SpinJsonNode.prop(...).stringValue()`. The JSON hint distinguishes parsing existing JSON text or
streams with Jackson from passing a POJO/Map directly; `String.valueOf(pojo)` is not JSON
serialization. The hints do not embed source expressions or automatically replace Spin calls. An
unused Spin import does not leave a migration warning after cleanup.

The typed-value prepare recipe unwraps known JSON `ObjectValue` builders only in direct
object-accepting call arguments or declarations/assignments it has migrated. Unknown formats,
builder-backed fields, multiple declarations, later-reassigned variables, and other expression
contexts remain for manual migration. A `private`, uninitialized `ObjectValue` field becomes
`Object` only when every assignment in its compilation unit uses a Camunda 7 typed-variable
getter with an equivalent untyped call and its other uses read the value through `.getValue()`.
The field also stays typed when an assignment's result feeds an `ObjectValue`-typed or other
unverified context; assignments used as statements or direct `Object`-accepting call arguments
can be migrated.
Fields accessed through typed-only methods or passed to code requiring `ObjectValue`, fields
accessible from other compilation units, fields also assigned builders, local-only typed getter
calls, and unsupported getter overloads stay `ObjectValue`. Inside preserved builders,
declarations, and assignments, safe nested field reads are still migrated without changing
typed getter/`getValue()` chains. Cleanup removes Spin dependencies after unused-import removal
only when no Spin types remain in source.

See the [user documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/code-conversion/#refactoring-recipes-using-openrewrite) for details on each recipe.

## Contributing
See [the contribution guide](../../README.md#contributing).

## Resources

- **User documentation:** [docs.camunda.io](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/code-conversion/#refactoring-recipes-using-openrewrite)
- **OpenRewrite documentation:** [docs.openrewrite.org](https://docs.openrewrite.org/)
- **Pattern catalog:** [../patterns/](../patterns/)
- **Example migration:** [camunda-7-to-8-migration-example](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example)
