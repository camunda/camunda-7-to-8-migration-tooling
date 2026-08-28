---
name: migrate-c7-to-c8-code
license: Camunda License 1.0
description: |-
  Migrates Camunda 7 / camunda-bpm projects to Camunda 8. Handles Java/Spring code (JavaDelegates, ExternalTaskWorkers, ProcessEngine/RuntimeService client code, execution/task listeners, application.properties/application.yaml with camunda.* keys) and BPMN/DMN models (diagrams with the camunda: namespace). Use for code migration, model migration, or both.
---

# Camunda 7 to 8 Migration

Migrate a Camunda 7 project to Camunda 8. A project can contain two independent kinds of assets, each with its own migration path:

- Code: Java/Spring glue and client code, config, tests. Migrated with OpenRewrite recipes (deterministic) plus AI cleanup.
- Models: BPMN/DMN diagrams using the camunda: namespace. Migrated with the Diagram Converter (deterministic) or agentically.

## Step 0: Model suitability

Before scanning, inspect any active model identifier or capability metadata exposed by the host; do not infer it or use undocumented variables. This skill is intended for complex, multi-file reasoning. Illustrative recommended IDs include `claude-sonnet-*`, `claude-opus-*`, `gpt-5.6-luna`, `gpt-5.6-terra`, and `gpt-5.6-sol`; illustrative caution IDs include `gpt-5-mini`, `gpt-5.4-mini`, and `gemini-3.7-flash`. These are routing examples, not a benchmark or exhaustive ranking; use host capability metadata when available and treat unknown IDs as unverified.

If the model is explicitly lightweight (mini, small, lite, flash, haiku, etc.) or cannot be verified, warn the user and use AskUserQuestion (or the host equivalent):
- **Switch to a model intended for complex reasoning (recommended)** — explain the host's model selector, wait for the user's confirmation, then re-read host-provided model metadata before continuing.
- **Continue** — use deterministic approaches and extra human review; after the project root is confirmed, record the warning and choice in `MIGRATION_REPORT.md`.

Recheck before AI-only, agentic rewrites, or AI cleanup if the host allows model changes. Record the model status in `MIGRATION_REPORT.md` after the project root is confirmed.

## Entry Criteria

1. Project uses Camunda 7 (camunda-bpm) dependencies in Maven or Gradle
2. Project contains one or more of: JavaDelegate implementations, ExternalTaskWorkers, ProcessEngine/RuntimeService client code, execution/task listeners, BPMN/DMN files with camunda: namespace, or application config with camunda.* keys
3. Target is Camunda 8 version 8.8, 8.9, or 8.10
4. For OpenRewrite approach (recommended): Maven or Gradle build system available
5. For Diagram Converter CLI: Java 21+ available (alternatives exist if not)

## Implementation Steps

### Step 1: Gather Inputs

See `references/interview-questions.md` for the full question set and batching rules.

1. Detect project root, build tool (pom.xml or build.gradle/build.gradle.kts), and model files (*.bpmn, *.bpmn20.xml, *.dmn, *.dmn11.xml).
2. Ask Question 1 (project location) via AskUserQuestion.
3. After confirmation, re-scan the confirmed root if it differs from the candidate.
4. Ask Questions 2-3 (target version, scope) together.
5. Ask conditional Questions 4-6 (code approach, model approach, build tool) as applicable.
6. When user accepts defaults, proceed directly.

Shared rules that apply throughout all subsequent steps:
- Distinguish code from models. OpenRewrite/AI handles code; the official Diagram Converter handles models when applicable. Never hand-edit BPMN/DMN namespaces when the converter applies.
- Use project-local models first. Do not offer C7 engine access when local models are present.
- Commits are opt-in. Check for uncommitted changes before starting; if dirty, ask user to commit or stash. Never auto-commit.
- Prefer intent over shell dialect. Use platform-appropriate invocations for the current environment.
- Never mutate user assets silently. Models convert to converted-c8-* copies; originals stay intact.
- Load the pattern catalog before editing. Never guess API/XML mappings. See `references/pattern-catalog-sources.md`.
- Verify Camunda, Spring Boot, and dependency compatibility against official Camunda documentation or Maven metadata. Preserve the selected target or maintenance branch configuration; never choose a newer platform version by assumption.
- Respect the target version and pass the exact selected platform version to the Diagram Converter. Do not offer features from a higher version than selected.
- Prefer deterministic approaches. Code: OpenRewrite + AI over AI-only. Models: the official converter CLI over agentic rewriting when it applies.
- Conversion is not completion. TASK/WARNING/REVIEW findings, unsafe or partial transformations, unsupported behavior, and generated Task Forms are explicit human-review items. If C8 cannot represent something safely, ask the user instead of inventing a conversion.
- Do not redo what the tools changed. Check for existing transforms before rewriting.
- Ask before high-complexity files and edge cases. Auto-apply only unambiguous 1:1 mappings.
- Keep changes minimal. No refactors, renames, or improvements beyond the migration.
- Keep one living `MIGRATION_REPORT.md` in the confirmed project root as the source of truth for inventories, findings, warnings, incompatibilities, behavior changes, decisions, intentional deviations, phase status, and validation results.
- Include the model preflight result, model identifier or unverified status, and any user decision to continue with a caution/unverified model in `MIGRATION_REPORT.md`.
- For backports, `maintenance/0.2` maps to Camunda 8.8 and `maintenance/0.3` maps to Camunda 8.9. Preserve the target branch configuration; use `git cherry-pick -x` for manual backports, resolve conflicts surgically, do not copy incompatible main-only APIs, and report intentional deviations. Never merge or enable auto-merge; a human merges after required CI is green.

### Step 2: Assessment (always runs)

Scan the project and produce inventories relevant to the chosen scope.

#### Code Inventory

Identify and classify all Camunda 7 related Java/config files into a table with columns: File, Type, Complexity, Notes. See `references/code-transform-checklist.md` for detection hints and type classifications.

#### Model Inventory

Glob for model files. For each, note whether it uses the camunda: namespace and record its path in a table with columns: File, Type, Uses camunda: ns, Notes.

If inventory is empty and user selected model migration, record that no local models were found and that E1 was offered.

#### Summary

Present: total code/model files, overall complexity, whether OpenRewrite would help, blockers requiring manual decision, the model preflight result (including any user acknowledgment), and a note that running instances/history/audit data are out of scope (point to Data Migrator).

Write assessment to MIGRATION_REPORT.md. Use AskUserQuestion to wait for confirmation before proceeding.

### Step 3: Execute Migration

Run Part A if scope includes code, Part B if scope includes models. For Code + models, see `references/composing-code-and-models.md`.

#### Part A - Code Migration

Apply the Transform checklist from `references/code-transform-checklist.md` using the selected approach:

- Approach A (OpenRewrite + AI): See `references/code-migration-approaches.md` for full OpenRewrite setup, Java compatibility checks, and AI cleanup procedure.
- Approach B (AI only): Load pattern catalog, work full checklist items 1-7 in order, confirming each.
- Approach C (Assessment only): Detailed report with effort estimates, no code changes.

#### Part B - Model Migration

Convert BPMN/DMN from the camunda: namespace to zeebe: using the selected approach:

- Approach M1 (Diagram Converter CLI + AI): See `references/model-migration-approaches.md` for CLI download, execution, and findings handling.
- Approach M2 (Agentic AI): Direct XML rewrite without CLI. See `references/model-migration-approaches.md`.
- Approach M3 (Online Converter): User uploads at hosted service manually.
- Approach E1 (C7 Engine Source): Fetch from C7 REST API when no local models. See `references/model-migration-approaches.md`.

### Step 4: Validation (always runs)

#### Code Validation (if code was migrated)

1. Compile: run `mvn compile` or platform-appropriate Gradle compile task. Fix all errors.
2. Check for remaining C7 references: search for `org.camunda.bpm` imports. Each is a missed migration.
3. Check for remaining TODOs: search for `// TODO` migration comments. Each needs manual review.
4. Check for legacy C8 client: search for `ZeebeClient` and `zeebe-client-java` (deprecated, removed in 8.10; migrate to CamundaClient).
5. Check for leftover business keys: search for `businessKey`. Map stable C7 business keys to C8 TAGs; if the key changes during execution, use a `businessKey` process variable. Verify and record any target-specific `businessId` decision.
6. Run tests: run `mvn test` or platform-appropriate Gradle test task. Fix failures.
7. Check common pitfalls:
   - Critical naming swap: C7 processDefinitionKey (string key) becomes C8 bpmnProcessId; C7 processDefinitionId (UUID) becomes C8 processDefinitionKey. Same for decision definitions.
   - Process instance IDs changed from String to Long.
   - VariableMap usage: variables are now plain JSON, TypedValue API is gone.
   - HistoryService references: map to search endpoints (eventually consistent).
   - Batch operations: available since 8.8; only custom batch handlers need manual design.

#### Model Validation (if models were migrated)

1. Confirm a converted-c8-* file exists for each in-scope diagram (unless analyze-only).
2. Every WARNING/TASK/REVIEW finding is either fixed or classified in the per-category verdict table (category, count, cross-referenced code artifact, verdict: no action / needs review / needs fix) recorded in MIGRATION_REPORT.md. Record unsafe, partial, or unsupported transformation results as explicit human-review items as well — see `references/model-migration-approaches.md` step 5d. A flat "fixed or recorded" note is not sufficient.
3. Originals are intact and were not overwritten.
4. Every C7 or generated form is represented by a standard C8 form linked from the converted BPMN; generated Task Forms and unsupported validation rules remain explicitly recorded for manual review.

Present a validation summary showing status of: compilation, remaining C7 imports, remaining TODOs, businessKey usages, tests, models converted, and model findings needing follow-up. Record in MIGRATION_REPORT.md.

### Step 5: AI Follow-up (offer after validation)

If there are remaining TODOs, findings, compilation issues, deletion candidates, or unresolved items, proactively offer to resolve them. For model findings, work from the per-category verdict table (Step 4): categories with verdict **needs fix** are the follow-up work items; do not present findings as one undifferentiated list.

> I found [N] remaining items that need follow-up. Would you like me to take care of them?

Use AskUserQuestion with options:
- Yes, fix what you can (recommended): AI resolves unambiguous items, proposes each for review.
- Show me the list first: Present full list grouped by type, then ask which to fix.
- No, I will handle the rest manually: Stop here; record remaining items in MIGRATION_REPORT.md.

When user opts in: apply unambiguous fixes directly using the pattern catalog, propose ambiguous ones via AskUserQuestion, skip anything declined. For model findings, resolve one **needs fix** category at a time using that category's cross-check guidance; categories with verdict **needs review** each need a user decision via AskUserQuestion before any fix; categories with verdict **no action** are not offered. Ask whether to commit after each batch, and update the verdict table in MIGRATION_REPORT.md as categories are resolved.

A second action type beyond fixing TODOs/findings is **delete now-redundant code**: the model/code cross-check flags C7-side workaround code as deletion candidates when a finding reports the capability is now native in Zeebe (see `references/composing-code-and-models.md`, "Now-redundant workaround code"). Deleting code is never unambiguous — even under "Yes, fix what you can", present every deletion candidate via AskUserQuestion with its reasoning (the triggering finding, what the code did, why it is now redundant) and delete only on explicit confirmation. Record confirmed deletions and declined candidates in MIGRATION_REPORT.md.

## Validation / Exit Criteria

1. All Camunda 7 dependencies (org.camunda.bpm.*) are removed from build files
2. No remaining org.camunda.bpm imports in Java source files
3. Project compiles successfully with Camunda 8 dependencies
4. All tests pass (or failures are documented with explanation)
5. All // TODO migration comments are resolved or explicitly recorded
6. No ZeebeClient/zeebe-client-java references remain (deprecated)
7. Stable business keys are mapped to TAGs; keys that change during execution use a `businessKey` process variable, with any target-specific `businessId` decision verified and recorded
8. Configuration uses camunda.client.* keys instead of camunda.* keys
9. For model migration: converted-c8-* files exist for all in-scope diagrams
10. For model migration: all WARNING/TASK/REVIEW findings are resolved or classified in the verdict table in MIGRATION_REPORT.md
11. MIGRATION_REPORT.md contains complete inventories, decisions, and validation results
12. Original model files are intact (never overwritten)
