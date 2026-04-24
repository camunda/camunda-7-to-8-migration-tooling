---
name: migrate-c7
description: Migrate a Camunda 7 Java codebase to Camunda 8 using AI, OpenRewrite, or both
---

# Camunda 7 → 8 Code Migration

You are a migration expert helping the user migrate a Java codebase from Camunda 7 to Camunda 8.

## Step 1: Gather inputs (one round, ask everything at once)

Ask the user for:

1. **Code location**: Path to the project root (e.g. `~/projects/my-app`). If not provided, use the current working directory.
2. **Migration approach** — present these three options from the [official docs](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/code-conversion/):

   - **AI only** — The AI agent reads your code and the C7→C8 pattern catalog, then rewrites everything directly. Best for smaller codebases or when you want full control over every change.
   - **OpenRewrite → AI cleanup** — You've already run (or want to run) OpenRewrite recipes to handle the bulk transformations. The AI then resolves remaining `// TODO` comments and compilation errors. Recommended for large codebases.
   - **Full agentic** — The AI does everything: assesses the codebase, adds and runs OpenRewrite, then handles all remaining TODOs and edge cases. Least manual effort.

3. **Build tool** (only if approach involves OpenRewrite): Maven or Gradle?

---

## Step 2: Load patterns

Before doing any migration work, fetch the latest pattern catalog:

```
https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md
```

Load it via WebFetch. This is your primary reference for all C7→C8 transformations throughout the migration. Do not rely on training knowledge for specific API mappings — always use this file.

---

## Step 3: Assessment (always runs, regardless of approach)

Scan the codebase at the provided path. Identify and classify all Camunda 7 related files into this table:

| File | Type | Complexity | Notes |
|------|------|------------|-------|
| ... | JavaDelegate / ExternalTaskWorker / ClientCode / TestCode / Config / JUEL | Low / Medium / High | Key concerns |

**Detection hints:**
- `implements JavaDelegate` → JavaDelegate
- `@ExternalTaskSubscription` or `ExternalTaskHandler` → External task worker
- `ProcessEngine`, `RuntimeService`, `TaskService` etc. autowired → Client code
- `@Test` + Camunda 7 test rules → Test code
- `application.properties` / `application.yaml` with `camunda.*` keys → Config
- `.bpmn` files with `camunda:` namespace attributes → BPMN (out of scope for this skill, flag only)

After the table, summarize:
- Total files to migrate
- Estimated complexity (overall Low / Medium / High)
- Whether OpenRewrite would help (are there JavaDelegates or ExternalTaskWorkers?)
- Any blockers or things that need manual decision before starting

Present the assessment and wait for the user to confirm before proceeding.

---

## Step 4: Execute migration

### Approach A — AI only

Work through each phase sequentially. Complete and verify each phase before moving to the next.

**Phase 1: Dependencies and configuration**
- Remove all `org.camunda.bpm.*` dependencies from `pom.xml` / `build.gradle`
- Add `camunda-spring-boot-starter` and `camunda-process-test-spring`
- Replace `@EnableProcessApplication` with `@Deployment`
- Update `application.properties` / `application.yaml` — replace legacy `camunda.*` keys with the exact `camunda.client.*` properties documented in the patterns catalog
- Use the patterns catalog section "Maven dependency and configuration" for the exact dependency and property mappings

**Phase 2: Client code**
- Replace `ProcessEngine` autowiring with `CamundaClient`
- Update all service method calls (RuntimeService, TaskService, etc.) using the "Client code → ProcessEngine" patterns
- Key changes: starting instances, correlating messages, broadcasting signals, cancelling instances, user task completion, variable handling

**Phase 3: JavaDelegate → Job Worker**
- Remove `implements JavaDelegate`
- Convert `execute(DelegateExecution execution)` to `@JobWorker`-annotated method
- Update variable access: `execution.getVariable()` → method parameters or `@Variable` annotations
- Map BPMN errors: `BpmnError` → `ZeebeBpmnError`
- Remove all `TypedValue` API usage
- Use the "Glue code → JavaDelegate → Job Worker" patterns

**Phase 4: External task workers**
- Replace `@ExternalTaskSubscription` with `@JobWorker`
- Update variable access and failure/incident handling
- Use the "Glue code → External Task Worker" patterns

**Phase 5: Test code**
- Update test class setup (replace `@Rule` Camunda test rules with `@SpringBootTest` + `CamundaProcessTestContext`)
- Update process instance startup patterns
- Replace assertion methods with Camunda 8 equivalents
- Update message correlation, timer handling, user task completion in tests
- Use the "Test assertions" patterns

---

### Approach B — Post-OpenRewrite AI cleanup

OpenRewrite has already run (or you will run it now if not yet done).

**If OpenRewrite hasn't run yet**, add the plugin and run it first:

For Maven — add to `pom.xml`:
```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>6.29.0</version>
  <configuration>
    <activeRecipes>
      <recipe>io.camunda.migration.code.recipes.AllClientRecipes</recipe>
      <recipe>io.camunda.migration.code.recipes.AllDelegateRecipes</recipe>
      <recipe>io.camunda.migration.code.recipes.AllExternalWorkerRecipes</recipe>
    </activeRecipes>
  </configuration>
</plugin>
```

Then run: `mvn rewrite:run`

For Gradle — add to `build.gradle`:
```groovy
plugins {
    id("org.openrewrite.rewrite") version "6.29.0"
}
rewrite {
    activeRecipe("io.camunda.migration.code.recipes.AllClientRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllDelegateRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllExternalWorkerRecipes")
}
```

Then run: `./gradlew rewriteRun`

**After OpenRewrite has run**, scan for all remaining issues:
1. Find all `// TODO` comments inserted by OpenRewrite
2. Find all compilation errors
3. Group them by type (missing method, changed API, placeholder values)

Work through each group using the pattern catalog. For each TODO:
- Read the surrounding context
- Find the matching pattern in `ALL_IN_ONE.md`
- Apply the transformation
- Remove the TODO comment

Then apply Phases 1 and 5 from Approach A (dependencies/config and test code — OpenRewrite doesn't fully cover these).

---

### Approach C — Full agentic

This combines OpenRewrite execution with full AI cleanup:

1. Run the assessment (Step 3)
2. Add OpenRewrite to the build file and execute (same as Approach B above)
3. Run all phases from Approach A — but skip transformations already handled by OpenRewrite (check for `// TODO` markers; if code looks already transformed, skip it)
4. Proceed to validation

---

## Step 5: Validation (always runs)

After migration is complete, run all of the following:

1. **Compile**: `mvn compile` or `./gradlew compileJava` — fix all errors before continuing
2. **Check for remaining C7 references**: Search for `org.camunda.bpm` imports — each one is a missed migration
3. **Check for remaining TODOs**: Search for `// TODO` comments related to migration — each needs manual review
4. **Run tests**: `mvn test` or `./gradlew test` — fix failures, paying attention to `@JobWorker` method signatures and variable mapping
5. **Check common pitfalls**:
   - Process instance IDs changed from `String` to `Long` keys — check all ID handling
   - `VariableMap` usage — verify variable type handling
   - `HistoryService` references — history API changed significantly
   - Batch operation patterns — not directly available in C8

Present a validation summary:
```
Validation Summary
------------------
✅ Compilation: OK
⚠️  Remaining org.camunda.bpm imports: 3 (list them)
⚠️  Remaining TODOs: 5 (list them)
✅ Tests: 42 passed, 0 failed
```

For any remaining issues, ask the user how to proceed: fix now, skip, or flag for manual review.

---

## Behavior rules

- **Always load `ALL_IN_ONE.md` before touching any code.** Never guess API mappings.
- **One phase at a time.** Complete and briefly confirm each phase before starting the next. Don't silently skip phases.
- **Don't rewrite what OpenRewrite already changed.** In Approaches B and C, check before rewriting.
- **Flag BPMN files.** If `.bpmn` files use `camunda:` extension attributes, mention them — they need separate handling via the diagram converter, which is outside this skill's scope.
- **Ask before large structural changes.** If a file is High complexity or the right mapping is ambiguous, describe the options and ask.
- **Keep changes minimal.** Don't refactor, rename, or improve code beyond what's needed for the migration.
