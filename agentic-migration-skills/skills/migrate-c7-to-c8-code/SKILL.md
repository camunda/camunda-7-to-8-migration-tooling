---
name: migrate-c7-to-c8-code
description: Use this when migrating or converting a Camunda 7 / camunda-bpm Java/Spring codebase to Camunda 8 — including JavaDelegates, ExternalTaskWorkers, ProcessEngine/RuntimeService client code, execution/task listeners, and application.properties/application.yaml with camunda.* keys.
argument-hint: Optional path to project root (defaults to current directory)
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, WebFetch, AskUserQuestion
---

# Camunda 7 → 8 Code Migration

You are a migration expert helping the user migrate a Java codebase from Camunda 7 to Camunda 8.

## Step 1: Gather inputs

Before calling `AskUserQuestion`, pick a candidate project root (use the provided argument if present, otherwise the current working directory), then detect the build tool by checking that directory for `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle).

Then call `AskUserQuestion` **once** with the following questions (combine them in a single call — do not ask one at a time):

**Question 1 — Code location**

"What is the path to the project root?"

- If an argument was passed to the command, propose that path (e.g. "I'll use `/path/to/project` — is that correct?").
- Otherwise propose the current working directory.

**Question 2 — Target Camunda 8 version**

Ask which Camunda 8 version the user is migrating to:

- **8.9 or later** *(recommended)* — includes Business ID (business key successor), BPMN conditional events, global user task listeners, batch delete, History/Identity Data Migrator.
- **8.8** — first version with the unified Orchestration Cluster API, CamundaClient, and Camunda Process Test. No Business ID (use tags), no conditional events.

The target version changes which patterns apply — record the answer and use it throughout.

**Question 3 — Migration approach**

Ask the user to choose one of:

- **A. OpenRewrite + AI** *(recommended)* — Runs OpenRewrite recipes first for deterministic bulk transforms (delegates, workers, client code), then AI resolves remaining `// TODO` comments, compilation errors, config, and test code. Best for most codebases.
- **B. AI only** — AI migrates everything directly without OpenRewrite. Use this when you can't run OpenRewrite (non-Maven/Gradle builds, restricted environments) or want to review every change individually.
- **C. Assessment only** — Scan the codebase and produce a report: file inventory, complexity estimate, effort breakdown. No code changes. Use this first if you want to understand the scope before committing.

**Question 4 — Build tool** (include only when detection is ambiguous; relevant only if the user later chooses approach A)

- If exactly one build tool was detected, do not ask — state the detection in the question text of Question 3 (e.g. "Detected Maven."). Only ask if both or neither were found.
- When you do ask it, use explicit question text such as: "Which build tool should I use for the OpenRewrite step: Maven or Gradle?"

---

## Step 2: Assessment (always runs, regardless of approach)

Scan the codebase at the provided path. Identify and classify all Camunda 7 related files:

| File | Type | Complexity | Notes |
|------|------|------------|-------|
| ... | JavaDelegate / ExternalTaskWorker / Listener / ClientCode / TestCode / Config / JUEL / DMN | Low / Medium / High | Key concerns |

**Detection hints:**
- `implements JavaDelegate` → JavaDelegate
- `@ExternalTaskSubscription` or `ExternalTaskHandler` → External task worker
- `implements ExecutionListener` or `implements TaskListener` → Listener (C8: execution listeners 8.6+, user task listeners 8.8+, global user task listeners 8.9+)
- `ProcessEngine`, `RuntimeService`, `TaskService` autowired → Client code
- `HistoryService` → Client code (maps to Orchestration Cluster search endpoints; historic *data* needs the History Data Migrator)
- `DecisionService` → Client code (maps to `newEvaluateDecisionCommand`)
- `IdentityService`, `FormService` → Client code, flag for manual design (Identity Data Migrator covers authorizations since 8.9)
- `businessKey` usage → flag: maps to Business ID (8.9+) or tags (8.8)
- Batch operations (`...Async` methods, `ManagementService` batches) → Client code (Orchestration Cluster batch operations since 8.8)
- `ZeebeClient` / Spring Zeebe SDK (`io.camunda.spring:spring-boot-starter-camunda` or `zeebe-client-java`) → legacy C8 client code; migrate to `CamundaClient` / `camunda-spring-boot-starter` (ZeebeClient is removed in 8.10)
- `@Test` + Camunda 7 test rules → Test code
- `application.properties` / `application.yaml` with `camunda.*` keys → Config
- `.bpmn` files with `camunda:` namespace attributes → BPMN (flag only — convert using the online tool, see below)
- `ProcessEnginePlugin`, BPMN parse listeners → flag: global behavior; user-task cases map to global user task listeners (8.9+), others need manual design

**Special blockers to flag explicitly:**
- Listeners or delegates attached to multi-instance *bodies* that compute the collection variable — this sequencing does not exist in C8 (listeners fire after collection evaluation). Requires model change (preceding service task). High complexity.
- Custom batch handlers (`ManagementService#createBatch` with custom jobs) — no generic C8 equivalent.

After the table, present:
- Total files to migrate
- Overall complexity estimate
- Whether OpenRewrite would help (JavaDelegates or ExternalTaskWorkers present?)
- Any blockers requiring manual decision before starting
- One short note on data migration scope: running instances, history/audit data, and authorizations are **not** code migration — point to the [Data Migrator](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/) (runtime since 8.8; history and identity since 8.9, history requires RDBMS secondary storage)

**Write the assessment to `MIGRATION_REPORT.md` in the project root** (table, decisions, blockers). Keep this file updated as phases complete — it is the durable record of the migration across sessions.

Use `AskUserQuestion` to wait for user confirmation before proceeding.

---

## Step 3: Execute migration

**Git checkpoint rule (applies to A and B):** before changing anything, verify the working tree is clean (`git status`). If not, ask the user to commit or stash first. Do not create commits automatically. After OpenRewrite and after each completed phase, ask whether the user wants a commit; only commit if they explicitly approve, using a clear message (e.g. `migration: resolve delegate TODOs`). This keeps every step reviewable and reversible while preserving user control.

### Approach A — OpenRewrite + AI (recommended)

**1. Run OpenRewrite**

Before adding the plugin, resolve the latest released versions via WebFetch:

- `rewrite-maven-plugin` (OpenRewrite): `https://search.maven.org/solrsearch/select?q=g:org.openrewrite.maven+AND+a:rewrite-maven-plugin&rows=1&wt=json` → read `response.docs[0].latestVersion`
- `camunda-7-to-8-code-conversion-recipes`: `https://search.maven.org/solrsearch/select?q=g:io.camunda+AND+a:camunda-7-to-8-code-conversion-recipes&rows=1&wt=json` → read `response.docs[0].latestVersion`

Use those resolved versions in the snippets below (replacing `REWRITE_VERSION` and `RECIPES_VERSION`).

Check if the OpenRewrite plugin is already in the build file. If not, add it:

For Maven — add to `pom.xml`:
```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>REWRITE_VERSION</version>
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
      <version>RECIPES_VERSION</version>
    </dependency>
  </dependencies>
</plugin>
```

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
    id("org.openrewrite.rewrite") version "REWRITE_VERSION"
}
rewrite {
    activeRecipe("io.camunda.migration.code.recipes.AllClientRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllDelegateRecipes")
    activeRecipe("io.camunda.migration.code.recipes.AllExternalWorkerRecipes")
}
```
Run: `./gradlew rewriteRun`

Before AI cleanup, ask whether to commit the OpenRewrite result. Commit only if the user explicitly approves.

**2. AI cleanup — after OpenRewrite has run**

First, fetch the pattern catalog via WebFetch from `main` — this is your reference for resolving TODOs, config, tests, listeners, and JUEL. Do not rely on training knowledge for API mappings:
`https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md`
If context budget is tight, fetch only the individual pattern files you need from `code-conversion/patterns/` (same repo, same raw URL scheme) instead of the full catalog.

Work through each of the following. Confirm each before moving on. Do not auto-commit; ask whether to commit after each item and commit only with explicit user approval.

- Find all `// TODO` comments inserted by OpenRewrite and resolve using the pattern catalog
- Fix all compilation errors
- **Dependencies and configuration**: remove remaining `org.camunda.bpm.*` dependencies, add `camunda-process-test-spring` for tests, update `application.properties` / `application.yaml` — replace `camunda.*` keys with `camunda.client.*` equivalents (see the [properties reference](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/properties-reference/))
- **Listeners**: OpenRewrite does not cover `ExecutionListener` / `TaskListener` implementations — convert per the "Listeners" patterns (execution listener job workers; user task listeners with corrections/deny; global user task listeners for cluster-wide cases on 8.9)
- **Client code not covered by recipes**: `HistoryService` → search requests; `DecisionService` → `newEvaluateDecisionCommand`; batch `...Async` methods → batch operation endpoints (8.8+); `businessKey` → `businessId` (8.9) or tags (8.8)
- **Test code**: replace `@Rule` Camunda test rules with `@CamundaSpringProcessTest`, update assertions (e.g. `isWaitingAt("id")` → `hasActiveElements("id")`), message correlation, timer handling (`processTestContext.increaseTime(...)`), user task completion (`processTestContext.completeUserTask(...)`), worker mocking (`processTestContext.mockJobWorker(...)`) — OpenRewrite doesn't fully cover tests. For large suites on 8.9+, recommend CPT *shared runtime* mode to cut execution time.
- **JUEL expressions**: triage per the "Expression → Job Worker" pattern — pure data expressions convert to FEEL (Diagram Converter automates this), conditional events are supported natively since 8.9, only bean-invoking expressions need a JUEL job worker or a refactor into regular job workers

---

### Approach B — AI only

First, fetch the pattern catalog via WebFetch from `main` — this is your primary reference for all C7→C8 transformations. Do not rely on training knowledge for API mappings:
`https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md`
If context budget is tight, fetch only the individual pattern files you need from `code-conversion/patterns/` instead of the full catalog.

Work through each phase sequentially. Confirm completion of each phase before moving to the next. Do not auto-commit; ask whether to commit after each phase and commit only with explicit user approval.

**Phase 1: Dependencies and configuration**

Before modifying the build file, resolve the latest released Camunda version via WebFetch:
- `https://search.maven.org/solrsearch/select?q=g:io.camunda+AND+a:camunda-spring-boot-starter&rows=1&wt=json` → read `response.docs[0].latestVersion` as `CAMUNDA_VERSION`

If the user's target is 8.8, use the latest 8.8.x patch (`8.8.x`); if 8.9+, `CAMUNDA_VERSION` is fine as-is.

Detect the Spring Boot version from the existing `pom.xml` or `build.gradle`:
- Spring Boot 3.x → use `io.camunda:camunda-spring-boot-3-starter` (supported for 8.9+; use for 8.8 too)
- Spring Boot 4.x → use `io.camunda:camunda-spring-boot-starter`

Add the Camunda public repository if not already present (some Camunda artifacts may not be on Maven Central):
```xml
<repository>
  <id>camunda-public</id>
  <name>Camunda Public Repository</name>
  <url>https://artifacts.camunda.com/artifactory/public/</url>
</repository>
```

Then:
- Remove all `org.camunda.bpm.*` dependencies and `camunda-bom` from `pom.xml` / `build.gradle`
- Remove embedded-engine dependencies (H2, JDBC starter) — no longer needed without the embedded engine
- Add the resolved starter at the resolved version; add `io.camunda:camunda-process-test-spring` at the same version in test scope if tests exist
- Also add `org.springframework.boot:spring-boot-starter` if not already transitively available (needed for `jakarta.annotation` and common Spring Boot auto-config)
- Replace `@EnableProcessApplication` with `@Deployment`
- Update `application.properties` / `application.yaml` — replace `camunda.*` keys with `camunda.client.*` equivalents (see the [properties reference](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/properties-reference/))
- Reference: "Maven dependency and configuration" section in patterns

**Phase 2: Client code**
- Replace `ProcessEngine` autowiring with `CamundaClient`
- Update all service method calls (RuntimeService, TaskService, HistoryService, DecisionService, etc.)
- Key changes: starting instances (incl. `businessId`/tags), correlating messages, broadcasting signals, cancelling instances, user task handling, variable handling, history queries → search requests, DMN evaluation, batch operations
- Reference: "Client code → ProcessEngine" patterns (incl. "Business Key", "Batch Operations", "Evaluate Decisions", "Query History")

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

**Phase 5: Listeners**
- Convert `ExecutionListener` implementations to execution listener job workers (`zeebe:executionListener` + `@JobWorker`)
- Convert `TaskListener` implementations to user task listener job workers (job result with corrections / deny)
- For listeners registered globally (engine plugins, parse listeners) on user tasks: use global user task listeners (8.9+, configuration-based)
- Flag multi-instance body listeners that prepare collections — requires model change
- Reference: "Glue code → Listeners" patterns

**Phase 6: Test code**
- Replace `@Rule` Camunda test rules with `@CamundaSpringProcessTest`
- Update process instance startup patterns
- Replace assertion methods: e.g. `isWaitingAt("id")` → `hasActiveElements("id")`
- Update message correlation, timer handling, user task completion in tests (use `processTestContext` utilities: `completeUserTask`, `completeJob`, `mockJobWorker`, `increaseTime`)
- Disable real workers where mocks are used: `camunda.client.worker.defaults.enabled=false` with per-worker overrides
- On 8.9+, recommend CPT shared runtime mode for large suites
- Reference: "Test assertions" patterns

---

### Approach C — Assessment only

Present the assessment table from Step 2 with additional detail:
- Per-file effort estimate (hours)
- Total estimated effort
- Which files OpenRewrite can handle automatically vs. require manual AI work
- Recommended approach (A or B) based on codebase size and complexity
- Known risks or blockers (incl. multi-instance listener pattern, custom batches, IdentityService/FormService usage)
- Data migration scope note (Data Migrator: runtime / history / identity)

Write the full report to `MIGRATION_REPORT.md`. Then stop — make no code changes.

---

## Step 4: Validation (always runs)

1. **Compile**: `mvn compile` or `./gradlew compileJava` — fix all errors
2. **Check for remaining C7 references**: Search for `org.camunda.bpm` imports — each is a missed migration
3. **Check for remaining TODOs**: Search for `// TODO` migration comments — each needs manual review
4. **Check for legacy C8 client**: Search for `ZeebeClient` and `zeebe-client-java` — deprecated, removed in 8.10; migrate to `CamundaClient`
5. **Check for leftover business keys**: Search for `businessKey` — map to `businessId` (8.9+) or tags (8.8), don't silently drop
6. **Run tests**: `mvn test` or `./gradlew test` — fix failures
7. **Check common pitfalls**:
  - **Critical naming swap**: C7 `processDefinitionKey` (the string key like `"my-process"`) becomes C8 `bpmnProcessId`; C7 `processDefinitionId` (the UUID) becomes C8 `processDefinitionKey` — easy to miss, causes silent runtime bugs. Same swap applies to decision definitions.
  - Process instance IDs changed from `String` to `Long` — check all ID handling
  - `VariableMap` usage — variables are now plain JSON, `TypedValue` API is gone
  - `HistoryService` references — map to Orchestration Cluster search endpoints (eventually consistent — no read-after-write inside workers); historic *data* needs the History Data Migrator (8.9, RDBMS)
  - Batch operations — available since 8.8 via the Orchestration Cluster API (cancel/resolve/migrate/modify; delete since 8.9); only *custom* batch handlers need manual design

Present a summary:
```
Validation Summary
------------------
✅ Compilation: OK
⚠️  Remaining org.camunda.bpm imports: 3 → [list files]
⚠️  Remaining TODOs: 5 → [list them]
⚠️  Remaining businessKey usages: 2 → [list them]
✅ Tests: 42 passed, 0 failed
```

Record the summary in `MIGRATION_REPORT.md`. For any remaining issues, ask the user: fix now, skip, or flag for manual review.

---

## Behavior rules

- **Always load the pattern catalog before touching any code.** Never guess API mappings. For API details not covered by the catalog, prefer the official Camunda docs (`docs.camunda.io`) via `WebFetch` over training knowledge.
- **Respect the target version.** Do not recommend 8.9 features (businessId, conditional events, global user task listeners, batch delete) to an 8.8 target, and do not send 8.9 targets down 8.8 workarounds.
- **One phase at a time; commits are opt-in.** Confirm each phase before starting the next. Never start on a dirty working tree. Ask before each commit and proceed only when the user explicitly allows it.
- **Keep `MIGRATION_REPORT.md` current.** Assessment, decisions, phase status, validation results.
- **Don't rewrite what OpenRewrite already changed.** In Approach A, check for existing transforms before rewriting.
- **Flag BPMN files.** If `.bpmn` files use `camunda:` attributes, mention them in the assessment summary and recommend the user convert them at **https://diagram-converter.camunda.io/** — it handles namespace updates, listener mappings, simple JUEL→FEEL conversions, and (since 8.9) conditional events. Suggest running it after the code migration.
- **Ask before High complexity files.** Describe the options and confirm before proceeding.
- **Keep changes minimal.** Don't refactor, rename, or improve code beyond what the migration requires.
- **Consult on edge cases, don't auto-fix.** Auto-apply changes only when the pattern catalog gives an unambiguous 1:1 mapping. For anything else — JUEL expressions invoking beans, BPMN error mapping, async/correlation, IdentityService/FormService usage, custom batches, multi-instance listener patterns, ambiguous TODOs from OpenRewrite, or compile errors whose fix isn't a direct catalog match — propose the change via `AskUserQuestion` before applying. When unsure whether a case is unambiguous, ask.
