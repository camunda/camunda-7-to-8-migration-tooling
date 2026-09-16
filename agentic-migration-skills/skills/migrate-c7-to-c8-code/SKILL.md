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
examples, not a benchmark and not a ranking. Prefer host capability metadata. (SHOULD) Treat an unknown identifier as unverified.

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
2. The project contains one or more of: JavaDelegate implementations, ExternalTaskWorkers,
   ProcessEngine/RuntimeService client code, execution/task listeners, BPMN/DMN files with the
   `camunda:` namespace, or application config with `camunda.*` keys.
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

- Route each asset kind to the selected Part A or Part B approach.
- For code, use the selected Part A approach: OpenRewrite plus AI, AI only, or assessment only.
- For models, use the Diagram Converter in M1, M3, or E1, or agentic editing in M2.
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
- Converter annotations are temporary review metadata. Once every category has a verdict and
  recorded category verification evidence, strip `conversion:*` elements and attributes from the
  converted copies with namespace-aware XML tooling.
- A category verdict is provisional until every participating converted copy recorded for this run
  passes the verification gate in Step 5.
- Keep `MIGRATION_REPORT.md` in the confirmed project root.
- Keep `MIGRATION_REPORT.md` current.
- Use `MIGRATION_REPORT.md` as the single source of truth for inventories, decisions, open items,
  phase status, incompatibilities, and validation results.
- Never scatter this record across separate notes.
- Keep an open-items section in `MIGRATION_REPORT.md` for each design question the migration cannot
  answer.
- Name the call site in each open item.
- State the question in each open item.
- Set each open item to status `open` or `resolved`.
- Create an open item for every migrated query against secondary storage, regardless of the running
  model.
- See `references/code-transform-checklist.md` for the mandatory triggers and the wording.

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

Record the original Java source baseline used for migration with the Code Inventory. Record each
class by its fully qualified class name, including its package and class name. Include every domain
or service class that could receive or delegate a `@JobWorker`, including classes without Camunda
APIs.

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
Capture the immutable converted-copy, form, and code baselines before these form procedures run.
Record `absent` for a form that the procedure will create.
### Step 4: Validation (always runs)

Each item below is a check to run and a condition that must hold at exit. Record every result in
`MIGRATION_REPORT.md`.

#### Code checks, when code was migrated

1. **Compile** — run `mvn compile` or the Gradle compile task. Fix every error.
2. **Camunda 7 dependencies** — no dependency with groupId `org.camunda.bpm` remains in the build files. No dependency with a groupId that starts with `org.camunda.bpm.` remains either.
3. **Camunda 7 imports** — search `org.camunda.bpm`. No import remains. Each one is a missed
   migration.
4. **Migration TODOs** — search for `// TODO` comments that OpenRewrite inserted or that mark
   migration work. Review each matching TODO and resolve or record it.
5. **Legacy Camunda 8 client** — search `ZeebeClient` and `zeebe-client-java`. No reference remains.
   Use `CamundaClient`.
6. **Business keys** — search `businessKey`. Each use maps per the pattern catalog: businessId on
   8.9+, tags on 8.8. A key the process mutates stays a `businessKey` process variable.
7. **Configuration** — `camunda.client.*` keys replace the `camunda.*` keys in
   `application.properties` or `.yaml`.
8. **Tests** — run `mvn test` or the Gradle test task. Every test passes, or each failure is
   documented with an explanation.
9. **Eventually-consistent queries** — search for every C8 search-request factory method listed in
   `references/code-transform-checklist.md`, not only the `SearchRequest` type name. Every migrated
   search call site has a matching open item in the `MIGRATION_REPORT.md` open-items section. A
   missing entry fails the check. See the mandatory open items in
   `references/code-transform-checklist.md`.
10. **Worker adapters** — compare every `@JobWorker` declaration's fully qualified declaring class
    name with the original Java source baseline recorded in Step 2. Flag the declaration when its
    class appears in that baseline, even when the class name ends with `Worker`. Accept it only
    when the class is absent from the baseline, is a new `*Worker` adapter component, and delegates
    to the baseline bean. Record each flagged declaration and its replacement adapter in
    `MIGRATION_REPORT.md`. A migrated Spring bean method must never receive `@JobWorker` directly.

Check these pitfalls as well:

- Naming swap: Camunda 7 `processDefinitionKey` (a string key) becomes Camunda 8 `bpmnProcessId`, and
  Camunda 7 `processDefinitionId` (a UUID) becomes Camunda 8 `processDefinitionKey`. Decision
  definitions swap the same way.
- Camunda 7 `processInstanceId` is a `String`. Camunda 8 `processInstanceKey` is a `Long`. Update declarations and call sites, not only the names.
- Variables are plain JSON and the `TypedValue` API is gone, so every `VariableMap` use changes.
- Batch operations exist since 8.8. Only a custom batch handler needs a manual design.

#### Model checks, when models were migrated

After every manual BPMN edit, lint the converted copy with the Camunda compatibility ruleset for the
target version. See the linting section in `references/model-migration-approaches.md`.

1. A `converted-c8-*` file exists for every in-scope diagram, unless the run is analyze-only.
2. Every original file is intact and was never overwritten.
3. Treat every resource directory that the build configures for inclusion in a Maven or Gradle
   application artifact as a packaged resource directory. Include `src/main/resources` when it
   exists. No findings report named `analysis-results.<ext>` or `analysis-results (n).<ext>` remains
   under a packaged resource directory, where `n` is a positive integer and `<ext>` is `.csv`,
   `.json`, `.md`, or `.xlsx`. Keep findings reports under `.camunda-migration/reports/` only when
   the build does not package that directory. Otherwise, use another explicitly non-packaged
   directory.
4. Every WARNING, TASK, REVIEW, and INFO finding is fixed, or classified in the per-category verdict table
   with its category, count, cross-referenced code artifact, link, verdict, and verification state. See
   `references/model-migration-approaches.md` step 5d. A flat "fixed or recorded" note is not enough.
   Mark a category **no action** only after its Step 5 verification row records a passing result,
   including when no manual edit was needed.
5. Every source Generated Task Form is `accepted`, `blocked`, or `declined`, including a
   form-property-only definition. None is silently omitted.
6. Every accepted form is a standard Camunda 8 `.form`.
7. Every accepted form parses.
8. Where a target-compatible official schema exists, the skill validates every accepted form with it.
9. Where target-compatible form-js tooling exists, the skill imports or renders every accepted form
   with it.
10. Every accepted form has a planned matching `zeebe:formDefinition`.
    Step 5 verifies the actual linkage after form remediation and annotation cleanup.
11. Step 5 deploys every accepted form with its BPMN after form remediation and annotation cleanup.
12. No draft, blocked, or declined form is linked or deployed. Every semantic gap and every user
   decision is recorded.
13. Every referenced form and every form-free owner has a recorded per-category decision and a final
   status of `kept`, `relinked`, `accepted`, `declined`, `deferred`, or `blocked`. The in-progress
   statuses `pending` and `draft` must not remain. A `deferred` or `blocked` item stays open follow-up
   work. A kept external reference is never reported as a completed migration, and no category is
   closed as **no action** because the converter copied a reference.
14. Every relinked or rebuilt form is referenced by `zeebe:formDefinition@formId` with a recorded
   binding decision: `bindingType` written for `deployment` and `versionTag`, or `latest` left
   deliberately to the Camunda 8 default. The copied Camunda 7 `externalReference` or `formKey` is
   gone from that element.
15. After Step 5e and the final whole-file cleanup, the converted copies hold no `conversion:*`
   node, no `conversion:*` attribute, no unused Camunda 7 or conversion namespace declaration,
   and no leftover BPMN definitions-level XPath `expressionLanguage` attribute.
16. When the model uses M2, inspect every `zeebe:taskDefinition/@type`. Derive the expected type
    from the original `camunda:delegateExpression`, `camunda:expression`, `camunda:class`, or
    `camunda:topic` attribute using the binding rules in
    `references/model-migration-approaches.md`. If the emitted type differs, require a confirmed
    decision-log entry in `MIGRATION_REPORT.md` with the source file and element, original
    implementation, emitted type, and rationale. Treat a mismatch without that entry as a
    validation failure.
17. When any migration approach creates or imports a converted copy, verify the Modeler namespace
    `executionPlatformVersion` attribute against the selected target in canonical patch-zero form,
    such as `8.10.0` for target `8.10`. Verify the Modeler namespace `executionPlatform` attribute
    as `Camunda Cloud`. When M2 creates the copy, set both fields before verification. If either
    target metadata field is missing or mismatched, record `metadata unavailable` or
    `metadata mismatch` as a run-level validation failure in `MIGRATION_REPORT.md`. Keep the
    migration incomplete until both fields are corrected, even when no finding category maps to
    the converted copy. Do not treat converter output as passing evidence or resolve a category
    based on it.

#### Summary

Present a validation summary that states the status of compilation, remaining Camunda 7 imports,
remaining migration TODOs, `businessKey` uses, the open items, tests, converted models, and the
findings that still need follow-up. Record it in `MIGRATION_REPORT.md`.

### Step 5: AI Follow-up (offer after validation)

#### Verification before resolving a category

Run one verification pass for every category, including INFO and no-edit categories, before changing
its verdict to **no action**. Include every category in both the findings inventory and verification
table. Use provisional **needs review** for INFO categories until their verification pass succeeds.
Do not request a human decision for this provisional INFO verdict.
The shared gate overrides procedure-specific rules that otherwise move a category to **no action**.
Those rules make a category eligible for the gate, but they do not replace it.
Run it after a category fix and on every converted copy participating in the category when no
manual edit was needed. Record before-and-after evidence in `MIGRATION_REPORT.md`. Do not start an
automatic fix loop.
Before each remediation batch, capture an immutable baseline for every participating converted copy,
`.form` resource, and referenced code artifact. Immediately before verifying a no-edit category,
capture the same baseline. For XML and code, use a lowercase SHA-256 digest of exact UTF-8 bytes
without normalization. Include namespace counts, wiring references, and FEEL state.
For forms, use a lowercase SHA-256 digest of the exact generated JSON bytes without normalization.
Also record the schema result, render result, linkage, and deployment state. Record `absent` when
a remediation will create a new form. Mark the schema, render, linkage,
and deployment checks `not applicable` in that absent `Before` state. Record `not applicable` when
no form resource participates. For code, include content hashes and matched worker, listener,
dispatcher, and precompute declarations. Record `absent` when a remediation will create a code
artifact, and mark its declaration checks `not applicable` in that absent `Before` state. Use the
baseline for `Before` evidence.
Do not reconstruct it from the original Camunda 7 model. Run this gate when a converted copy
participates in verification. Do not resolve a category verdict in an analyze-only run that creates
no converted copies. Keep category verdicts provisional in that mode.
Record a verification state for every category. Use `pending` before the gate, `passed` after every
applicable check passes, `failed` after a check fails, and `unavailable` when a required
deterministic tool is unavailable. The supplementary converter check is not required for the
aggregate category state in M1, M2, M3, or E1. An unavailable target FEEL parser is also
supplementary.
Record each applicable limitation only in its check evidence. An unavailable converter or FEEL
parser is non-blocking. An executed converter comparison failure is blocking. Set the category to
`passed` when all other required checks and the finding-specific postcondition pass. For a participating BPMN or DMN copy,
record `not applicable` in converter applicability evidence only for the expected already-converted
exception. For a form-only category with no BPMN or DMN copy, record converter checks as
`not applicable`. Allow other check rows to record
`not applicable` for their explicitly
defined cases, such as out-of-scope code coverage or no referenced wiring. This converter-specific
restriction does not apply to those other check rows. A category with any state other than
`passed` cannot receive the **no action** verdict.

Apply XML, namespace, and converter checks only to BPMN or DMN converted copies. Apply the
generated-form check to `.form` resources. Record `not applicable` for XML-only and converter
checks when a category contains only generated forms.
Run category-specific checks before Step 5e strips converter annotations. Run Step 5e after every
category has a verdict and verification evidence. Run form procedures in 5f and 5g only for
unresolved form remediation after the Step 3 execution. Do not repeat an accepted Step 3 form
procedure. Rerun the form verification row after any form remediation, linkage, or deployment
change. Run model validation and final whole-file cleanup after those form checks and all 5f/5g
changes. Retain **no action** only after this sequence passes.

| Check | Required evidence |
|---|---|
| XML structure | For every participating BPMN or DMN converted copy, re-parse the file with a namespace-aware XML parser, including files with no manual edit. For M1, use paths captured from this run's `Created ...` lines. For M2, M3, and E1, use the recorded original-to-converted pair paths. Record the command, exit code, and paths. |
| Generated form structure | Before Step 5e, parse every participating `.form` resource and apply its schema and render checks from `form-migration.md`. Parse every FEEL-bearing form expression with the target FEEL parser when available. Record an unavailable parser in FEEL evidence and continue other required checks. A failed parse remains a verification failure. Record `not applicable` and the reason when target-compatible schema or render tooling is unavailable, as defined by `form-migration.md`. After Step 5e, verify form linkage and deployment. For a standalone `.form` with no owner or converted BPMN, record linkage and deployment as `not applicable` with the reason. Compare the results with the immutable form baseline. Compare `absent` with the created resource when a remediation creates a form. Record before-and-after evidence, the command, exit code, and resource paths. |
| Namespace and metadata cleanup | For BPMN or DMN converted copies, use namespace-aware XML queries by namespace URI, not literal prefixes. Count remaining Camunda 7 elements or attributes, conversion nodes or attributes, and QName-valued attribute values resolved to those namespace URIs. Record before-and-after counts. Require zero remaining Camunda 7 elements, attributes, or QName-valued attribute values for the category's touched elements before changing its verdict to **no action**. |
| Final whole-file cleanup | After category-specific verification runs for every category and Step 5e removes converter annotations, inspect the entire BPMN or DMN converted copy before retaining any **no action** verdict. Require and record zero remaining Camunda 7 elements, attributes, and QName-valued attribute values. Require and record zero conversion nodes or attributes, zero unused Camunda 7 or conversion namespace declarations, and zero leftover BPMN definitions-level XPath `expressionLanguage`. |
| Final cleanup failure | If any final count is non-zero, invalidate every passed row for that file, update every invalidated row in both tables to **needs fix** or **needs review**, record a run-level validation failure when no category maps to the leftover, and keep the migration incomplete until final revalidation passes. |
| Referenced conversion wiring | When code is in scope, inspect every task wiring, listener, header, dispatcher, and DMN/precompute reference in each participating converted copy. This row supersedes trust-only guidance for runtime and code coverage. Confirm matching declarations and code coverage, even when the converter emitted no wiring finding. Converter output remains authoritative for its transformation. When no wiring reference exists, record the row as `not applicable`. When code is out of scope, confirm matching XML declarations and record code coverage as `not applicable`. |
| Finding-specific postcondition | Define a deterministic postcondition from the category's cross-check and record the expected finding-specific evidence. A valid XML, namespace, or converter check does not replace this condition. If no deterministic postcondition exists, set the verification state to `failed`, keep the category at **needs review** or **needs fix**, and route it through the explicit escalation below. Do not set its verification state to `passed`. |
| FEEL syntax | Parse every resulting FEEL expression in every participating converted copy with the target FEEL parser when one is available. Record the parser, expression location, and result, or record `none present`. If no parser is available, record `unavailable` in the FEEL evidence and continue the other required checks. Treat this limitation as non-blocking for the aggregate category state. Keep the category at **needs fix** when parsing fails. |
| Converter regression command | Where the local CLI, Java executable, and converter JAR support a participating BPMN or DMN converted copy, normalize the recorded path to an absolute path before invoking `"<java-cmd>" -Dfile.encoding=UTF-8 -jar "<jar>" local "<file>" --platform-version "<target>" --check --csv`. Run the command once per unique converted copy for each unchanged file state. On Windows PowerShell, prefix the command with the call operator: `& "<java-cmd>" ...`. Reuse the captured command result only while the file and target metadata are unchanged. Rerun the command after any remediation edit and during final validation. |
| Converter regression comparison | Parse each captured CSV with the converter's semicolon delimiter. Filter the parsed result for each category. Capture relevant rows and compare them with the immutable pre-remediation findings evidence or the expected result. Record `none` when no relevant rows exist. Record the comparison as supplementary evidence. If the comparison finds a new or remaining relevant row, set the verification state to `failed` and keep the category at **needs fix** or **needs review**. The unavailable-CLI exception does not apply to a failed comparison. Do not use CSV rows as findings input or as the sole pass/fail criterion. |
| Converter applicability | For a participating BPMN or DMN converted copy, query `executionPlatform` and `executionPlatformVersion` on the document's BPMN or DMN `definitions` element. Resolve both attributes by the Modeler namespace URI `http://camunda.org/schema/modeler/1.0` and local names. Compare `executionPlatform` exactly with `Camunda Cloud` and the version exactly with the selected target in canonical patch-zero form, such as `8.10.0` for target `8.10`. Apply this check before the converter failure rule for M1, M2, M3, and E1. Set converter applicability to `not applicable` when a category has no participating BPMN or DMN copy. |
| Already-converted exception | Record `not applicable` only when a participating BPMN or DMN converted copy has both exact metadata values and the CLI reports `This diagram is already a Camunda 8 diagram`. Do not treat that expected rejection exit code as a failure. Treat any other non-zero result, parse failure, or empty result without that message as failed evidence. |
| Converter metadata failure | Record missing or mismatched metadata as a run-level validation failure. When a category maps to the file, keep that category at **needs review** or **needs fix**. Do not treat CLI output as applicable passing evidence. |
| Converter unavailable | For M1, M2, M3, or E1, record `unavailable` in the converter check evidence when the local CLI, Java executable, or converter JAR is unavailable. Treat this supplementary check as non-blocking for the aggregate category state. Set the category to `passed` only after every other required check and the finding-specific postcondition pass. |
| Converter failure | After the applicability row classifies an expected already-converted rejection as `not applicable`, do not treat its non-zero exit code as a failure. For all other files, treat any reported parse failure as a failed verification even when the CLI exits `0` or writes an empty CSV. Treat any other non-zero exit code or CSV-generation failure as a failed verification and keep the category at **needs fix** or **needs review**. |
| Converter evidence | Capture the command, exit code, parse failures, and CLI's `Created ...` CSV path for each command run. Before continuing or exiting, move every fresh CSV to the chosen explicitly non-packaged reports directory. Record the final evidence path after relocation, `removed` after cleanup deletes the CSV, or `not created` when the command produces no CSV. |
| Findings source | Do not use CSV rows as findings input or as the sole pass/fail criterion. Use JSON for findings input in M1, M3, and E1. For M2, use the structured direct-rewrite findings summary and do not consume an unrelated JSON report. |

Keep the per-category findings inventory from `references/model-migration-approaches.md` step 5d
with its `Category`, `Count`, `Cross-referenced code artifact`, `Link`, `Verdict`, and
`Verification` columns. Use `pending`, `passed`, `failed`, or `unavailable` in the `Verification`
column. Record permitted `not applicable` checks in the evidence.
Add a separate verification table with one row per category and these columns: `Category`,
`Participating files`, `Before`, `Postcondition`, `Checks and evidence`, `After`, `Verdict`, and
`Verification`. Do not replace the findings inventory with the verification table.

The CLI can filter parse failures before its visitor pipeline and can write an empty CSV. Therefore,
the gate treats every reported parse failure as failed evidence. The CLI does not reconstruct the
original Camunda 7 mapping from an already-converted copy. It does not prove that a job
worker, listener, header, or FEEL expression has the intended runtime semantics. The namespace-aware
checks and code cross-checks above provide that coverage.

Record failures with their before-and-after values using this decision table:

| Failure condition | Verification | Verdict | Next action |
|---|---|---|---|
| Concrete remediation remains | `failed` | **needs fix** | Resolve the category after the required decision. |
| A design decision or required deterministic check is unavailable | `failed` or `unavailable` | **needs review** | Ask for a new decision before another attempt. |
| Supplementary converter or FEEL tooling is unavailable | Evidence-only `unavailable` | Keep the current verdict | Continue the other required checks. |

Do not mark a category **no action** after a failed blocking check. Escalate after the single
verification pass when the failure needs a new design or a second remediation attempt. Update the
nonterminal verdict in both the findings inventory and verification table.
After any remediation batch edits a converted copy, `.form` resource, or referenced code artifact,
invalidate every earlier `passed` verification row whose recorded file or code artifact changed and
rerun every invalidated category. Before exit, run the
final whole-file cleanup check for every participating file. Rerun the supplementary converter
command for each changed BPMN or DMN copy during final validation. Do not rerun category-specific
postconditions in the final whole-file check. Retain **no action** only for rows that passed their
category verification and the final whole-file cleanup.

If the run is a model analyze-only run, present the findings, inventories, and provisional verdicts,
update `MIGRATION_REPORT.md`, and stop before the model-finding remediation offer. Do not offer model
remediation for a run that created no converted copies. Code-only follow-up remains eligible for
the generic offer.
If a category has failed or unavailable verification, use AskUserQuestion before another
remediation attempt. Present the category, failure evidence, and the recorded postcondition.
Ask whether to retry with a new remediation plan or leave the category unresolved. Record the
answer in `MIGRATION_REPORT.md`. Do not retry automatically. Do not include the category in the
generic follow-up offer until the user decides.
Exclude an INFO category with provisional **needs review** and a category whose only pending action
is the shared verification pass from this offer until its verification pass completes. Exclude a
category with a failed or unavailable verification from this offer. Require a new explicit user
decision before another remediation attempt for that category. If any other migration TODO, finding,
compilation issue, deletion candidate, or unresolved item remains, then offer to resolve it:

> I found [N] remaining items that need follow-up. Would you like me to take care of them?

Use AskUserQuestion with these options:

- **Yes, fix what you can (recommended)** — resolve the unambiguous items, and propose each one for
  review.
- **Show me the list first** — present the full list grouped by type, then ask which items to fix.
- **No, I will handle the rest manually** — stop, and record the remaining items in
  `MIGRATION_REPORT.md`.

#### Action 1: fix findings and migration TODOs

For model findings, work from the Step 4 verdict table. Never present model findings as one
undifferentiated list.

| Verdict | Verification | Outstanding action | Action |
|---|---|---|---|
| **needs fix** | `failed` or `unavailable` | Retry or redesign is required | Use AskUserQuestion for a new decision. Do not retry automatically. |
| **needs fix** | `pending` or `passed` | Concrete remediation remains | Resolve one category at a time, using that category's cross-check guidance. |
| **needs review** | `pending` and verification is the only outstanding action | No user or design decision remains | Run the verification gate. Do not ask for a user decision. |
| **needs review** | Any other state | A user or design decision remains | Collect the pending user decision through AskUserQuestion before any fix. |
| **no action** | `passed` | None | Do not offer the category. |

- Apply an unambiguous fix directly, using the pattern catalog.
- Propose an ambiguous fix through AskUserQuestion. Skip whatever the user declines.
- Handle `form-data` and source-detected `formProperty` through `references/form-migration.md`:
  generate the drafts deterministically, and link only an accepted form.
- Handle the form-reference categories through `references/form-reference-migration.md`: present the
  inventory, and take one decision per integration group inside each category, grouping only owners
  that share an integration.
- After each batch, ask whether to commit.
- For a model-finding batch, update the verdict table in `MIGRATION_REPORT.md`.
- After each model-finding batch, run the verification pass before changing its verdict to
  **no action**. Record the before-and-after evidence and keep a failed category at **needs fix**
  or **needs review** in `MIGRATION_REPORT.md`.

#### Action 2: delete now-redundant code

The model/code cross-check flags Camunda 7 workaround code as a deletion candidate when a finding
reports that Zeebe now provides the capability natively. See "Now-redundant workaround code" in
`references/composing-code-and-models.md`.

Deleting code is never unambiguous. Even under "Yes, fix what you can", present every deletion
candidate through AskUserQuestion with its reasoning: the triggering finding, what the code did, and
why it is now redundant. Delete only on an explicit confirmation. Record the confirmed deletions and
the declined candidates in `MIGRATION_REPORT.md`.

## Exit Criteria

The migration run may exit only when every pass condition in Step 4 holds, every category marked
**no action** has a passing verification row in the Step 5 verification table, and
`MIGRATION_REPORT.md` holds the complete inventories, decisions, open items, and validation results.
The skill reports a complete migration only when no unresolved migration TODO, finding, compilation
issue, or deletion candidate remains and no item has `deferred` or `blocked` status.
An open item is a team decision, so an `open` status does not block completion, but the summary
always lists every open item.
Otherwise, the skill reports the migration as incomplete and records the follow-up work.
