---
name: migrate-c7-to-c8-code
description: Use this when migrating or converting a Camunda 7 / camunda-bpm project to Camunda 8 — both Java/Spring code (JavaDelegates, ExternalTaskWorkers, ProcessEngine/RuntimeService client code, execution/task listeners, application.properties/application.yaml with camunda.* keys) and BPMN/DMN models (diagrams with the camunda: namespace). Handles code migration, model migration, or both.
argument-hint: Optional path to project root (defaults to current directory)
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, WebFetch, AskUserQuestion
---

# Camunda 7 → 8 Migration

You are a migration expert helping the user migrate a Camunda 7 project to Camunda 8. A project can contain **two independent kinds of assets**, each with its own migration path:

- **Code** — Java/Spring glue and client code, config, tests. Migrated with **OpenRewrite recipes** (deterministic) plus AI cleanup.
- **Models** — BPMN/DMN diagrams using the `camunda:` namespace. Migrated with the **Diagram Converter** (deterministic) or agentically.

Treat these as separate operations that compose. Do not conflate "migrate my code" with "migrate my diagrams" — ask what the user wants (Step 1, Question 3) and run only the relevant path(s).

## Step 1: Gather inputs

Before calling `AskUserQuestion`, pick a candidate project root (use the provided argument if present, otherwise the current working directory), then detect the build tool by checking that directory for `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle). Also glob for models (`**/*.bpmn`, `**/*.bpmn20.xml`, `**/*.dmn`, `**/*.dmn11.xml`) so you can tailor the scope question to what is actually present.

Then call `AskUserQuestion` **once** with Questions 1-3 below (combine them in a single call — do not ask one at a time):

**Question 1 — Project location**

"What is the path to the project root?"

- If an argument was passed to the command, propose that path (e.g. "I'll use `/path/to/project` — is that correct?").
- Otherwise propose the current working directory.

**Question 2 — Target Camunda 8 version**

Ask which Camunda 8 version the user is migrating to:

- **8.9 or later** *(recommended)* — includes Business ID (business key successor), BPMN conditional events, global user task listeners, batch delete, History/Identity Data Migrator.
- **8.8** — first version with the unified Orchestration Cluster API, CamundaClient, and Camunda Process Test. No Business ID (use tags), no conditional events.

The target version changes which patterns apply **and** is passed to the Diagram Converter as `--platform-version` (valid values `8.0`–`8.10`). Record the concrete `major.minor` (e.g. `8.9`, `8.10`); if the user says "8.9 or later" without a specific minor, confirm the exact minor to target (default to the latest, `8.10`). Use the answer throughout.

**Question 3 — Migration scope**

Ask what the user wants to migrate (tailor the wording to what you detected — code files, model files, or both):

- **Code only** — Java/Spring code. Runs Part A.
- **Models only** — BPMN/DMN diagrams. Runs Part B.
- **Code + models** *(recommended when both are present)* — Runs both Part A and Part B and composes them.
- **Assessment only** — Scan and report scope/complexity/effort for code and models. No changes.

**Follow-up questions (second `AskUserQuestion` call, after Question 3 is answered)**

Ask only the follow-ups that apply to the chosen scope. Combine them into a single call where possible.

*If scope includes code* — **Code migration approach**:

- **A. OpenRewrite + AI** *(recommended)* — Runs OpenRewrite recipes first for deterministic bulk transforms (delegates, workers, client code), then AI resolves remaining `// TODO` comments, compilation errors, config, and test code. Best for most codebases.
- **B. AI only** — AI migrates everything directly without OpenRewrite. Use this when you can't run OpenRewrite (non-Maven/Gradle builds, restricted environments) or want to review every change individually.
- **C. Assessment only** — Scan the codebase and produce a report: file inventory, complexity estimate, effort breakdown. No code changes.

*If scope includes models* — **Model migration approach**:

- **M1. Diagram Converter CLI (deterministic)** *(recommended)* — Downloads the official `camunda-7-to-8-diagram-converter-cli` from GitHub releases into the project and runs it locally against your BPMN/DMN files, targeting your Camunda 8 version. Deterministic and repeatable; produces converted files plus analysis reports (CSV/XLSX). **Requires Java 21+.** This is the diagram equivalent of "OpenRewrite for code".
- **M2. Agentic AI** — AI rewrites the BPMN/DMN XML directly (namespace, listeners, JUEL→FEEL, event mappings). Use when Java 21 is unavailable, you want to review every change, or a niche case the CLI doesn't cover. Slower and non-deterministic.
- **M3. Online Diagram Converter (hosted)** — You upload your diagrams to **https://diagram-converter.camunda.io/** and download the converted results yourself. No local Java needed, but **files leave your machine** — do not use for confidential models. Use this to opt out of the local CLI entirely.
- Any of the above can be run in **analyze-only** mode first (see "Analyze-only mode" in Part B) to see findings without producing converted files.

*If scope includes code and approach is A (OpenRewrite + AI)* — **Build tool** (only if detection was ambiguous — both Maven and Gradle found, or neither): "Which build tool should I use for the OpenRewrite step: Maven or Gradle?" If exactly one build tool was detected, do not ask; state the detection in the approach question text instead (e.g. "Detected Maven."). Do not proceed until you have the answer.

---

## Step 2: Assessment (always runs, regardless of scope)

Scan the project at the provided path. Produce two inventories as relevant to the chosen scope.

### Code inventory

Identify and classify all Camunda 7 related Java/config files:

| File | Type | Complexity | Notes |
|------|------|------------|-------|
| ... | JavaDelegate / ExternalTaskWorker / Listener / ClientCode / TestCode / Config / JUEL | Low / Medium / High | Key concerns |

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
- `ProcessEnginePlugin`, BPMN parse listeners → flag: global behavior; user-task cases map to global user task listeners (8.9+), others need manual design

**Special blockers to flag explicitly:**
- Listeners or delegates attached to multi-instance *bodies* that compute the collection variable — this sequencing does not exist in C8 (listeners fire after collection evaluation). Requires model change (preceding service task). High complexity.
- Custom batch handlers (`ManagementService#createBatch` with custom jobs) — no generic C8 equivalent.

### Model inventory

Glob for `**/*.bpmn`, `**/*.bpmn20.xml`, `**/*.dmn`, `**/*.dmn11.xml`. For each, note whether it uses the `camunda:` namespace (needs conversion) and record its path:

| File | Type | Uses `camunda:` ns | Notes |
|------|------|--------------------|-------|
| ... | BPMN / DMN | yes / no | e.g. JavaDelegate refs, JUEL expressions, listeners |

These are migrated in **Part B** (not by OpenRewrite). Do not attempt to hand-edit them here — that is the Diagram Converter's job.

### Summary

After the tables, present:
- Total code files and total model files to migrate
- Overall complexity estimate
- Whether OpenRewrite would help (JavaDelegates or ExternalTaskWorkers present?)
- Any blockers requiring manual decision before starting
- One short note on data migration scope: running instances, history/audit data, and authorizations are **not** code or model migration — point to the [Data Migrator](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/) (runtime since 8.8; history and identity since 8.9, history requires RDBMS secondary storage)

**Write the assessment to `MIGRATION_REPORT.md` in the project root** (both tables, decisions, blockers). Keep this file updated as phases complete — it is the durable record of the migration across sessions.

Use `AskUserQuestion` to wait for user confirmation before proceeding.

---

## Step 3: Execute migration

**Git checkpoint rule (applies to all approaches):** before changing anything, verify the working tree is clean (`git status`). If not, ask the user to commit or stash first. Do not create commits automatically. After OpenRewrite, after the Diagram Converter run, and after each completed phase, ask whether the user wants a commit; only commit if they explicitly approve, using a clear message (e.g. `migration: resolve delegate TODOs`). This keeps every step reviewable and reversible while preserving user control.

Run **Part A** if the scope includes code, **Part B** if it includes models. For **Code + models**, see "Composing code + model migration" at the end of this step.

---

## Part A — Code migration (Java/Spring)

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
- **JUEL expressions**: triage per the "Expression → Job Worker" pattern — pure data expressions convert to FEEL (the Diagram Converter automates this on the model side, see Part B), conditional events are supported natively since 8.9, only bean-invoking expressions need a JUEL job worker or a refactor into regular job workers

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

- **Maven (`pom.xml`)**:
    <repository>
      <id>camunda-public</id>
      <name>Camunda Public Repository</name>
      <url>https://artifacts.camunda.com/artifactory/public/</url>
    </repository>

- **Gradle (`build.gradle` / `build.gradle.kts`)**:
    repositories {
      maven { url "https://artifacts.camunda.com/artifactory/public/" }
    }

Then:
- Remove all `org.camunda.bpm.*` dependencies and `camunda-bom` from `pom.xml` / `build.gradle`
- Remove embedded-engine dependencies (H2, JDBC starter) — no longer needed without the embedded engine
- Add the resolved starter at the resolved version; add `io.camunda:camunda-process-test-spring` at the same version in test scope if tests exist
- Ensure Spring Boot dependency management is correctly configured (e.g., `spring-boot-starter-parent` or the Spring Boot BOM); avoid adding `org.springframework.boot:spring-boot-starter` solely to “get jakarta.annotation”.
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

Present the code assessment table from Step 2 with additional detail:
- Per-file effort estimate (hours)
- Total estimated effort
- Which files OpenRewrite can handle automatically vs. require manual AI work
- Recommended approach (A or B) based on codebase size and complexity
- Known risks or blockers (incl. multi-instance listener pattern, custom batches, IdentityService/FormService usage)
- Data migration scope note (Data Migrator: runtime / history / identity)

Write the full report to `MIGRATION_REPORT.md`. Then stop — make no code changes.

---

## Part B — Model migration (BPMN/DMN)

Model migration converts BPMN/DMN diagrams from the Camunda 7 (`camunda:`) namespace to the Camunda 8 (`zeebe:`) namespace: job types for delegates, listener mappings, simple JUEL→FEEL, event definitions, version-gated features. This is the model-side analogue of OpenRewrite: prefer the **deterministic** Diagram Converter (M1) over agentic rewriting (M2).

**Conversion is not full migration.** The converter emits findings at severities **WARNING**, **TASK**, **REVIEW**, and **INFO**. TASK/REVIEW/WARNING items still need human follow-up, and JUEL conversion is only partial. Always tell the user this after a run.

### Approach M1 — Diagram Converter CLI (deterministic, recommended)

**1. Java 21+ prerequisite — fail fast**

```bash
java -version   # read the major version
```

If `java` is missing or the major version is **< 21**, STOP the CLI path and explain clearly, e.g.:

> The Diagram Converter CLI requires Java 21+. Detected: `<version or "not found">`. Install Java 21+ and re-run, or choose **M2 (agentic AI)** which needs no Java, or **M3 (online converter)**.

Do not silently skip model migration — surface the blocker and offer the alternatives.

**2. Resolve the latest release and download the CLI into the project**

The CLI is published as a self-contained executable JAR named `camunda-7-to-8-diagram-converter-cli-<version>.jar` on the repo's GitHub releases. Resolve the latest release tag, then download the matching asset into a project-local directory (reuse it if already present — don't re-download):

```bash
REPO=camunda/camunda-7-to-8-migration-tooling
DEST_DIR=.camunda-migration
mkdir -p "$DEST_DIR"

# Preferred: GitHub CLI resolves the latest release tag
TAG=$(gh release view --repo "$REPO" --json tagName -q .tagName)
# Fallback without gh:
#   TAG=$(curl -fsSL https://api.github.com/repos/$REPO/releases/latest | python3 -c 'import sys,json;print(json.load(sys.stdin)["tag_name"])')

JAR="$DEST_DIR/camunda-7-to-8-diagram-converter-cli-${TAG}.jar"
if [ ! -f "$JAR" ]; then
  curl -fL -o "$JAR" \
    "https://github.com/$REPO/releases/download/${TAG}/camunda-7-to-8-diagram-converter-cli-${TAG}.jar"
fi
```

The JAR is large (~30 MB). If the project is a git repo, add `.camunda-migration/` to `.gitignore` — do not commit the downloaded tool.

**3. Run the converter in local mode, targeting the user's C8 version**

The CLI's `local` subcommand accepts a single file **or** a directory (recursive by default). Always pass `--platform-version` set to the version from Step 1 Question 2 so version-gated conversions (e.g. conditional events on 8.9+) are applied correctly.

```bash
# TARGET_MINOR is the version from Step 1, e.g. 8.9 or 8.10
java -Dfile.encoding=UTF-8 -jar "$JAR" local <FILE_OR_DIR> \
  --platform-version <TARGET_MINOR> \
  --csv \
  --xlsx
# Optional flags to add above:
#   --csv / --xlsx             write analysis reports (already shown)
#   -o / --override            overwrite pre-existing converted files
#   --check                    analyze-only (no converted diagrams exported) — see "Analyze-only mode"
#   -nr / --not-recursive      disable recursive search when a directory is given
```

Useful options:
- `--platform-version <v>` — target C8 version (`8.0`–`8.10`); defaults to latest if omitted. **Always set it.**
- `--prefix <str>` — prefix for generated filenames (default `converted-c8-`). The converter writes a **new file next to the source**, e.g. `converted-c8-order-process.bpmn`, so originals are never mutated in place.
- `-o` / `--override` — overwrite an existing converted file (otherwise it writes `... (1).bpmn` to avoid clobbering).
- `--csv`, `--xlsx`, `--md` — write an analysis report (`analysis-results.csv` / `.xlsx` / `.md`) in the target directory.
- `--check` — analyze only; no converted diagrams are exported.

**4. Surface the outputs to the user**

After the run, report back:
- **Converted files** — list every `converted-c8-*.bpmn` / `*.dmn` produced and where.
- **Analysis findings** — summarize from the CLI stdout and/or the CSV/XLSX report, grouped by severity (WARNING / TASK / REVIEW / INFO) with counts and the top items.
- **Analysis artifacts** — if you generated `--csv` / `--xlsx`, point the user to them for review.

**5. Explain that conversion ≠ done, then offer to help with findings**

Tell the user plainly that REVIEW/WARNING/TASK findings remain and JUEL conversion is partial — these need follow-up. Then **offer to take care of them agentically**: for findings with an unambiguous fix, propose editing the converted BPMN/DMN (e.g. filling a job type, adjusting a FEEL expression), apply with `Edit`, and then **ask the human to review** the result. For ambiguous findings, consult via `AskUserQuestion` before changing anything (see the behavior rule on edge cases). Never overwrite the user's original diagrams — work on the `converted-c8-*` copies.

### Approach M2 — Agentic AI (direct XML rewrite)

Use when Java 21 is unavailable, the user wants to review every change, or the CLI can't handle a case. Fetch the current diagram-conversion guidance rather than relying on training knowledge:
`https://raw.githubusercontent.com/camunda/camunda-docs/main/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter.md`

Then, for each in-scope diagram, produce a **new** `converted-c8-<name>.bpmn`/`.dmn` (never edit the original in place) applying, respecting the target version:
- `camunda:` namespace/extension elements → `zeebe:` equivalents (task definitions/job types, IO mappings, headers)
- Execution/task listeners → `zeebe:executionListeners` / user task listeners
- JavaDelegate/expression references → job types (or blank, to be filled)
- Simple JUEL → FEEL for pure data expressions; flag bean-invoking expressions for manual work
- Conditional events natively only on 8.9+; otherwise flag
- DMN: update decision/definition namespaces and expression language as needed

Emit a findings summary mirroring the CLI severities (WARNING/TASK/REVIEW/INFO) and ask for human review. This path is slower and non-deterministic — recommend M1 whenever Java 21 is available.

### Approach M3 — Online Diagram Converter (hosted, opt out)

If the user prefers not to run the local CLI, point them to the hosted converter:

> Upload your BPMN/DMN files at **https://diagram-converter.camunda.io/**, set the target version there, and download the converted results. Note: your diagrams leave your machine and are processed by the hosted service — don't use this for confidential models.

This ticket does not automate the hosted service. Once the user brings the converted files back into the project, you can offer the same agentic findings follow-up as in M1 step 5.

### Analyze-only mode

For "analyze but don't convert": run M1 with `--check` (optionally `--csv`/`--xlsx`) to produce findings and reports with no converted files, or do an M2 read-only pass. Present the findings grouped by severity and stop — make no diagram changes.

---

## Composing code + model migration

When the scope is **Code + models**:
- The two paths are independent — run each with its chosen approach. Order doesn't strictly matter; a reasonable default is **models first** (diagrams define the job types/listeners the code must implement), then code, but follow the user's preference.
- Keep both inventories and both sets of results in `MIGRATION_REPORT.md`.
- After both complete, cross-check: job types emitted by the Diagram Converter should match the `@JobWorker(type = ...)` values produced by the code migration. Flag mismatches for the user.

---

## Step 4: Validation (always runs)

### Code validation (if code was migrated)

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

### Model validation (if models were migrated)

1. **Converted files present**: confirm a `converted-c8-*` file exists for each in-scope diagram (unless analyze-only).
2. **Findings triaged**: every WARNING/TASK/REVIEW finding is either fixed (with human review) or explicitly recorded as remaining follow-up in `MIGRATION_REPORT.md`.
3. **Originals intact**: the user's original diagrams were not overwritten.

Present a summary:
```
Validation Summary
------------------
✅ Compilation: OK
⚠️  Remaining org.camunda.bpm imports: 3 → [list files]
⚠️  Remaining TODOs: 5 → [list them]
⚠️  Remaining businessKey usages: 2 → [list them]
✅ Tests: 42 passed, 0 failed
✅ Models converted: 4 → [list converted-c8-* files]
⚠️  Model findings needing follow-up: 6 (2 REVIEW, 3 TASK, 1 WARNING) → [see analysis report]
```

Record the summary in `MIGRATION_REPORT.md`. For any remaining issues, ask the user: fix now, skip, or flag for manual review.

---

## Behavior rules

- **Distinguish code from models.** Run OpenRewrite/AI on code and the Diagram Converter on models — never hand-edit BPMN/DMN as part of the code flow. Ask for scope (Step 1 Q3) rather than assuming.
- **Always load the reference before touching anything.** For code, load the pattern catalog; for agentic model work, load the diagram-converter docs. Never guess API or XML mappings. For details not covered, prefer official Camunda docs (`docs.camunda.io`) via `WebFetch` over training knowledge.
- **Respect the target version.** Do not recommend 8.9 features (businessId, conditional events, global user task listeners, batch delete) to an 8.8 target, and do not send 8.9 targets down 8.8 workarounds. Pass the same version to the Diagram Converter via `--platform-version`.
- **Prefer deterministic over agentic.** For code, prefer OpenRewrite + AI over AI-only. For models, prefer the Diagram Converter CLI (M1) over agentic rewriting (M2). Use the agentic/manual path only when the deterministic one can't run.
- **Diagram Converter: fail fast on Java, download don't ask.** Require Java 21+ and stop with a clear message (offer M2/M3) if it's missing. Resolve and download the CLI automatically from the latest GitHub release into `.camunda-migration/`; reuse an existing download; never commit the JAR.
- **Never mutate user assets silently.** The converter writes `converted-c8-*` copies — keep originals intact. Treat converted files and CSV/XLSX/markdown as generated outputs returned to the user for inspection.
- **Conversion is not completion.** Always explain that REVIEW/WARNING/TASK findings and partial JUEL conversion remain, then offer to fix the unambiguous ones agentically and ask the human to review.
- **One phase at a time; commits are opt-in.** Confirm each phase before starting the next. Never start on a dirty working tree. Ask before each commit and proceed only when the user explicitly allows it.
- **Keep `MIGRATION_REPORT.md` current.** Both inventories, decisions, phase status, validation results.
- **Don't rewrite what the tools already changed.** In Approach A, check for existing transforms before rewriting. After a Diagram Converter run, don't re-do conversions it already applied.
- **Ask before High complexity files.** Describe the options and confirm before proceeding.
- **Keep changes minimal.** Don't refactor, rename, or improve beyond what the migration requires.
- **Consult on edge cases, don't auto-fix.** Auto-apply changes only when the reference gives an unambiguous 1:1 mapping. For anything else — JUEL expressions invoking beans, BPMN error mapping, async/correlation, IdentityService/FormService usage, custom batches, multi-instance listener patterns, ambiguous TODOs from OpenRewrite, ambiguous model findings, or compile errors whose fix isn't a direct catalog match — propose the change via `AskUserQuestion` before applying. When unsure whether a case is unambiguous, ask.
