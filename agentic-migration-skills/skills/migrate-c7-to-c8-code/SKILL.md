---
name: migrate-c7-to-c8-code
description: |-
  Migrates Camunda 7 / camunda-bpm projects to Camunda 8. Handles Java/Spring code (JavaDelegates, ExternalTaskWorkers, ProcessEngine/RuntimeService client code, execution/task listeners, application.properties/application.yaml with camunda.* keys) and BPMN/DMN models (diagrams with the camunda: namespace). Use for code migration, model migration, or both.
license: Camunda License 1.0
---

# Camunda 7 to 8 Migration

Migrate a Camunda 7 project to Camunda 8. A project holds two independent kinds of assets:

- **Code** — Java/Spring glue and client code, config, tests. Migrated with OpenRewrite recipes
  (deterministic) plus AI cleanup.
- **Models** — BPMN/DMN diagrams in the `camunda:` namespace. Migrated with the Diagram Converter
  (deterministic) or agentically.

Every instruction here is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an
option is marked (MAY).

## Step 0: Model preflight

This skill needs complex, multi-file reasoning. Before the scan, read the active model identifier or
capability metadata that the host exposes. Never infer it and never read undocumented variables.
Recommended examples: `claude-sonnet-*`, `claude-opus-*`, `gpt-5.6-luna`, `gpt-5.6-terra`,
`gpt-5.6-sol`. Caution examples: `gpt-5-mini`, `gpt-5.4-mini`, `gemini-3.7-flash`. These are routing
examples, not a benchmark and not a ranking. Prefer host capability metadata, and treat an unknown
identifier as unverified.

If the model is lightweight (mini, small, lite, flash, haiku, and similar) or unverified, then warn
the user and ask through AskUserQuestion, or the host equivalent:

- **Switch to a model built for complex reasoning (recommended)** — explain the host model selector,
  wait for confirmation, then read the host model metadata again.
- **Continue** — use deterministic approaches and ask for extra human review.

Where the host permits a model change, repeat this check before AI-only migration, an agentic
rewrite, and AI cleanup. Once the user confirms the project root, record the preflight result, the
model identifier or its unverified status, and any decision to continue on a caution or unverified
model in `MIGRATION_REPORT.md`.

## Entry Criteria

1. The project declares Camunda 7 (camunda-bpm) dependencies in Maven or Gradle.
2. The project holds at least one of the asset kinds named in this skill's description.
3. The target is Camunda 8 version 8.8, 8.9, or 8.10.
4. Where OpenRewrite is selected (recommended), Maven or Gradle is available.
5. Where the Diagram Converter CLI is selected, Java 21+ is on `PATH` or in a user-supplied JDK home.
   Alternatives exist when it is not.

## Implementation Steps

### Step 1: Gather Inputs

See `references/interview-questions.md` for the question set and the batching rules.

1. Detect the project root, the build tool (`pom.xml`, or `build.gradle` / `build.gradle.kts`), and
   the model files (`*.bpmn`, `*.bpmn20.xml`, `*.dmn`, `*.dmn11.xml`).
2. Ask Question 1 (project location) through AskUserQuestion.
3. If the confirmed root differs from the candidate, then scan the confirmed root again.
4. Ask Questions 2 and 3 (target version, scope) together.
5. Ask Questions 4 to 6 (code approach, model approach, build tool) where they apply.
6. When the user accepts the defaults, continue without further questions.

#### Shared rules

These rules apply to every later step.

**Assets and tools**

- Route code to OpenRewrite and AI. Route models to the Diagram Converter.
- The Diagram Converter converts the models in M1, M3, and E1.
- Only M2 edits BPMN/DMN agentically, and only on a converted copy.
- Never hand-edit BPMN or DMN in the code flow.
- Use project-local models first. While local models exist, never offer or request Camunda 7 engine
  access.
- Prefer the deterministic path: OpenRewrite plus AI over AI-only for code, the CLI over an agentic
  rewrite for models. (SHOULD)
- Use invocations that suit the current platform. Never assume one shell dialect.

**Safety**

- Before the first change, check for uncommitted changes. If the working tree is dirty, then ask the
  user to commit or stash.
- Never commit without an explicit user request.
- Write each converted model to a `converted-c8-*` copy. Leave every original file unchanged.
- Where the target is a separate location, such as a sibling Camunda 8 project, treat the Camunda 7
  project as read-only and copy the assets across.
- Before any edit, load the pattern catalog. See `references/pattern-catalog-sources.md`.
- Never guess an API mapping or an XML mapping.
- Never offer a feature from a version above the selected target.
- Before each Java-dependent phase, run `java -version` on `PATH`. If that major version is missing
  or incompatible, then ask for an alternate JDK home and check it before continuing.
- Scope that JDK home to one phase.
- Apply a mapping unasked only when it is an unambiguous 1:1 mapping.
- Ask before changing a high-complexity file or an edge case.

**Minimal, faithful change**

- Never refactor, rename, or improve anything beyond the migration.
- Before rewriting code, check whether a tool already transformed it.
- Carry package names, class names, file and folder layout, resource paths, startup behavior, and the
  dependency footprint across unchanged wherever a Camunda 8 equivalent exists.
- Rename or delete only what has no Camunda 8 equivalent, and record why in `MIGRATION_REPORT.md`.
- Many Camunda 7 patterns exist only because of a Camunda 7 limitation: flat form binding, no
  computed display value, the in-process engine API. Before replicating such a pattern or adding a
  worker, check whether Camunda 8 no longer has the limitation.
- When a finding reports that Zeebe now supports a capability natively, run the same check on the
  Camunda 7 workaround code. See `references/composing-code-and-models.md`.

**Findings and report**

- A finished conversion is not a finished migration. Every WARNING, TASK, and REVIEW finding needs
  human follow-up.
- An INFO finding is informational until a later cross-check identifies work.
- Converter annotations are temporary review metadata. Once the verdict table is complete, strip
  `conversion:*` elements and attributes from the converted copies with namespace-aware XML tooling.
- Keep `MIGRATION_REPORT.md` in the confirmed project root and keep it current. It holds the verdict
  table, and it is the single source of truth for inventories, decisions, phase status,
  incompatibilities, and validation results. Never scatter this record across separate notes.

**Forms**

A **form-free owner** is a user task or a process-level none start event that carries no form metadata
at all.

- Build the form inventory from the original BPMN source in Step 2, never from converter findings.
  Treat findings as corroboration only.
- If a finding and the source disagree, then report the disagreement. Never pick one side silently.
- Generated Task Forms are an agentic follow-up, not a Diagram Converter feature. Keep the converter's
  `form-data` manual finding, generate standard `.form` resources from the exact original BPMN, and
  follow `references/form-migration.md` for every decision and every link.
- Copying a reference does not migrate a referenced form. Embedded HTML/JavaScript keys, external or
  custom application keys, Camunda Form references, and form-free owners each need their own
  decision. See `references/form-reference-migration.md`.
- Offer to rebuild a form as a Camunda 8 form, and generate one only on an explicit user request. A
  rebuilt form reproduces the data contract, never the Camunda 7 user interface.
- Never accept, link, or deploy a generated form before the user reviews it. Ask about every semantic
  gap and every unsupported construct, and never invent a replacement.

### Step 2: Assessment (always runs)

Scan the project and produce the inventories that the chosen scope needs.

#### Code Inventory

Classify every Camunda 7 related Java file and config file into a table with the columns File, Type,
Complexity, Notes. See `references/code-transform-checklist.md` for the detection hints and the type
classifications.

#### Model Inventory

Glob for the model files. Record each one in a table with the columns File, Type, Uses `camunda:` ns,
Notes.

Then parse every original BPMN with a namespace-aware parser and inventory all three Camunda 7 form
surfaces:

| Surface | Record |
|---|---|
| Generated Task Forms (`camunda:formData`, `camunda:formProperty`) | source file, process id, owning user task or start event, field count, business-key field, custom types and validators, initial status |
| Referenced forms (`camunda:formKey`, `camunda:formRef`) | the classification, and whether the referenced HTML or `.form` file exists in the project |
| Generic Task Forms (no form metadata at all) | every form-free owner affected |

See `references/form-reference-migration.md` for the classification rules and the full inventory
columns.

If the model inventory is empty and the user selected model migration, then record that no local
model was found and that E1 was offered.

#### Summary

Present the code file count, the model file count, the overall complexity, whether OpenRewrite would
help, the blockers that need a manual decision, and the Step 0 preflight result including any user
acknowledgment. State that running instances, history, and audit data are out of scope, and point the
user to the Data Migrator.

Write the assessment to `MIGRATION_REPORT.md`. Ask the user to confirm before Step 3, using
AskUserQuestion.

### Step 3: Execute Migration

Run Part A when the scope includes code. Run Part B when the scope includes models. For Code +
models, see `references/composing-code-and-models.md`.

#### Part A - Code Migration

Apply the Transform checklist from `references/code-transform-checklist.md` with the approach chosen
in Question 4. See `references/code-migration-approaches.md` for all three.

- **A. OpenRewrite + AI** (recommended) — run the recipes, then clean up what they left.
- **B. AI only** — work checklist items 1 to 8 in order, confirming each one.
- **C. Assessment only** — report with effort estimates, no code changes.

#### Part B - Model Migration

Convert BPMN/DMN from the `camunda:` namespace to `zeebe:` with the approach chosen in Question 5.
See `references/model-migration-approaches.md` for all four.

- **M1. Diagram Converter CLI + AI** (recommended) — download and run the CLI, then handle the
  findings.
- **M2. Agentic AI** — rewrite the XML directly, without the CLI.
- **M3. Online Converter** — the user uploads the diagrams at the hosted service.
- **E1. Camunda 7 engine source** — fetch the definitions from the Camunda 7 REST API when no local
  model exists.

For every approach, once each original BPMN is paired with its converted copy, run
`references/form-migration.md` for the Generated Task Forms, then
`references/form-reference-migration.md` for the referenced forms and the form-free owners.
### Step 4: Validation (always runs)

Each item below is a check to run and a condition that must hold at exit. Record every result in
`MIGRATION_REPORT.md`.

#### Code checks, when code was migrated

1. **Compile** — run `mvn compile` or the Gradle compile task. Fix every error.
2. **Camunda 7 dependencies** — no dependency with groupId `org.camunda.bpm` remains in the build files. No dependency with a groupId that starts with `org.camunda.bpm.` remains either.
3. **Camunda 7 imports** — search `org.camunda.bpm`. No import remains. Each one is a missed
   migration.
4. **Migration TODOs** — search `// TODO`. Each one needs manual review, and each is resolved or
   recorded.
5. **Legacy Camunda 8 client** — search `ZeebeClient` and `zeebe-client-java`. No reference remains.
   Use `CamundaClient`.
6. **Business keys** — search `businessKey`. Each use maps per the pattern catalog: businessId on
   8.9+, tags on 8.8. A key the process mutates stays a `businessKey` process variable.
7. **Configuration** — `camunda.client.*` keys replace the `camunda.*` keys in
   `application.properties` or `.yaml`.
8. **Tests** — run `mvn test` or the Gradle test task. Every test passes, or each failure is
   documented with an explanation.

Check these pitfalls as well:

- Naming swap: Camunda 7 `processDefinitionKey` (a string key) becomes Camunda 8 `bpmnProcessId`, and
  Camunda 7 `processDefinitionId` (a UUID) becomes Camunda 8 `processDefinitionKey`. Decision
  definitions swap the same way.
- Camunda 7 `processInstanceId` is a `String`. Camunda 8 `processInstanceKey` is a `Long`. Update declarations and call sites, not only the names.
- Variables are plain JSON and the `TypedValue` API is gone, so every `VariableMap` use changes.
- `HistoryService` calls map to search endpoints, which are eventually consistent.
- Batch operations exist since 8.8. Only a custom batch handler needs a manual design.

#### Model checks, when models were migrated

After every manual BPMN edit, lint the converted copy with the Camunda compatibility ruleset for the
target version. See the linting section in `references/model-migration-approaches.md`.

1. A `converted-c8-*` file exists for every in-scope diagram, unless the run is analyze-only.
2. Every original file is intact and was never overwritten.
3. Every WARNING, TASK, and REVIEW finding is fixed, or classified in the per-category verdict table
   with its category, count, cross-referenced code artifact, and verdict. See
   `references/model-migration-approaches.md` step 5d. A flat "fixed or recorded" note is not enough.
4. Every source Generated Task Form is `accepted`, `blocked`, or `declined`, including a
   form-property-only definition. None is silently omitted.
5. Every accepted form is a standard Camunda 8 `.form`. It parses, it validates or renders with
   target-compatible form-js tooling where that tooling exists, it has a matching
   `zeebe:formDefinition`, and it is deployed with its BPMN.
6. No draft, blocked, or declined form is linked or deployed. Every semantic gap and every user
   decision is recorded.
7. Every referenced form and every form-free owner has a recorded per-category decision and a final
   status of `kept`, `relinked`, `accepted`, `declined`, `deferred`, or `blocked`. The in-progress
   statuses `pending` and `draft` must not remain. A `deferred` or `blocked` item stays open follow-up
   work. A kept external reference is never reported as a completed migration, and no category is
   closed as **no action** because the converter copied a reference.
8. Every relinked or rebuilt form is referenced by `zeebe:formDefinition@formId` with a recorded
   binding decision: `bindingType` written for `deployment` and `versionTag`, or `latest` left
   deliberately to the Camunda 8 default. The copied Camunda 7 `externalReference` or `formKey` is
   gone from that element.
9. Once the verdict table is complete, the converted copies hold no `conversion:*` node, no
   `conversion:*` attribute, no unused Camunda 7 namespace declaration, and no leftover BPMN
   definitions-level XPath `expressionLanguage` attribute.

#### Summary

Present a validation summary that states the status of compilation, remaining Camunda 7 imports,
remaining TODOs, `businessKey` uses, tests, converted models, and the findings that still need
follow-up. Record it in `MIGRATION_REPORT.md`.

### Step 5: AI Follow-up (offer after validation)

If any TODO, finding, compilation issue, deletion candidate, or unresolved item remains, then offer
to resolve it:

> I found [N] remaining items that need follow-up. Would you like me to take care of them?

Use AskUserQuestion with these options:

- **Yes, fix what you can (recommended)** — resolve the unambiguous items, and propose each one for
  review.
- **Show me the list first** — present the full list grouped by type, then ask which items to fix.
- **No, I will handle the rest manually** — stop, and record the remaining items in
  `MIGRATION_REPORT.md`.

#### Action 1: fix findings and TODOs

Work from the Step 4 verdict table. Never present the findings as one undifferentiated list.

| Verdict | Action |
|---|---|
| **needs fix** | Resolve one category at a time, using that category's cross-check guidance. |
| **needs review** | Collect the pending user decision through AskUserQuestion before any fix. |
| **no action** | Do not offer the category. |

- Apply an unambiguous fix directly, using the pattern catalog.
- Propose an ambiguous fix through AskUserQuestion. Skip whatever the user declines.
- Handle `form-data` and source-detected `formProperty` through `references/form-migration.md`:
  generate the drafts deterministically, and link only an accepted form.
- Handle the form-reference categories through `references/form-reference-migration.md`: present the
  inventory, and take one decision per integration group inside each category, grouping only owners
  that share an integration.
- After each batch, ask whether to commit, and update the verdict table in `MIGRATION_REPORT.md`.

#### Action 2: delete now-redundant code

The model/code cross-check flags Camunda 7 workaround code as a deletion candidate when a finding
reports that Zeebe now provides the capability natively. See "Now-redundant workaround code" in
`references/composing-code-and-models.md`.

Deleting code is never unambiguous. Even under "Yes, fix what you can", present every deletion
candidate through AskUserQuestion with its reasoning: the triggering finding, what the code did, and
why it is now redundant. Delete only on an explicit confirmation. Record the confirmed deletions and
the declined candidates in `MIGRATION_REPORT.md`.

## Exit Criteria

The migration is complete when every pass condition in Step 4 holds, and `MIGRATION_REPORT.md` holds
the complete inventories, the decisions, and the validation results.
