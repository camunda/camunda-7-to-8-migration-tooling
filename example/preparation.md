# Preparing Camunda 7 Code for Automated Migration

The code-conversion recipes (OpenRewrite) handle a wide range of Camunda 7 patterns,
but some coding styles are not yet supported. A small amount of preparation in
your Camunda 7 source **before** running the migration tooling can make the
difference between a fully automated conversion and one that requires manual
fixups afterwards.

This document lists the patterns we encountered and the simple refactorings that
kept us on the "happy path".

---

## 1. Use Field Injection (`@Autowired`) Instead of Constructor Injection

**Problem**

The `CleanupEngineDependencyRecipe` removes field declarations of engine service
types (`RuntimeService`, `ProcessEngine`, `TaskService`, `RepositoryService`)
and their imports. However, it only recognises **field-level** declarations — it
does **not** clean up constructor parameters or constructor body assignments.

**Before (not fully supported)**

```java
@RestController
public class MyController {

    private final RuntimeService runtimeService;

    public MyController(RuntimeService runtimeService) {   // ← left behind
        this.runtimeService = runtimeService;               // ← left behind
    }
}
```

After migration the constructor and its `RuntimeService` parameter remain,
causing a compilation error because the import was removed but the usage was not.

**After refactoring (fully supported)**

```java
@RestController
public class MyController {

    @Autowired
    private RuntimeService runtimeService;
}
```

The cleanup recipe removes the `@Autowired` field, the import, and the
`RemoveUnusedImports` recipe takes care of the `@Autowired` import if it becomes
unused.

> **Tip:** This is a mechanical change — search for constructor parameters whose
> type is one of the four engine services and convert them to `@Autowired`
> fields.

---

## 2. Use `getProcessInstanceId()` Instead of `getId()`

**Problem**

The `MigrateStartProcessInstanceMethodsRecipe` converts calls on
`ProcessInstance` results. It recognises `getProcessInstanceId()` and rewrites
it to `String.valueOf(getProcessInstanceKey())` (the Camunda 8 equivalent).
The shorter `getId()` method — which returns the same value in Camunda 7 — is
**not** recognised by the recipe and is left unchanged, causing a compilation
error because `ProcessInstanceEvent` (the C8 type) does not have `getId()`.

**Before (not converted)**

```java
ProcessInstance pi = runtimeService.startProcessInstanceByKey("myProcess");
return pi.getId();   // ← not recognised by the recipe
```

**After refactoring (fully converted)**

```java
ProcessInstance pi = runtimeService.startProcessInstanceByKey("myProcess");
return pi.getProcessInstanceId();   // ← converted to String.valueOf(pi.getProcessInstanceKey())
```

> **Tip:** A simple find-and-replace from `.getId()` to
> `.getProcessInstanceId()` on `ProcessInstance` variables is usually
> sufficient.

---

## Summary

| # | Pattern to avoid | Preferred alternative | Affected recipe |
|---|------------------|-----------------------|-----------------|
| 1 | Constructor-injected engine services | `@Autowired` field injection | `CleanupEngineDependencyRecipe` |
| 2 | `ProcessInstance.getId()` | `ProcessInstance.getProcessInstanceId()` | `MigrateStartProcessInstanceMethodsRecipe` |

These are minor, mechanical refactorings that can be applied to the Camunda 7
codebase before running `migrate.sh`. They ensure a clean, compilable output
from the automated migration without any manual post-processing.
