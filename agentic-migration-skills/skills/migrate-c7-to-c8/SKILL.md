---
name: migrate-c7-to-c8
description: Migrate a Camunda 7 Java codebase to Camunda 8 using AI, OpenRewrite, or both
argument-hint: Optional path to project root (defaults to current directory)
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, WebFetch, AskUserQuestion
---

# Camunda 7 → 8 Code Migration

You are a migration expert helping the user migrate a Java codebase from Camunda 7 to Camunda 8.

## Step 1: Gather inputs

Use `AskUserQuestion` for each question below. Do NOT ask the next question until you have received the answer to the current one.

**Question 1 — Code location**

Call `AskUserQuestion`: "What is the path to the project root?"

- If an argument was passed to the command, propose that path in the question (e.g. "I'll use `/path/to/project` — is that correct?").
- Otherwise propose the current working directory.

Do not proceed to Question 2 until you have the answer.

**Question 2 — Migration approach**

Call `AskUserQuestion` asking the user to choose one of:

- **A. OpenRewrite + AI** *(recommended)* — Runs OpenRewrite recipes first for deterministic bulk transforms (delegates, workers, client code), then AI resolves remaining `// TODO` comments, compilation errors, config, and test code. Best for most codebases.
- **B. AI only** — AI migrates everything directly without OpenRewrite. Use this when you can't run OpenRewrite (non-Maven/Gradle builds, restricted environments) or want to review every change individually.
- **C. Assessment only** — Scan the codebase and produce a report: file inventory, complexity estimate, effort breakdown. No code changes. Use this first if you want to understand the scope before committing.

Do not proceed to Question 3 until you have the answer.

**Question 3 — Build tool** (only if approach A was chosen)

Before asking, check the project root for `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle).

- If exactly one is found, call `AskUserQuestion` to confirm: e.g. "I detected Maven (`pom.xml`) — is that correct?"
- If both or neither are found, call `AskUserQuestion`: "Are you using Maven or Gradle?"

Do not proceed until you have the answer.

---

## Step 2: Assessment (always runs, regardless of approach)

Scan the codebase at the provided path. Identify and classify all Camunda 7 related files:

| File | Type | Complexity | Notes |
|------|------|------------|-------|
| ... | JavaDelegate / ExternalTaskWorker / ClientCode / TestCode / Config / JUEL | Low / Medium / High | Key concerns |

**Detection hints:**
- `implements JavaDelegate` → JavaDelegate
- `@ExternalTaskSubscription` or `ExternalTaskHandler` → External task worker
- `ProcessEngine`, `RuntimeService`, `TaskService` autowired → Client code
- `@Test` + Camunda 7 test rules → Test code
- `application.properties` / `application.yaml` with `camunda.*` keys → Config
- `.bpmn` files with `camunda:` namespace attributes → BPMN (flag only — convert using the online tool, see below)

After the table, present:
- Total files to migrate
- Overall complexity estimate
- Whether OpenRewrite would help (JavaDelegates or ExternalTaskWorkers present?)
- Any blockers requiring manual decision before starting

Wait for user confirmation before proceeding.

---

## Step 4: Execute migration

### Approach A — OpenRewrite + AI (recommended)

**1. Run OpenRewrite**

Check if the OpenRewrite plugin is already in the build file. If not, add it:

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
    <skipMavenParsing>false</skipMavenParsing>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>io.camunda</groupId>
      <artifactId>camunda-7-to-8-code-conversion-recipes</artifactId>
      <version>LATEST_RELEASE</version>
    </dependency>
  </dependencies>
</plugin>
```
Use the latest released version of `camunda-7-to-8-code-conversion-recipes` here (or align with the version used by your repository/examples).

**Before running**, check for Spotless + Java version incompatibility and fix proactively:

1. Detect Java major version: `java -version 2>&1 | head -1`
2. Check if Spotless is configured: `grep -r "spotless" pom.xml build.gradle build.gradle.kts 2>/dev/null`
3. If Spotless is present **and** Java major version ≥ 17:
   - Run with the JVM flags Spotless needs on Java 17+:
     ```
     MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED \
       --add-opens=java.base/java.util=ALL-UNNAMED \
       --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
       --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
       --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
       --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
       --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" \
     mvn rewrite:run
     ```
   - If this still fails with a Spotless error, ask the user: "Spotless is incompatible with your Java version. Would you like to skip it for now (`mvn rewrite:run -Dspotless.skip=true`) or switch to Java 11/17 first?"
4. If Spotless is not present, or Java < 17: run `mvn rewrite:run` directly.

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
Run: `./gradlew rewriteRun`

**2. AI cleanup — after OpenRewrite has run**

First, fetch the pattern catalog via WebFetch — this is your reference for resolving TODOs, config, tests, and JUEL. Do not rely on training knowledge for API mappings:
```
https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md
```

Work through each of the following. Confirm each before moving on.

- Find all `// TODO` comments inserted by OpenRewrite and resolve using the pattern catalog
- Fix all compilation errors
- **Dependencies and configuration**: remove remaining `org.camunda.bpm.*` dependencies, add `camunda-process-test-spring` for tests, update `application.properties` / `application.yaml` — replace `camunda.*` keys with `camunda.client.*` equivalents
- **Test code**: replace `@Rule` Camunda test rules with `@CamundaSpringProcessTest`, update assertions (e.g. `isWaitingAt("id")` → `hasActiveElements("id")`), message correlation, timer handling — OpenRewrite doesn't fully cover tests
- **JUEL expressions**: OpenRewrite does not handle JUEL — each expression needs a custom job worker implementation

---

### Approach B — AI only

First, fetch the pattern catalog via WebFetch — this is your primary reference for all C7→C8 transformations. Do not rely on training knowledge for API mappings:
```
https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md
```

Work through each phase sequentially. Confirm completion of each phase before moving to the next.

**Phase 1: Dependencies and configuration**
- Remove all `org.camunda.bpm.*` dependencies from `pom.xml` / `build.gradle`
- Add `io.camunda:camunda-spring-boot-starter` and `camunda-process-test-spring`
- Replace `@EnableProcessApplication` with `@Deployment`
- Update `application.properties` / `application.yaml` — replace `camunda.*` keys with `camunda.client.*` equivalents
- Reference: "Maven dependency and configuration" section in patterns

**Phase 2: Client code**
- Replace `ProcessEngine` autowiring with `CamundaClient`
- Update all service method calls (RuntimeService, TaskService, etc.)
- Key changes: starting instances, correlating messages, broadcasting signals, cancelling instances, user task completion, variable handling
- Reference: "Client code → ProcessEngine" patterns

**Phase 3: JavaDelegate → Job Worker**
- Remove `implements JavaDelegate`
- Convert `execute(DelegateExecution execution)` to `@JobWorker`-annotated method
- Update variable access: `execution.getVariable()` → method parameters or `@Variable` annotations
- Map BPMN errors: `BpmnError` → `CamundaError.bpmnError(...)`
- Remove all `TypedValue` API usage
- Reference: "Glue code → JavaDelegate → Job Worker" patterns

**Phase 4: External task workers**
- Replace `@ExternalTaskSubscription` with `@JobWorker`
- Update variable access and failure/incident handling
- Reference: "Glue code → External Task Worker" patterns

**Phase 5: Test code**
- Replace `@Rule` Camunda test rules with `@CamundaSpringProcessTest`
- Update process instance startup patterns
- Replace assertion methods: e.g. `isWaitingAt("id")` → `hasActiveElements("id")`
- Update message correlation, timer handling, user task completion in tests
- Reference: "Test assertions" patterns

---

### Approach C — Assessment only

Present the assessment table from Step 3 with additional detail:
- Per-file effort estimate (hours)
- Total estimated effort
- Which files OpenRewrite can handle automatically vs. require manual AI work
- Recommended approach (A or B) based on codebase size and complexity
- Known risks or blockers

Then stop — make no code changes.

---

## Step 5: Validation (always runs)

1. **Compile**: `mvn compile` or `./gradlew compileJava` — fix all errors
2. **Check for remaining C7 references**: Search for `org.camunda.bpm` imports — each is a missed migration
3. **Check for remaining TODOs**: Search for `// TODO` migration comments — each needs manual review
4. **Run tests**: `mvn test` or `./gradlew test` — fix failures
5. **Check common pitfalls**:
   - **Critical naming swap**: C7 `processDefinitionKey` (the string key like `"my-process"`) becomes C8 `bpmnProcessId`; C7 `processDefinitionId` (the UUID) becomes C8 `processDefinitionKey` — easy to miss, causes silent runtime bugs
   - Process instance IDs changed from `String` to `Long` — check all ID handling
   - `VariableMap` usage — variables are now plain JSON, `TypedValue` API is gone
   - `HistoryService` references — history API changed significantly in C8
   - Batch operations — not directly available in C8, flag for manual design

Present a summary:
```
Validation Summary
------------------
✅ Compilation: OK
⚠️  Remaining org.camunda.bpm imports: 3 → [list files]
⚠️  Remaining TODOs: 5 → [list them]
✅ Tests: 42 passed, 0 failed
```

For any remaining issues, ask the user: fix now, skip, or flag for manual review.

---

## Behavior rules

- **Always load `ALL_IN_ONE.md` before touching any code.** Never guess API mappings.
- **One phase at a time.** Confirm each phase before starting the next.
- **Don't rewrite what OpenRewrite already changed.** In Approach A, check for existing transforms before rewriting.
- **Flag BPMN files.** If `.bpmn` files use `camunda:` attributes, mention them in the assessment summary and recommend the user convert them at **https://diagram-converter.camunda.io/** — it handles namespace updates and some JUEL→FEEL conversions automatically. Suggest running it after the code migration.
- **Ask before High complexity files.** Describe the options and confirm before proceeding.
- **Keep changes minimal.** Don't refactor, rename, or improve code beyond what the migration requires.
