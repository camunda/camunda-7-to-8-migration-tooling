---
name: migrate-c7-to-c8-code
description: |-
  Migrates Camunda 7 / camunda-bpm projects to Camunda 8. Handles Java/Spring
  code (JavaDelegates, ExternalTaskWorkers, ProcessEngine/RuntimeService clients,
  execution/task listeners, IncidentHandler implementations, ProcessEnginePlugin
  registrations, and application config with camunda.* keys), Camunda 7 test
  inventories, BPMN/DMN models with the camunda: namespace, project documentation,
  and CI readiness.
  Use for code migration, model migration, or both.
license: Camunda License 1.0
---

# Camunda 7 to 8 Migration

Migrate a Camunda 7 project to Camunda 8. A project holds two independent kinds of assets:

- **Code** — Java/Spring glue and client code, config, tests. Migrated with a pattern-guided
  AI-first approach or OpenRewrite recipes plus AI cleanup.
- **Models** — BPMN/DMN diagrams in the `camunda:` namespace. Migrated with the Diagram Converter
  (deterministic) or agentically.

Every instruction here is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an
option is marked (MAY).

## Host interaction

Ask the user in the host's current conversation. Use a structured question tool only when the host
documents one.
Where the run is non-interactive, take each choice, including the Step 3 confirmation, only from an
explicit statement in the request, the conversation, or the execution configuration. A default, a
recommended option, or a tool-trust setting never counts as a choice by itself.
If a non-interactive run lacks a required choice, then stop before the action that needs it. Record
the missing choice as an open item in `MIGRATION_REPORT.md`. While the project root is unconfirmed,
report it only in the response and write no files.

## Step 0: Model preflight

This skill needs complex, multi-file reasoning. Before the scan, read the active model identifier or
capability metadata that the host exposes. Never infer it and never read undocumented variables.
Recommended examples: `claude-sonnet-*`, `claude-opus-*`, `gpt-5.6-luna`, `gpt-5.6-terra`,
`gpt-5.6-sol`. Caution examples: `gpt-5-mini`, `gpt-5.4-mini`, `gemini-3.7-flash`. These are routing
examples, not a benchmark and not a ranking. Prefer host capability metadata. (SHOULD) Treat an unknown identifier as unverified.

If the model is lightweight (mini, small, lite, flash, haiku, and similar) or unverified, then warn
the user and ask:

- **Switch to a model built for complex reasoning (recommended)** — explain any host model selector,
  wait for confirmation, then read the host model metadata again.
- **Continue** — use deterministic approaches and ask for extra human review.

Where the host permits a model change, repeat this check before AI-only migration, an agentic
rewrite, and AI cleanup. Once the user confirms the project root, record the preflight result, the
model identifier or its unverified status, and any decision to continue on a caution or unverified
model in `MIGRATION_REPORT.md`.

## Entry Criteria

1. The project declares Camunda 7 (camunda-bpm) dependencies in Maven or Gradle.
2. The project contains one or more of: JavaDelegate implementations, ExternalTaskWorkers,
   ProcessEngine/RuntimeService client code, execution/task listeners, implementations of
   `org.camunda.bpm.engine.impl.incident.IncidentHandler`, `ProcessEnginePlugin` registrations,
   BPMN/DMN files with the `camunda:` namespace, or application config with `camunda.*` keys.
3. The target is Camunda 8 version 8.8, 8.9, or 8.10.
4. Where the user selects OpenRewrite, require Maven or Gradle.
5. Where the user selects M1 or E1, require Java 21 or later for the Diagram Converter CLI.
   Do not apply the OpenRewrite upper bound to this phase.

## Implementation Steps

### Step 1: Gather Inputs

See `references/interview-questions.md` for the question set and the batching rules.

1. Detect the project root, the build tool (`pom.xml`, or `build.gradle` / `build.gradle.kts`), and
   the model files (`*.bpmn`, `*.bpmn20.xml`, `*.dmn`, `*.dmn11.xml`).
2. Ask Question 1 (project location).
3. If the confirmed root differs from the candidate, then scan the confirmed root again.
4. Ask Questions 2 and 3 (target version, scope) together.
5. Ask Questions 4 to 6, including Question 5a where it applies.
6. When the user accepts the defaults, continue without repeating Questions 1 to 6.
   After Step 2, ask Question 7 whenever its trigger applies, even if the user accepted those defaults.
   When Question 7 applies, do not continue to Step 3 until the user confirms every decision.

#### Shared rules

These rules apply to every later step and every reference.

**Assets and tools**

- Route each asset kind to the selected Part A or Part B approach.
- For code, use the selected Part A approach: OpenRewrite plus AI, AI only, or assessment only.
- For models, use the Diagram Converter in M1, M3, or E1, or agentic editing in M2.
- Only M2 edits BPMN/DMN agentically, and only on a converted copy.
- Never hand-edit BPMN or DMN in the code flow.
- Use project-local models first. While local models exist, never offer or request Camunda 7 engine
  access.
- Select the code approach with the tables in `references/code-migration-approaches.md`.
- Prefer the CLI over an agentic rewrite for models. (SHOULD)
- Use invocations that suit the current platform. Never assume one shell dialect.

**Safety**

- Before the first change, check for uncommitted changes. If the working tree is dirty, then ask the
  user to commit or stash.
- Never commit without an explicit user request.
- Write each converted model to a `converted-c8-*` copy. Leave every original file unchanged.
- Where the target is a separate location, such as a sibling Camunda 8 project, treat the Camunda 7
  project as read-only and copy the assets across.
- During Step 2, and throughout an assessment-only or analyze-only run, edit no project file other
  than `MIGRATION_REPORT.md`. A full migration also writes its validation scope under
  `.camunda-migration/validation/` in Step 2.
- Parse BPMN, DMN, and other XML with a namespace-aware parser. Never parse XML with regular
  expressions.
- Never write credentials, tokens, or private endpoint values to `MIGRATION_REPORT.md`,
  documentation, or CI logs. Replace each sensitive value with `<redacted>`.
- Before any edit, load the pattern catalog. See `references/pattern-catalog-sources.md`.
- Never guess an API mapping or an XML mapping.
- Never offer a feature from a version above the selected target.
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
- Keep `MIGRATION_REPORT.md` in the confirmed project root.
- Keep `MIGRATION_REPORT.md` current.
- Use `MIGRATION_REPORT.md` as the human-readable source of truth for inventories, decisions, open
  items, phase status, incompatibilities, and validation summaries.
- Store machine-readable check evidence under `.camunda-migration/validation/`.
- Keep decisions and open items in `MIGRATION_REPORT.md`, not in separate notes.
- Keep an open-items section in `MIGRATION_REPORT.md` for each design question the migration cannot
  answer.
- Name the call site in each open item.
- State the question in each open item.
- Set each open item to status `open`, `blocked`, or `resolved`.
- Create an open item for every migrated query against secondary storage, regardless of the running
  model. See `references/code-transform-checklist.md` for the mandatory triggers and the wording.

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

#### Java Runtime Selection

| Migration phase | Java requirement | Action |
|---|---|---|
| M1 or E1, Diagram Converter CLI | Java 21 or later. No upper bound applies. | Use the validated CLI runtime. |
| Approach A, OpenRewrite | Java 21-25 (`[21,26)`) or a narrower project range. | Use a separate code-phase runtime. |
| Approach B, M2, M3, or assessment-only | No Java requirement for that selected path. | Do not block it on Java. Check any separate M1 or E1 phase independently. |

Select Java separately for each migration phase. When code and models are in scope, select a runtime
for each Java-dependent phase. Do not use one Java decision for both phases.

Before each Java-dependent phase, validate its runtime:

1. Resolve the phase's Java executable to an absolute path. Start with `java` on `PATH`: run
   `command -v java` on macOS/Linux, `Get-Command java` in PowerShell, or `where java` in Windows
   Command Prompt.
2. Run `-version` on that executable and record its actual major version. If the probe fails or the
   output has no major version, then ask for another executable. Do not guess the Java version.
3. If Java is missing or outside the phase range, then ask for a JDK home that contains `bin/java`
   (Windows: `bin\java.exe`). Never install Java or change the user's system configuration.
4. When several compatible JDK homes exist, use the lowest compatible version for reproducible runs.
   (SHOULD)
5. Read the `java.home` property from the selected executable's `-XshowSettings:properties -version`
   output.
6. Use that property value as the candidate `JAVA_HOME`. Never derive it from the executable path.
7. Before setting the phase environment, run `<JAVA_HOME>/bin/java -version` and confirm that it
   reports the same major as the selected executable. On Windows, use `<JAVA_HOME>\bin\java.exe`.
8. If the property is missing or its `bin/java` is missing or reports a different major, ask for
   another JDK.
9. Set `JAVA_HOME` to the validated property value.
10. Set `PATH` to `<JAVA_HOME>/bin` followed by the existing `PATH`. On Windows, use
    `<JAVA_HOME>\bin`.
11. Apply both values only to that phase's process. Never edit shell profiles or global environment
    settings.
12. Record the applicable Java range, executable, actual major, and preflight result in
    `MIGRATION_REPORT.md`.

Do not run or require a full repository build to preflight M1 or E1. Invoke the released CLI directly.
If a repository or OpenRewrite build fails, record its phase and error separately.
Do not mark M1 or E1 blocked by that unrelated failure.
Record repository build failures separately from CLI execution results.
If a validated CLI command exits nonzero, record its arguments, exit code, stdout, and stderr.
Do not describe a converter execution failure as an incompatible JDK unless the Java launcher failed.

### Step 2: Assessment (always runs)

Scan the project and produce the inventories that the chosen scope needs.
When the user confirms a full migration, prepare the validation scope with
`references/validation-evidence.md` before conversion. Never prepare it for assessment-only or
analyze-only.

Where the confirmed root is a Git repository, record `git rev-parse HEAD` and the complete
`git status --porcelain` output in `MIGRATION_REPORT.md` as the change baseline.

#### Code Inventory

Classify every Camunda 7 related Java file and config file into a table with the columns File, Type,
Complexity, Notes. See `references/code-transform-checklist.md` for the detection hints and the type
classifications.

Record the original Java source baseline used for migration with the Code Inventory. Record each
class by its fully qualified class name, including its package and class name. Include every domain
or service class that could receive or delegate a `@JobWorker`, including classes without Camunda
APIs.

Classify each Camunda 7 test that drives a running engine with `references/test-migration.md` before
building the HTTP topology inventory. Exclude test-only Engine REST calls and test-owned servers
from the HTTP topology inventory and Question 7.

Search production code, application configuration, production build files, scripts, and
deployment configuration.
The skill excludes dependencies declared only in test scope and plugin executions bound only to
test phases from production-source evidence.
Check these production sources for Spring web servers, application HTTP endpoints, health checks,
and Camunda 7 Engine REST calls. When any of these production sources contains a match, inventory
its HTTP topology. Follow `references/http-topology-migration.md`. Ask Question 7 from
`references/interview-questions.md` before Step 3.

#### Test Inventory

When the skill reaches Step 2, it follows `references/test-migration.md` for the test inventory procedure.

#### Model Inventory

Glob for the model files. Record each one in a table with the columns File, Type, Uses `camunda:` ns,
Notes. Include every model that a test deploys or parses, as `references/test-migration.md` defines.
Record a CMMN model as manual redesign. Never offer it for BPMN/DMN conversion.

Then parse every original BPMN and inventory all three Camunda 7 form surfaces:

| Surface | Record |
|---|---|
| Generated Task Forms (`camunda:formData`, `camunda:formProperty`) | source file, process id, owning user task or start event, field count, business-key field, custom types and validators, initial status |
| Referenced forms (`camunda:formKey`, `camunda:formRef`) | the classification, and whether the referenced HTML or `.form` file exists in the project |
| Generic Task Forms (no form metadata at all) | every form-free owner affected |

See `references/form-reference-migration.md` for the classification rules and the full inventory
columns.

For each call activity, inventory its called process, input/output mappings (including delegates),
and child business-key intent. See `references/model-migration-approaches.md` for scope rules.

For each original BPMN, record every `camunda:executionListener event="start"` directly on a
`bpmn:startEvent`. Record its source path, event ID, and implementation even if the converter omits
its finding.

If the model inventory is empty and the user selected model migration, then record that no local
model was found and that E1 was offered.

#### Deployment and timer preflight

Run `references/deployment-and-timer-preflight.md` after the code and model inventories and after
acquiring more models. Repeat it against the final code and converted copies before any target
deployment or readiness claim.

#### Project Documentation and CI Inventory

Inspect project documentation and CI workflows in every assessment. Follow
`references/project-readiness.md`.

#### Custom incident notifications

Where the scope includes code migration, run the custom incident-notification gate in
`references/code-transform-checklist.md` item 9.

#### Summary

Present the code and model file counts. Present the overall complexity and the recommended code path.
State whether recipes help, hurt, or are neutral. Present project documentation dispositions and CI gaps.
Present the test counts, CPT eligibility, `Report only` findings, and Camunda 8.8 handling that
`references/test-migration.md` defines.
Present blockers that need a manual decision. Include the Step 0 preflight result and any user acknowledgment.
State that running instances, history, and audit data are out of scope. Point the user to the Data Migrator.
When the Test Inventory includes a test with handling **Migrate**, **Migrate to CPT**, or
**Migrate (lower priority)**, ask Question 8 in a separate prompt when its conditions in
`references/interview-questions.md` apply. Wait for the user's answer before Step 3.

Write the assessment to `MIGRATION_REPORT.md`. Ask the user to confirm before Step 3.

### Step 3: Execute Migration

When `test_run_mode` is `run`, follow `references/test-migration.md`. Complete the C7 baseline,
model migration, test migration, and test freeze before production-code migration.
When `test_run_mode` is `migrate_only`, preserve the C7 baseline with `references/test-migration.md`
before Step 3 changes any file.

When the user selects Models only and Analyze-only, run `Analyze-Only Mode` in
`references/model-migration-approaches.md` instead of Part B.
Exit after it presents findings. Do not run conversion, form follow-up, Step 4, or Step 5.

When models are in scope outside the Models-only Analyze-only path, run Part B.
When code is in scope, run Part A.
When code and models are in scope, see `references/composing-code-and-models.md`.

#### Part A - Code Migration

Apply the Transform checklist from `references/code-transform-checklist.md` with the approach chosen
in Question 4. See `references/code-migration-approaches.md` for all three.
The skill follows `references/test-migration.md` for every Test Inventory row that it migrates.
Before the skill transforms a C7 JavaDelegate, it runs the transaction and security gate in
`references/code-transform-checklist.md` item 3.

- **A. OpenRewrite + AI** — use recipes for repeated, supported syntax changes. Expect cleanup and
  source-to-output review.
- **B. AI only** — use a pattern-guided, AI-first migration for semantic, mixed, or complex code
  when a capable model is available.
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

When the skill pairs each original BPMN with its converted copy, run
`references/form-migration.md` for the Generated Task Forms, then
`references/form-reference-migration.md` for the referenced forms and the form-free owners.

#### Part C - Project Documentation and CI

When the user approves the migration plan, follow `references/project-readiness.md` for in-scope
documentation and CI gaps.

### Step 4: Validation (always runs)

Follow `references/validation-evidence.md` to record command results and audit required checks.
Never write a passing command result by hand. Run the gate only after a full migration.
Assessment-only and analyze-only runs do not claim readiness.
When `test_run_mode` is `run`, follow `references/test-migration.md` for test parity, freeze,
repeat-run, and coverage checks.

#### Code checks, when code was migrated

1. **Compile** — run `mvn compile` or the Gradle compile task. Fix every error.
2. **Camunda 7 dependencies** — inventory and classify every dependency with
   `references/code-transform-checklist.md` item 1 before its removal.
3. **Camunda 7 imports** — search `org.camunda.bpm` and classify each match. Replace imports that
   depend on Camunda 7 engine APIs. Keep imports required by a retained, compatible domain library.
   Record unresolved behavior as `blocked` with a manual follow-up in `MIGRATION_REPORT.md`.
4. **Migration TODOs** — search for `// TODO` comments that OpenRewrite inserted or that mark
   migration work. Review each matching TODO and resolve or record it.
5. **Legacy Camunda 8 client** — search `ZeebeClient` and `zeebe-client-java`. No reference remains.
   Use `CamundaClient`.
6. **Business keys** — search `businessKey`. Each use maps per the pattern catalog: businessId on
   8.9+, tags on 8.8. A key the process mutates stays a `businessKey` process variable.
7. **Configuration** — run the configuration validation in
   `references/code-transform-checklist.md`.
8. **Dependency compatibility and client startup** — run the runtime dependency validation in
   `references/code-transform-checklist.md` for each Maven module that uses a Camunda Spring Boot
   starter.
9. **Tests** — when the target is Camunda 8.9 or later, verify that every process test with handling
   `Migrate to CPT` and every remote-engine test with handling `Migrate (lower priority)` were
   migrated by following `references/test-migration.md`. When the target is Camunda 8.8, verify that
   each such process test and remote-engine test keeps `Report only` handling with the reason
   `test migration needs Camunda 8.9 or later`. Run `mvn test` or the Gradle test task and every
   independent suite in each module.
   Test each retained domain-library behavior for every supported type and downstream call path.
   Use synthetic fixture values, never production keys or credentials. Continue with other suites
   after a failure. Classify infrastructure failures separately from application failures. A
   successful compile alone does not prove that behavior works. A failed or blocked suite prevents
   readiness.
   When the user selects **Migrate tests only**, follow `references/test-migration.md` to compile
   test sources without running tests and record every skipped test check as blocked.
10. **Eventually-consistent queries** — search for every C8 search-request factory method listed in
    `references/code-transform-checklist.md`, not only the `SearchRequest` type name. Every migrated
    search call site has a matching open item in the `MIGRATION_REPORT.md` open-items section. A
    missing entry fails the check.
11. **Query counts and pagination** — run the count checks in "Query counts and pagination" in
    `references/code-transform-checklist.md`.
12. **Worker adapters** — run the worker-adapter check in `references/code-transform-checklist.md`
    item 7. A migrated Spring bean method must never receive `@JobWorker` directly. For each delegate
    adapter, check that `MIGRATION_REPORT.md` holds every record that the delegate gate in item 3
    requires, and that undecided gaps remain open.
13. **Deployment resources** — when `@Deployment` or `@TestDeployment` is present after migration,
    build the inventory from this run's converted copies and accepted forms. Create separate
    `resources` entries for each included type, allowing multiple entries per type.
    Where a test uses Spring `@Deployment`, resolve the actual entries with Spring's
    `PathMatchingResourcePatternResolver`. Require each entry to match a non-empty subset of one
    resource type in the inventory. Reject any match outside the inventory. Require each inventory
    resource to match exactly one entry. Confirm that the packaged application contains every match.
    Where a test uses CPT `@TestDeployment`, resolve each entry against the test classpath. Require
    each entry to resolve at least one resource. Require every resolved resource to match a converted
    copy or an accepted form in the inventory. Check method-level entries before class-level entries
    because a method-level annotation takes precedence. Never deploy an original model. A test that
    disables annotation deployment does not validate this wiring.
14. **Build wiring** — for each Maven module in the last row of the "Maven build wiring" table in
    `references/code-transform-checklist.md`, `mvn spring-boot:run` resolves the plugin and
    launches the entry point class. `java -jar` on the `mvn package` artifact launches the same
    class. Stop each started process after the launch. A test-only module gains no
    `@SpringBootApplication` class and no `spring-boot-maven-plugin` declaration. A successful
    compile does not validate the plugin. If startup fails after the launch only because no Camunda 8
    cluster is reachable, then record that blocker. Record each command and exit code in
    `MIGRATION_REPORT.md`.
15. **HTTP topology** — when the production-source inventory identifies a Spring web server,
    application HTTP endpoint, health check, or Camunda 7 Engine REST call, run the validation in
    `references/http-topology-migration.md`.
16. **SLF4J providers** — the skill runs the provider check in
    `references/code-transform-checklist.md` for every runtime module. The skill reports a complete
    migration only after a provider **PASS** or a user-approved exception resolves the finding.
17. **Notification parity** — where a custom incident-notification finding exists, the skill verifies
    it with `references/code-transform-checklist.md` item 9.

#### Model checks, when models were migrated

Run every check in "Model validation" in `references/model-migration-approaches.md`.

#### Process behavior

For every executable process, run the applicable assertions in `references/validation-evidence.md`.
Record justified non-applicability in `MIGRATION_REPORT.md`.
Every executable process has a test that starts it directly, with the normal inputs and without each
input that a worker may not receive. Coverage through a call activity does not count, because the
parent can supply variables that a direct start lacks. If a process is not a valid standalone entry
point, then `MIGRATION_REPORT.md` records the process ID, the reason, and the covering test. A
process with neither fails validation. For each failing scenario, record the process ID, inputs,
failing element, job type, and incident message.

#### Project readiness checks, when code or models were migrated

Run the checks in `references/project-readiness.md`.

#### Summary

Present a validation summary that states the status of compilation, configuration binding,
remaining Camunda 7 imports, remaining migration TODOs, `businessKey` uses, the open items, tests,
converted models, and the findings that still need follow-up. Include the `Notification parity`
rows that `references/code-transform-checklist.md` item 9 requires. Run the evidence validator
and use its generated gate block as the validation-readiness summary in `MIGRATION_REPORT.md`.
Record project documentation, CI readiness, and the project-readiness verdict separately.
State test verification as `verified`, `not verified (Migrate tests only)`, or `blocked` with its
reason.

### Step 5: AI Follow-up (offer after validation)

If any migration TODO, finding, compilation issue, deletion candidate, or unresolved item remains, then offer
to resolve it:

> I found [N] remaining items that need follow-up. Would you like me to take care of them?

Offer these options:

- **Yes, fix what you can (recommended)** — resolve the unambiguous items, and propose each one for
  review.
- **Show me the list first** — present the full list grouped by type, then ask which items to fix.
- **No, I will handle the rest manually** — stop, and record the remaining items in
  `MIGRATION_REPORT.md`.

#### Action 1: fix findings and migration TODOs

When processing model categories, use the verdict table in `model-migration-approaches.md` step 5d.
For each M1 **needs fix** or **needs review** row, load its complete list from the Element list
reference. Use the grouped summary only to select a category.
Process the rows in the step 5d order: resolve all **Blocking** rows before **Advisory** rows. Do not
use severity as a substitute for runtime impact. Apply each row's verdict action from step 5d, and do
not offer a **no action** row.

- Apply an unambiguous fix directly, using the pattern catalog.
- Propose an ambiguous fix to the user. Skip whatever the user declines.
- Handle `form-data` and source-detected `formProperty` through `references/form-migration.md`.
- Handle the form-reference categories through `references/form-reference-migration.md`.
- When the source inventory or M1 findings report identifies a start listener, use the relocation
  procedure in `references/model-migration-approaches.md` step 5d.3. Never relocate this listener
  automatically.
- When a Code + models run has Diagram Converter findings, offer a dispatcher scaffold for each many-to-one
  job-type group with a **needs review** verdict and no dispatcher. Use the procedure in
  `references/composing-code-and-models.md`.
- After each batch, ask whether to commit.
- For a model-finding batch, run the verification gate in `references/model-migration-approaches.md`
  before updating the verdict table in `MIGRATION_REPORT.md`.

#### Action 2: delete now-redundant code

The model/code cross-check flags Camunda 7 workaround code as a deletion candidate when a finding
reports that Zeebe now provides the capability natively. See "Now-redundant workaround code" in
`references/composing-code-and-models.md`.

Deleting code is never unambiguous. Even under "Yes, fix what you can", present every deletion
candidate to the user with its reasoning: the triggering finding, what the code did, and
why it is now redundant. Delete only on an explicit confirmation. Record the confirmed deletions and
the declined candidates in `MIGRATION_REPORT.md`.

## Exit Criteria

When reporting a full migration as complete, require every pass condition in Step 4, a `READY`
evidence-gate result, and a `ready` project-readiness verdict.
Also require no unresolved migration TODO, finding, compilation issue, deletion candidate, or
project-readiness blocker.
Keep complete inventories, decisions, open items, and validation results in `MIGRATION_REPORT.md`.
No item can have `deferred` or `blocked` status.
An open item is a team decision. If an open item prevents an in-scope documentation change or a
required readiness check, then it blocks completion. If it does not prevent either, then it does not
block completion. The summary always lists every open item.
Unresolved deployment/timer preflight findings are `blocked` readiness checks, not non-blocking
`open` team decisions.
If a full migration fails any completion condition, then the skill reports it as incomplete and
records the follow-up work.
Where the root is confirmed, follow `references/final-change-summary.md` before the final response.
