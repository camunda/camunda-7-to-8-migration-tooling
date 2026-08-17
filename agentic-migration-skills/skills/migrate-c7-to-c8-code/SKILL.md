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

Treat these as separate operations that compose. Ask what the user wants (Step 1, Question 3) and run only the relevant path(s).

## Shared rules

These apply throughout — referenced below instead of repeated.

- **Distinguish code from models.** OpenRewrite/AI handles code; the Diagram Converter handles models. Never hand-edit BPMN/DMN in the code flow. Ask for scope (Q3) rather than assuming.
- **Use project-local models first.** Treat any BPMN/DMN file found under the project root as the source of truth. Do not offer or request C7 engine access when local models are present. Offer the engine source only after the project-root scan finds no models and the user selected a model scope.
- **Commits are opt-in.** Verify a clean tree (`git status`) first; if dirty, ask the user to commit or stash. Never auto-commit. After each phase, ask whether to commit and proceed only on explicit approval.
- **Prefer intent over shell dialect.** Use the available agent tools to inspect files, discover configuration, create directories, download artifacts, and run commands. When command execution is required, choose the platform-appropriate invocation for the current environment instead of assuming POSIX shell syntax or Unix-only helpers.
- **Never mutate user assets silently.** Models convert to `converted-c8-*` copies; originals stay intact. Converted files and CSV/XLSX/MD reports are generated outputs for the user to review.
- **Load the reference before editing.** Never guess API/XML mappings. For gaps, prefer `docs.camunda.io` via WebFetch over training knowledge.
  - Code → pattern catalog: `https://raw.githubusercontent.com/camunda/camunda-7-to-8-migration-tooling/main/code-conversion/patterns/ALL_IN_ONE.md`. If context is tight, fetch only the individual files under `code-conversion/patterns/`.
  - Agentic models → diagram-converter docs (see M2).
- **Respect the target version** (Q2). Don't offer 8.9 features (businessId, conditional events, global user task listeners, batch delete) to an 8.8 target, or 8.8 workarounds to 8.9+. Pass the same version to the CLI via `--platform-version`.
- **Prefer deterministic over agentic.** Code: OpenRewrite + AI over AI-only. Models: CLI (M1) over agentic rewrite (M2). Use agentic only when the deterministic path can't run.
- **Conversion is not completion.** Converter finding severities: WARNING, TASK, REVIEW, INFO. TASK/REVIEW/WARNING need human follow-up and JUEL conversion is partial — always say so, then offer the Step 5 follow-up.
- **Diagram Converter: fail fast on Java, download don't ask.** Require Java 21+; stop with a clear message (offer M2/M3) if missing. Download the CLI from the latest GitHub release into `.camunda-migration/`, reuse an existing download, never commit the JAR.
- **Don't redo what the tools changed.** In Approach A, check for existing transforms before rewriting. After a converter run, don't re-apply conversions it already did.
- **Ask before High-complexity files and edge cases.** Auto-apply only unambiguous 1:1 mappings. For anything else — JUEL invoking beans, BPMN error mapping, async/correlation, IdentityService/FormService, custom batches, multi-instance listeners, ambiguous TODOs or findings, compile errors without a direct catalog match — propose via `AskUserQuestion` first. When unsure, ask.
- **Keep changes minimal.** No refactors, renames, or improvements beyond the migration.
- **Keep `MIGRATION_REPORT.md` current** — both inventories, decisions, phase status, validation results.

## Step 1: Gather inputs

Before calling `AskUserQuestion`, pick a candidate project root (use the provided argument if present, otherwise the current working directory), then detect the build tool by checking that directory for `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle). Also glob for models (`**/*.bpmn`, `**/*.bpmn20.xml`, `**/*.dmn`, `**/*.dmn11.xml`) so you can tailor the scope question (Q3) to what appears to be present. This candidate scan is only used to shape Q3; the confirmed project-root scan after Q1 is what gates whether the C7 engine option (Q5 / E1) is offered.

Then ask the questions below with `AskUserQuestion`. **Tool limits: at most 4 questions per call, and every question that supplies `options` must have at least 2 options.** Because Q1 must be answered before the confirmed project root is known and up to 6 questions may apply, batch them:

- **Call 1** — ask **Question 1** (project location) first.
- **Re-scan** — after Q1 is answered, if the selected root differs from the candidate, re-detect the build tool and re-glob for models in the confirmed root.
- **Call 2** — the always-applicable questions now that the root is confirmed: **Q2, Q3** (2 questions).
- **Call 3** — the conditional approach questions that apply given the Q3 answer and your updated detection: any of Q4, Q5, Q6 (≤3 questions).

Only include a conditional question when its stated condition holds, and never put more than 4 in one call. If a single call would exceed 4, split it further. Use the post-Q1 scan result for all subsequent gating decisions, especially whether to include the C7 engine source options (Q5 / E1).

**Question 1 — Project location**

Confirm the project root you detected. Provide two options so the question is valid:

- **Use `<detected path>`** *(recommended)* — the argument path if one was passed, otherwise the current working directory.
- **Enter a different path** — let the user type the path in the free-form field.

(If you prefer a pure free-text prompt instead, omit `options` entirely — do not send a single-option question.)

**Question 2 — Target Camunda 8 version**

Ask which specific Camunda 8 version the user is migrating to (user can't select anything else):

- **8.10** *(next version, not yet GA)* —; includes all features from 8.8 and 8.9 too.
- **8.9** *(latest stable)* — adds Business ID (business key successor), BPMN conditional events, global user task listeners, batch delete, History/Identity Data Migrator.
- **8.8** — first version with the unified Orchestration Cluster API, CamundaClient, and Camunda Process Test. No Business ID (use tags), no conditional events.

The target version changes which patterns apply **and** is passed to the Diagram Converter as `--platform-version` (valid values `8.0`–`8.10`; this skill only offers `8.8`–`8.10`). Record the concrete `major.minor` the user selects and use it throughout.

**Question 3 — Migration scope**

Ask what the user wants to migrate (tailor the wording to what you detected — code files, model files, or both, user can't select anything else):

- **Code + models** *(recommended when both are present — default to this)* — Runs both Part A and Part B and composes them.
- **Code only** — Java/Spring code. Runs Part A.
- **Models only** — BPMN/DMN diagrams. Runs Part B.
- **Assessment only** — Scan and report scope/complexity/effort for code and models. No changes.

When no local model files were found, do not silently drop models from a combined request. Keep **Code only** as the default recommendation, and offer the C7 engine source below only if the user explicitly selects **Code + models** or **Models only**.

**Question 4 — Code migration approach** *(include only if code files are present and user selected code migration, user can't select anything else)*:

- **A. OpenRewrite (deterministic) + AI** *(recommended)* — Runs OpenRewrite recipes first for deterministic bulk transforms (delegates, workers, client code). When prompted you can ask AI to resolve remaining `// TODO` comments, compilation errors, config, and test code. Best for most codebases.
- **B. AI only** — AI migrates everything directly without OpenRewrite. Use this when you can't run OpenRewrite (non-Maven/Gradle builds, restricted environments) or want to review every change individually.
- **C. Assessment only** — Scan the codebase and produce a report: file inventory, complexity estimate, effort breakdown. No code changes.

**Question 5 — Model source and migration approach** *(include only if the user selected model migration, user can't select anything else)*:

Show **only one** of the two groups below, based on the model scan result from Step 1:
- If local model files were found under the project root, show only the **M1–M3** local-model options.
- If no local model files were found, show only the **E1–E2** engine-source options; do not offer E1 when local models are present.

**Local models found — choose one of M1–M3:**

- **M1. Diagram Converter CLI (deterministic) + AI** *(recommended)* — Downloads the official `camunda-7-to-8-diagram-converter-cli` from GitHub releases into the project and runs it locally against your BPMN/DMN files, targeting your Camunda 8 version. Deterministic and repeatable; produces converted files plus analysis reports (CSV/XLSX). **Requires Java 21+.** When prompted you can ask AI to address remaining TODO items and suggest changes.
- **M2. Agentic AI** — AI rewrites the BPMN/DMN XML directly (namespace, listeners, JUEL→FEEL, event mappings). Use when Java 21 is unavailable, you want to review every change, or a niche case the CLI doesn't cover. Slower and non-deterministic.
- **M3. Online Diagram Converter (hosted)** — Upload your diagrams at **https://diagram-converter.camunda.io/** and download the converted results. No local Java needed; uses the hosted service.
- Any of the above can be run in **analyze-only** mode first (see "Analyze-only mode" in Part B) to see findings without producing converted files.

**No local models found — choose one of E1–E2:**

- **E1. Camunda 7 engine (recommended)** — Fetch BPMN/DMN definitions from a reachable C7 REST API and pass them through the same deterministic conversion flow. The CLI engine path converts fetched XML in memory and writes converted outputs; named-key acquisition can stage raw XML and use M1 local mode. Ask for C7 access before connecting; the required details are described in Part B.
- **E2. Provide a model path** — Wait for the user to provide another file or directory, re-run model detection there, and then show M1–M3.

**Question 6 — Build tool** *(include only if scope includes code, approach is A, and detection was ambiguous — both Maven and Gradle found, or neither)*: "Which build tool should I use for the OpenRewrite step: Maven or Gradle?" If exactly one build tool was detected, do not ask; state the detection in the approach question text instead (e.g. "Detected Maven."). Do not proceed until you have the answer.

When the user accepts the defaults (recommended options) without changing anything, proceed directly. Be opinionated — the recommended options are what most projects need.

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

If the inventory is empty and the user selected model migration, record that no local source models were found and that E1 (C7 engine source) was offered. Do not report an empty local inventory as a successful model migration.

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

Commit policy and reference loading: see Shared rules. Run **Part A** if the scope includes code, **Part B** if it includes models. For **Code + models**, see "Composing code + model migration" at the end of this step.

---

## Part A — Code migration (Java/Spring)

Both approaches apply the same **Transform checklist** below. Approach A runs OpenRewrite first for the deterministic bulk, then uses the checklist for the rest; Approach B works the whole checklist manually.

### Transform checklist

Confirm each item before the next (commit policy: Shared rules). Tags mark what OpenRewrite already handles.

**1. Dependencies & configuration**
- Resolve the latest Camunda version via WebFetch: `https://search.maven.org/solrsearch/select?q=g:io.camunda+AND+a:camunda-spring-boot-starter&rows=1&wt=json` → `response.docs[0].latestVersion`. For 8.8 targets use the latest `8.8.x`; for 8.9+ use it as-is.
- Pick the starter by Spring Boot version: 3.x → `io.camunda:camunda-spring-boot-3-starter`; 4.x → `io.camunda:camunda-spring-boot-starter`.
- Add the Camunda public repo if artifacts aren't on Maven Central:
  - Maven: `<repository><id>camunda-public</id><url>https://artifacts.camunda.com/artifactory/public/</url></repository>`
  - Gradle: `maven { url "https://artifacts.camunda.com/artifactory/public/" }`
- Remove `org.camunda.bpm.*`, `camunda-bom`, and embedded-engine deps (H2, JDBC starter).
- Add the starter; add `io.camunda:camunda-process-test-spring` (test scope) if tests exist.
- Ensure Spring Boot dependency management is set (parent or BOM); don't add `spring-boot-starter` just for jakarta.annotation.
- Replace `@EnableProcessApplication` with `@Deployment`. If the C7 app relied on implicit classpath auto-deployment (no `@EnableProcessApplication` present), still add explicit `@Deployment(resources = ...)` for BPMN/DMN files because C8 has no equivalent implicit deployment.
- Replace `camunda.*` keys with `camunda.client.*` in `application.properties`/`.yaml` ([properties reference](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/properties-reference/)).
- Reference: "Maven dependency and configuration".

**2. Client code** (`ProcessEngine` → `CamundaClient`)
- Replace `ProcessEngine`/service autowiring (RuntimeService, TaskService, HistoryService, DecisionService, ManagementService) with `CamundaClient`.
- Map: start instances (incl. `businessId`/tags), message correlation, signal broadcast, cancel, user tasks, variables, `HistoryService` → search requests, `DecisionService` → `newEvaluateDecisionCommand`, batch `...Async` → batch operations (8.8+).
- Reference: "Client code → ProcessEngine" (incl. Business Key, Batch Operations, Evaluate Decisions, Query History).

**3. JavaDelegate → Job Worker** *(OpenRewrite covers this)*
- Remove `implements JavaDelegate`; convert `execute(DelegateExecution)` to a `@JobWorker` method.
- Variable access → method params / `@Variable`. `BpmnError` → `CamundaError.bpmnError(...)`. Remove `TypedValue` usage.
- Reference: "Glue code → JavaDelegate → Job Worker".

**4. External task workers** *(OpenRewrite covers this)*
- `@ExternalTaskSubscription` → `@JobWorker`; update variable access and failure/incident handling.
- Reference: "Glue code → External Task Worker".

**5. Listeners** *(not covered by OpenRewrite)*
- `ExecutionListener` → execution listener job workers (`zeebe:executionListener` + `@JobWorker`).
- `TaskListener` → user task listener job workers (job result with corrections/deny).
- Globally registered listeners (engine plugins, parse listeners) on user tasks → global user task listeners (8.9+).
- Flag multi-instance body listeners that prepare collections — requires a model change.
- Reference: "Glue code → Listeners".

**6. Test code** *(not fully covered by OpenRewrite)*
- `@Rule` Camunda test rules → `@CamundaSpringProcessTest`.
- Update assertions (`isWaitingAt("id")` → `hasActiveElements("id")`), message correlation, timers, and user task completion — use `processTestContext` (`completeUserTask`, `completeJob`, `mockJobWorker`, `increaseTime`).
- Disable real workers where mocked: `camunda.client.worker.defaults.enabled=false` with per-worker overrides.
- On 8.9+, use CPT shared-runtime mode for large suites.
- Reference: "Test assertions".

**7. JUEL expressions** *(not covered by OpenRewrite)*
- Pure data expressions → FEEL (the converter automates this model-side, Part B). Conditional events are native since 8.9. Only bean-invoking expressions need a JUEL job worker or a refactor into job workers.
- Reference: "Expression → Job Worker".

### Approach A — OpenRewrite + AI (recommended)

**1. Run OpenRewrite** — deterministic bulk transforms for delegates, external workers, and client code.

RECIPES_VERSION by Camunda target, use the latest from these minor versions: 8.8 → `0.2.x`; 8.9 and 8.10 → `0.3.x`.

REWRITE_VERSION: Before adding the plugin, resolve the latest released version via WebFetch:
- `rewrite-maven-plugin` (OpenRewrite): `https://search.maven.org/solrsearch/select?q=g:org.openrewrite.maven+AND+a:rewrite-maven-plugin&rows=1&wt=json` → read `response.docs[0].latestVersion`


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

Before AI cleanup, ask whether to commit the OpenRewrite result (commit policy: Shared rules).

**2. AI cleanup — after OpenRewrite**

Ask the user whether to run AI cleanup; proceed only on YES. Load the pattern catalog (Shared rules), then work the Transform checklist for what OpenRewrite left:

- Resolve all `// TODO` comments it inserted, and fix compile errors.
- Apply items **1** (deps/config), **5** (listeners), **6** (tests), **7** (JUEL), and any **2** (client code) the recipes didn't cover.

---

### Approach B — AI only

Load the pattern catalog (Shared rules), then work the full Transform checklist (items 1–7) in order, confirming each before the next.

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

Converts BPMN/DMN from the `camunda:` namespace to `zeebe:`: job types for delegates, listener mappings, simple JUEL→FEEL, event definitions, version-gated features. Prefer the deterministic CLI (M1). See Shared rules for severities and "conversion is not completion".

### Source selection

Use the model scan from Step 1 before choosing a conversion path:

- If one or more local model files were found under the project root, use local mode and do not offer or request C7 engine access. Run M1, M2, or M3 against those files.
- If no local model files were found and the user selected E1, fetch the requested definitions from C7 first. For all-latest acquisition, the engine subcommand converts fetched XML in memory; for named-key acquisition, stage the raw XML and run the existing local conversion flow. Do not continue with an empty input directory.

### Approach E1 — Camunda 7 engine source (only when no local models were found)

The Diagram Converter CLI already has an `engine` subcommand for C7 REST acquisition. Use it only for the no-local-model case; it queries the latest process and decision definitions and their XML, converts the fetched XML in memory, and writes the converted outputs and optional analysis reports.

**1. Ask for C7 access**

Before contacting the system, use `AskUserQuestion` to request the access needed for this run:

- Preferred: the C7 engine REST base URL, including its `/engine-rest` context path when applicable.
- Authentication: no authentication or Basic authentication username/password. Obtain secrets through the agent's secure credential mechanism; never write them to `MIGRATION_REPORT.md` or commit them. **Caution:** the CLI's `--password` flag exposes secrets in shell history and OS process listings, so use a trusted environment and prefer temporary or dedicated credentials.
- If REST access is unavailable, ask whether a supported database-backed extractor is available and request only the connection details it requires. The released `engine` CLI path supports REST with optional Basic authentication, not direct database extraction or OIDC; stop with that limitation rather than pretending a DB/OIDC connection was used.

Also ask whether to fetch all latest process/decision definitions or only named keys. The existing `engine` command fetches all latest definitions. For a named set, query the C7 REST list endpoints with the appropriate key filters, fetch each definition's `/xml` resource, write the XML files to a staging directory such as `.camunda-migration/c7-models/`, and then run M1 local mode on that directory.

**2. Fetch and convert**

For the all-latest case, download/reuse the CLI as in M1, create `.camunda-migration/c7-models`, and invoke it with the platform-appropriate command runner:

```
java -Dfile.encoding=UTF-8 -jar <jar> engine <c7-rest-url> --target-directory .camunda-migration/c7-models --platform-version <target-version> [--username <username> --password <password>] [--csv] [--xlsx]
```

Keep the target directory as generated working state, never as a replacement for project source files. Report every converted BPMN/DMN output and analysis artifact. For named-key acquisition, retain the raw staged XML and invoke the `local` subcommand so the downstream conversion is identical to a file-based migration.

**3. Handle failures without partial success**

Treat an unreachable endpoint, TLS/DNS failure, `401`/`403`, malformed XML, or an empty response for a requested definition as a blocking error. Report the URL, operation, status/error, and the concrete next action (correct the URL, grant REST access, or provide supported credentials). Do not silently continue, convert a partial set, or report success when any requested definition failed to fetch.

### Approach M1 — Diagram Converter CLI +AI (deterministic, recommended)

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

**5. Follow up on findings**

REVIEW/WARNING/TASK findings remain and JUEL conversion is partial — resolve them in Step 5, working on the `converted-c8-*` copies (never the originals).

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

### Approach M3 — Online Diagram Converter (hosted)

If the user prefers not to run the local CLI, point them to the hosted converter:

> Upload your BPMN/DMN files at **https://diagram-converter.camunda.io/**, set the target version there, and download the converted results.

This path does not automate the hosted service. Once the user brings the converted files back into the project, you can offer the same agentic findings follow-up as in M1 step 5.

### Analyze-only mode

For "analyze but don't convert": run M1 with `--check` (optionally `--csv`/`--xlsx`) to produce findings and reports with no converted files, or do an M2 read-only pass. Present the findings grouped by severity and stop — make no diagram changes.

---

## Composing code + model migration

When the scope is **Code + models**:
- The two paths are independent — run each with its chosen approach. Order doesn't strictly matter; a reasonable default is **models first** (diagrams define the job types/listeners the code must implement), then code, but follow the user's preference.
- Keep both inventories and both sets of results in `MIGRATION_REPORT.md`.
- After both complete, cross-check: job types emitted by the Diagram Converter should match the `@JobWorker(type = ...)` values produced by the code migration. Flag mismatches for the user.
- After both complete, ask whether to wire deployment of converted files in application code via `AskUserQuestion`:
  - **Yes, add/update `@Deployment` for converted files** *(recommended when code scope includes a Spring Boot app)* — add or update `@Deployment(resources = ...)` so it targets only converted resources with explicit recursive classpath patterns (for example, `@Deployment(resources = {"classpath*:**/converted-c8-*.bpmn", "classpath*:**/converted-c8-*.dmn"})`) and never the original diagrams.
  - **No, I will handle deployment outside app startup** — leave code unchanged and record this decision in `MIGRATION_REPORT.md`.

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

Record the summary in `MIGRATION_REPORT.md`.

---

## Step 5: AI follow-up (offer after validation)

After presenting the validation summary, if there are remaining TODOs, REVIEW/WARNING/TASK findings, compilation issues, or unresolved items, **proactively offer** to resolve them:

> I found [N] remaining items that need follow-up. Would you like me to take care of them? I can:
> - Fix remaining `// TODO` comments based on the pattern catalog
> - Resolve REVIEW/TASK/WARNING findings in the converted models
> - Suggest fixes for compilation errors or test failures
> - Fill in blank job types or FEEL expressions where the mapping is clear
>
> I'll propose each change for your review before applying it.

Use `AskUserQuestion` with:
- **Yes, fix what you can** *(recommended)* — AI resolves unambiguous items, proposes each for review.
- **Show me the list first** — Present the full list of remaining items grouped by type, then ask which to fix.
- **No, I'll handle the rest manually** — Stop here; record remaining items in `MIGRATION_REPORT.md`.

When the user opts in, work through items one at a time: apply unambiguous fixes directly (using the pattern catalog / converter docs as reference), propose ambiguous ones via `AskUserQuestion`, and skip anything the user declines. Ask whether to commit after each batch of resolved items.
