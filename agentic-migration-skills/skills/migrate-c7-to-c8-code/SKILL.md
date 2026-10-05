---
name: migrate-c7-to-c8-code
description: |-
  Migrates Camunda 7 / camunda-bpm projects to Camunda 8. Handles Java/Spring
  code (JavaDelegates, ExternalTaskWorkers, ProcessEngine/RuntimeService clients,
  execution/task listeners, IncidentHandler implementations, ProcessEnginePlugin
  registrations, and application config with camunda.* keys). Handles BPMN/DMN
  models with the camunda: namespace, project documentation, and CI readiness.
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

These rules apply to every later step.

**Assets and tools**

- Route each asset kind to the selected Part A or Part B approach.
- For code, use the selected Part A approach: OpenRewrite plus AI, AI only, or assessment only.
- For models, use the Diagram Converter in M1, M3, or E1, or agentic editing in M2.
- Only M2 edits BPMN/DMN agentically, and only on a converted copy.
- Never hand-edit BPMN or DMN in the code flow.
- Use project-local models first. While local models exist, never offer or request Camunda 7 engine
  access.
- When a capable model can examine the source, prefer an AI-first, pattern-guided code migration.
  (SHOULD)
- When code repeats supported syntactic transformations, use OpenRewrite plus AI.
- When the team needs a deterministic first diff, use OpenRewrite plus AI.
- Where a recipe run includes semantic or mixed delegate/client code, review its output with AI.
- Compare recipe output with the original source before accepting it.
- Recipes do not decide domain behavior, eventual consistency, transaction boundaries, or
  architecture.
- Review these decisions in both code paths.
- Prefer the CLI over an agentic rewrite for models. (SHOULD)
- Use invocations that suit the current platform. Never assume one shell dialect.

**Safety**

- Before the first change, check for uncommitted changes. If the working tree is dirty, then ask the
  user to commit or stash.
- Before a target deployment or a readiness claim, repeat
  `references/deployment-and-timer-preflight.md` against the final code and converted copies.
- Never commit without an explicit user request.
- Write each converted model to a `converted-c8-*` copy. Leave every original file unchanged.
- Where the target is a separate location, such as a sibling Camunda 8 project, treat the Camunda 7
  project as read-only and copy the assets across.
- Before any edit, load the pattern catalog. See `references/pattern-catalog-sources.md`.
- Never guess an API mapping or an XML mapping.
- Never offer a feature from a version above the selected target.
- Select Java separately for each migration phase.
- Before each Java-dependent phase, resolve its Java executable to an absolute path.
- Run `-version` on that executable and record its actual major version.
- Read the `java.home` property from the selected executable's `-XshowSettings:properties -version` output.
- Use that property value as the candidate `JAVA_HOME`. Never derive it from the executable path.
- Before setting the phase environment, run `<JAVA_HOME>/bin/java -version` and confirm that it
  reports the same major as the selected executable. On Windows, use `<JAVA_HOME>\bin\java.exe`.
- If the property is missing or its `bin/java` is missing or reports a different major, ask for another JDK.
- Set `JAVA_HOME` to the validated property value.
- Set `PATH` to `<JAVA_HOME>/bin` followed by the existing `PATH`. On Windows, use
  `<JAVA_HOME>\bin`.
- Apply both values only to that phase's process. Never edit shell profiles or global environment
  settings.
- Apply a mapping unasked only when it is an unambiguous 1:1 mapping.
- Ask before changing a high-complexity file or an edge case.

#### Java Runtime Selection

| Migration phase | Java requirement | Action |
|---|---|---|
| M1 or E1, Diagram Converter CLI | Java 21 or later. No upper bound applies. | Use the validated CLI runtime. |
| Approach A, OpenRewrite | Java 21-25 (`[21,26)`) or a narrower project range. | Use a separate code-phase runtime. |
| Approach B, M2, M3, or assessment-only | No Java requirement for that selected path. | Do not block it on Java. Check any separate M1 or E1 phase independently. |

For Code + models, select a runtime for each Java-dependent phase. Do not use one Java decision for
both phases.
Do not run or require a full repository build to preflight M1 or E1. Invoke the released CLI directly.
If a repository or OpenRewrite build fails, record its phase and error separately.
Do not mark M1 or E1 blocked by that unrelated failure.
If a validated CLI command exits nonzero, record its arguments, exit code, stdout, and stderr.
Do not describe a converter execution failure as an incompatible JDK unless the Java launcher failed.

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
- Keep `MIGRATION_REPORT.md` in the confirmed project root.
- Keep `MIGRATION_REPORT.md` current.
- Use `MIGRATION_REPORT.md` as the human-readable source of truth for inventories, decisions, open
  items, phase status, incompatibilities, and validation summaries.
- Record the applicable Java range, executable, actual major, and preflight result for each
  Java-dependent phase. Record repository build failures separately from CLI execution results.
- Store machine-readable check evidence under `.camunda-migration/validation/`.
- Keep decisions and open items in `MIGRATION_REPORT.md`, not in separate notes.
- Keep an open-items section in `MIGRATION_REPORT.md` for each design question the migration cannot
  answer.
- Name the call site in each open item.
- State the question in each open item.
- Set each open item to status `open`, `blocked`, or `resolved`.
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
When the user confirms a full migration, save the confirmed module paths and original model paths
in `.camunda-migration/validation/step2-inventory.json` before conversion. Where E1 fetches a
model, add its original path before converting it. Run the `init` command in
`references/validation-evidence.md` after completing the scope. Never create this file for
assessment-only or analyze-only.
When rerunning validation, preserve the inventory's source snapshot. Reset it only from a restored
C7 baseline with the command in `references/validation-evidence.md`.

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

When the project contains a Spring web server, application HTTP endpoint, health check, or Camunda 7
Engine REST call, inventory its HTTP topology. Follow
`references/http-topology-migration.md`. Ask Question 7 from
`references/interview-questions.md` before Step 3. Record the target application bind address and
port, the Camunda REST base address, and the authentication mode. Record the endpoint decisions and
consumer actions. Where the management server uses a separate bind address or port, record both.

#### Test Inventory

Record each test's models in the Model Inventory before Step 3.
When the Test Inventory includes a test with handling **Migrate**, ask Question 8 in a separate
prompt when its conditions in `references/interview-questions.md` apply. Wait for the user's answer
before Step 3.

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

For each call activity, inventory its called process, input/output mappings (including delegates),
and child business-key intent. See `references/model-migration-approaches.md` for scope rules.

For each original BPMN, record every `camunda:executionListener event="start"` directly on a
`bpmn:startEvent`. Record its source path, event ID, and implementation even if the converter omits
its finding.

Run `references/deployment-and-timer-preflight.md` after the code and model inventories. Record its
findings and decisions in `MIGRATION_REPORT.md`.
For a full migration, record deployment sets, timer decisions, and module due-date reviews through
`references/validation-evidence.md`. Never treat the gate's source hints as a complete caller review.
The validation gate supports a guarded `message_rearm` path for project-approved active-timer updates.
When the project approves this path, map each source location to a reviewed migrated caller and run the live check.
If a mapping is unknown or unapproved, then keep the affected flow blocked.

If the model inventory is empty and the user selected model migration, then record that no local
model was found and that E1 was offered.

#### Project Documentation and CI Inventory

Inspect project documentation and CI workflows in every assessment.
Search README files and runbooks for C7 APIs, embedded-engine claims, legacy form and application URLs, and rollback assumptions.
Inspect profiles, ports, worker startup instructions, forms, and process-test commands for in-scope components.
Inspect existing CI workflows for packaging, configuration validation, BPMN lint, process tests, and a working C8/Docker runtime.
Classify each document as in-scope, mixed, retained C7-only, or unknown. See `references/project-readiness.md`.
Do not edit project files other than `MIGRATION_REPORT.md` during assessment.

#### Summary

Present the code and model file counts. Present the overall complexity and the recommended code path.
Present test counts by test kind and list every test with handling **Report only**, with its reason.
State whether recipes help, hurt, or are neutral. Present project documentation dispositions and CI gaps.
Present blockers that need a manual decision. Include the Step 0 preflight result and any user acknowledgment.
State that running instances, history, and audit data are out of scope. Point the user to the Data Migrator.

#### Custom incident notifications

Where the scope includes code migration, run this assessment and decision gate.
Find `org.camunda.bpm.engine.impl.incident.IncidentHandler` implementations and
`ProcessEnginePlugin` registrations in the source and configuration.
Trace each handler's registration and observable actions.
When a handler sends notifications, record a separate `incident-notification` finding in
`MIGRATION_REPORT.md`. Capture its trigger, channel, recipients, context, duplicate behavior, and
exposed data. Keep the finding separate from job-worker migration.

Ask the project owner to choose a Camunda 8-compatible integration or explicitly waive notifications
for each finding. Never remove or replace the handler, its registration, or its configuration
before the project records its decision.

| Project decision | Action | Notification parity |
|---|---|---|
| Not recorded | Keep the finding `blocked`. Record the call site and decision question as an `open` item. Stop before Step 3 confirmation or deployment. | `blocked` |
| Approved integration | Record the target, integration, channel, recipients, approved context, duplicate policy, and privacy requirements. Keep the finding `blocked` until Step 4 verification passes, then resolve it. | `blocked` until Step 4 passes, then `verified` |
| Explicit waiver | Record the approver, reason, and accepted behavior loss. Resolve the finding. | `waived` (not parity) |

When the project approves an integration, test it in a disposable Camunda 8 target with synthetic
data during Step 4. Verify each handler using its recorded trigger.
For failed-job handlers, fail a test job with zero remaining retries and verify the expected incident.
Verify notification delivery through the approved channel to the approved recipients.
Verify that the notification includes useful context approved by the project.
Verify that the notification contains no secrets or sensitive business data.
Verify the agreed duplicate policy for one triggering event.
Where delivery can retry, verify redelivery does not create an unwanted duplicate.
Record the target version, integration, incident, expected and actual delivery counts, and redacted
evidence in `MIGRATION_REPORT.md`.

Where a notification finding exists, include a separate `Notification parity` row for each finding
in the validation summary. Compilation, worker registration, and Operate visibility do not prove
notification parity.

Write the assessment to `MIGRATION_REPORT.md`. Ask the user to confirm before Step 3.

### Step 3: Execute Migration

When the user selects Models only and Analyze-only, run `Analyze-Only Mode` in
`references/model-migration-approaches.md` instead of Part B.
Exit after it presents findings. Do not run conversion, form follow-up, Step 4, or Step 5.

Run Part B when the scope includes models.
Run Part A when the scope includes code.
For Code + models, see `references/composing-code-and-models.md`.

#### Part A - Code Migration

Apply the Transform checklist from `references/code-transform-checklist.md` with the approach chosen
in Question 4. See `references/code-migration-approaches.md` for all three.

For Approach A, the skill runs this gate for every C7 JavaDelegate before `REWRITE_COMMAND`.
For Approach B, the skill runs the gate before each C7 JavaDelegate transformation.
The gate treats `camunda:asyncAfter` on a preceding activity as a boundary after that activity.
If the gate blocks migration or has an undecided gap, then the skill stops that delegate's
transformation and, for Approach A, OpenRewrite. The skill asks the user for the missing evidence or
listed decision.
When the user supplies evidence or makes a decision, the skill reruns the gate.
The skill resumes only after the gate passes or `MIGRATION_REPORT.md` records the user's decision for
every open item. The skill records each accepted parity gap in `MIGRATION_REPORT.md` before it
resumes.

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

For every approach, once each original BPMN is paired with its converted copy, run
`references/form-migration.md` for the Generated Task Forms, then
`references/form-reference-migration.md` for the referenced forms and the form-free owners.

#### Part C - Project Documentation and CI

When the user approves the migration plan, follow `references/project-readiness.md` for in-scope documentation and CI gaps.
Update only approved in-scope documentation. Leave retained C7-only documents unchanged.
While the user selects assessment-only or analyze-only, do not edit project files other than `MIGRATION_REPORT.md`.
Record the findings in `MIGRATION_REPORT.md`.
Follow the exit rule for the selected mode.

### Step 4: Validation (always runs)

Follow `references/validation-evidence.md` to record command results and audit required checks.
Never write a passing command result by hand. Run the gate only after a full migration.
Assessment-only and analyze-only runs do not claim readiness.

#### Code checks, when code was migrated

1. **Compile** — run `mvn compile` or the Gradle compile task. Fix every error.
2. **Camunda 7 dependencies** — inventory every dependency and its uses before removal. Record
   each dependency, use, classification, and decision in `MIGRATION_REPORT.md`. Treat a group ID
   starting with `org.camunda.bpm` as a review signal, not proof that a dependency is engine-only.
   Follow `references/code-transform-checklist.md`. If target compatibility remains unconfirmed,
   then leave the active code unchanged. Record each affected call site as `blocked` with a manual
   follow-up in `MIGRATION_REPORT.md`. Do not report an affected flow as migrated.
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
8. **Dependency compatibility and client startup** — for each Maven module that uses a Camunda
  Spring Boot starter, run the BOM and dependency-family checks in
  `references/code-transform-checklist.md`. Run a focused context test that creates the real
  `CamundaClient` bean. Do not mock the bean or issue a cluster request in this test. The skill
  applies the readiness verdicts in the checklist. Record the failing and final dependency
  coordinates and versions in `MIGRATION_REPORT.md`. Record the evidence and chosen remediation
  there. Record the test command and its exit code there.
9. **Tests** — run `mvn test` or the Gradle test task and every independent suite in each module.
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
   missing entry fails the check. See the mandatory open items in
   `references/code-transform-checklist.md`.
11. **Query counts and pagination**
    - Use a whitespace-tolerant or syntax-aware search for `.items()` followed by `.size()`.
    - Use a whitespace-tolerant or syntax-aware search for `.items()` followed by `.stream()` and `.count()`.
    - Trace search results assigned to variables before checking later `.size()` or `.stream().count()` uses.
    - If a hit represents a complete query count, then treat it as a validation failure.
    - Confirm that each migrated C7 `list().size()`, `list().stream().count()`, or `count()` uses
      `.page().totalItems()`.
    - If a search can exceed cluster result limits, then review `.page().hasMoreTotalItems()`.
12. **Worker adapters** — compare every `@JobWorker` declaration's fully qualified declaring class
    name with the original Java source baseline recorded in Step 2. Flag the declaration when its
    class appears in that baseline, even when the class name ends with `Worker`. Accept it only
    when the class is absent from the baseline, is a new `*Worker` adapter component, and delegates
    to the baseline bean. Record each flagged declaration and its replacement adapter in
    `MIGRATION_REPORT.md`. A migrated Spring bean method must never receive `@JobWorker` directly.
    For each delegate adapter, check that `MIGRATION_REPORT.md` records the pre-transform gate
    result, every incoming path, and the C7 command segment that runs the delegate. Check that the
    report records each `asyncBefore` and `asyncAfter` boundary, rollback effects, and every user
    decision. Check that undecided gaps remain open and accepted parity gaps appear in the decision
    log. If model/path evidence is missing and the user has not decided, check that the report marks
    the gate **blocked** and records the missing evidence and unknown rollback effects in an open
    item with status `open`.
13. **Deployment resources** — when `@Deployment` is present after migration, build the
    inventory from this run's converted copies and accepted forms. Create separate `resources`
    entries for each included type, allowing multiple entries per type. Resolve the actual annotation
    entries with Spring's `PathMatchingResourcePatternResolver`. Require each entry to match a
    non-empty subset of one resource type in the inventory. Reject any match outside the inventory.
    Require each inventory resource to match exactly one entry. Confirm that the packaged
    application contains every match. A test that disables annotation deployment does not validate
    this wiring.
14. **Build wiring** — for each Maven module in the last row of the "Maven build wiring" table in
    `references/code-transform-checklist.md`, `mvn spring-boot:run` resolves the plugin and
    launches the entry point class. `java -jar` on the `mvn package` artifact launches the same
    class. Stop each started process after the launch. The migration adds no
    `@SpringBootApplication` class and no `spring-boot-maven-plugin` declaration to a test-only
    module. A successful compile does not validate the plugin. If startup fails after the launch
    only because no Camunda 8 cluster is reachable, then record that blocker. Record each command
    and exit code in `MIGRATION_REPORT.md` with secret values replaced by `<redacted>`.
15. **HTTP topology** — when the project contains a Spring web server, application HTTP endpoints,
    health checks, or Camunda 7 Engine REST calls, follow
    `references/http-topology-migration.md`. Confirm that the application and cluster use distinct
    ports when they share a host. Test every discovered application endpoint and replacement API
    while the cluster is reachable. When the source includes a health check, verify each remote
    client uses finite connection and response timeouts. Test each dependency while it responds and
    while it is unavailable or timed out. Confirm that the application does not expose or proxy
    `/engine-rest`. A context-load test alone does not pass this check.
16. **SLF4J providers** — the skill runs the provider check in
    `references/code-transform-checklist.md` for every runtime module. The skill records the runtime
    dependency evidence and provider initialization result in `MIGRATION_REPORT.md`. The skill
    reports a complete migration only after a provider **PASS** or a user-approved exception resolves
    the finding. The skill keeps the migration incomplete while the finding remains open. The skill
    never marks logging or startup readiness **PASS** without a passing provider result.

Check these pitfalls as well:

- Naming swap: Camunda 7 `processDefinitionKey` (a string key) becomes Camunda 8 `bpmnProcessId`, and
  Camunda 7 `processDefinitionId` (a UUID) becomes Camunda 8 `processDefinitionKey`. Decision
  definitions swap the same way.
- Camunda 7 `processInstanceId` is a `String`. Camunda 8 `processInstanceKey` is a `Long`. Update declarations and call sites, not only the names.
- Variables are plain JSON and the `TypedValue` API is gone, so every `VariableMap` use changes.
- Batch operations exist since 8.8. Only a custom batch handler needs a manual design.

#### Model checks, when models were migrated

Lint every in-scope BPMN and DMN model with the target-compatible ruleset, not only models that the
skill edited. After every manual BPMN edit, lint the converted copy again. See the linting section
in `references/model-migration-approaches.md`.

1. A `converted-c8-*` file exists for every in-scope diagram, unless the run is analyze-only.
2. Every original file is intact and was never overwritten.
3. Treat every resource directory that the build configures for inclusion in a Maven or Gradle
   application artifact as a packaged resource directory. Include `src/main/resources` when it
   exists. No findings report named `analysis-results.<ext>` or `analysis-results (n).<ext>` remains
   under a packaged resource directory, where `n` is a positive integer and `<ext>` is `.csv`,
   `.json`, `.md`, or `.xlsx`. Keep findings reports under `.camunda-migration/reports/` only when
   the build does not package that directory. Otherwise, use another explicitly non-packaged
   directory.
4. Fix every WARNING, TASK, REVIEW, and INFO finding, or classify it in the per-category/impact
   verdict table. Each row gives its category, runtime impact, count, Element list,
   cross-referenced code artifact, impact evidence, and verdict. In an M1 run, every **needs fix**
   or **needs review** verdict-table row references its matching complete Element list. A converter
   category names the actual artifact path. See
   `references/model-migration-approaches.md` step 5d. A flat "fixed or recorded" note is not enough.
5. Every source Generated Task Form is `accepted`, `blocked`, or `declined`, including a
   form-property-only definition. None is silently omitted.
6. Every accepted form is a standard Camunda 8 `.form`.
7. Every accepted form parses.
8. Where a target-compatible official schema exists, the skill validates every accepted form with it.
9. Where target-compatible form-js tooling exists, the skill imports or renders every accepted form
   with it.
10. Every accepted form has a matching `zeebe:formDefinition`.
11. The skill deploys every accepted form with its BPMN.
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
15. Once the verdict table is complete, the converted copies hold no `conversion:*` node, no
   `conversion:*` attribute, no unused Camunda 7 namespace declaration, and no leftover BPMN
   definitions-level XPath `expressionLanguage` attribute.
16. For every converted BPMN with source BPMN DI, the converted copy preserves the source diagram,
    plane, shape, edge, label, bounds, waypoint, and `bpmnElement` reference data for unchanged
    semantic IDs.
17. For every converted BPMN without source BPMN DI, the skill does not create layout data and
    records the absent source DI as provenance in `MIGRATION_REPORT.md`.
18. When a semantic rewrite changes an ID referenced by BPMN DI, the skill updates the reference or
    records a blocking or review finding when it cannot reconcile the reference.
19. When the model uses M2, inspect every `zeebe:taskDefinition/@type`. Derive the expected type
    from the original `camunda:delegateExpression`, `camunda:expression`, `camunda:class`, or
    `camunda:topic` attribute using the binding rules in
    `references/model-migration-approaches.md`. If the emitted type differs, require a confirmed
    decision-log entry in `MIGRATION_REPORT.md` with the source file and element, original
    implementation, emitted type, and rationale. Treat a mismatch without that entry as a
    validation failure.
20. When the model uses M2, the skill runs the expression-prefix validator for every source and
    converted pair:

    ```sh
    python3 "<skill-directory>/scripts/validate_model_expressions.py" \
      --pair "<source.bpmn>" "<converted-c8-source.bpmn>"
    ```

    The skill supplies one `--pair` argument for every in-scope BPMN or DMN model.
    The validator parses each pair with a namespace-aware XML parser.
    The validator applies the first matching row to each `bpmn:conditionExpression` and
    conditional-event `bpmn:condition`:

    | Language attribute | Source value | Validator action |
    | --- | --- | --- |
    | Other than `juel` or `feel` on either copy | Any | Report a blocking redesign finding. Keep the finding blocked until the user approves a replacement. Do not translate the source condition automatically. |
    | `juel` or `feel` on the source | Any | Require a leading `=` on the converted expression. |
    | Missing or blank (default JUEL) | Starts with `=`, contains `${` or `#{`, or is another non-empty non-literal value | Require a leading `=` on the converted expression. |
    | Missing or blank (default JUEL) | Boolean, number, `null`, or quoted string without those markers | Treat the value as a literal. Do not require a leading `=`. |
    | Missing or blank (default JUEL) | Empty | Do not check the source as a dynamic condition. |
    The validator pairs `bpmn:conditionExpression` with its sequence flow.
    The validator pairs conditional-event `bpmn:condition` by its owning definition or event ID,
    not its optional condition ID.
    The validator pairs C7 input and output expressions with their Zeebe source attributes.
    The validator fails when the converted copy has no Zeebe mapping with the source parameter's
    name as its target.
    The validator treats C7 input and output parameters with nested
    `camunda:script scriptFormat="feel"` elements as dynamic expressions.
    The validator requires a leading `=` on each paired dynamic input or output source.
    The validator pairs dynamic `zeebe:subscription/@correlationKey` values with their source
    expressions.
    The validator requires a leading `=` on each converted
    `zeebe:subscription/@correlationKey`.
    The validator pairs dynamic `zeebe:calledElement/@processId`, `zeebe:taskDefinition/@type`,
    `zeebe:assignmentDefinition`, and `zeebe:formDefinition/@formId` values with their source
    expressions.
    The validator requires a leading `=` on each paired dynamic value.
    The validator rejects every `language="feel"` and `language="juel"` attribute in the converted
    copy.
    The validator rejects every BPMN `expressionLanguage` attribute.
    The validator does not check DMN `expressionLanguage` attributes.
    The skill preserves valid DMN expression languages.
    The skill records the command, exit code, source and converted paths, and each finding in
    `MIGRATION_REPORT.md`.
    | Validator finding | Skill action |
    | --- | --- |
    | Missing prefix or mapping | Fix each finding and rerun the validator before marking the model row passed. |
    | The converted BPMN copy retains a `language="feel"`, `language="juel"`, or `expressionLanguage` attribute | Remove the leftover attribute and rerun the validator before marking the model row passed. |
    | Unsupported condition language | Keep the redesign finding blocked until the user approves a replacement. Do not translate the source condition automatically. |
21. Every executable process has a test that starts it directly, with the normal inputs and without
    each input that a worker may not receive. Coverage through a call activity does not count,
    because the parent can supply variables that a direct start lacks. If a process is not a valid
    standalone entry point, then `MIGRATION_REPORT.md` records the process ID, the reason, and the
    covering test. A process with neither fails validation. For each failing scenario, record the
    process ID, inputs, failing element, job type, and incident message.
22. For each call activity, compare the converted scope with its original inputs and outputs.
    Use a namespace-aware XML parser to check every `bpmn:callActivity` in every
    `converted-c8-*.bpmn` file. For each call with a compatible C8 mapping, require its
    `zeebe:calledElement` to set `propagateAllChildVariables` explicitly.
    Require the flag to be `true` or `false`.
    Confirm that each flag matches the original C7 `camunda:out` mappings and the established
    contract or user-approved scope. Apply the rules in
    `references/model-migration-approaches.md`. Include both C7
    `camunda:variableMappingClass` and `camunda:variableMappingDelegateExpression` in this check.
    If the skill cannot establish a delegated contract, keep the call **needs review**.
    Ask the user to decide its scope.
    Do not assign propagation flags to a call without a compatible C8 mapping.
    Do not pass readiness validation while a call remains **needs review**.
    Test selected inputs with a parent-only variable, and check child identity independently.
    Record each contract in `MIGRATION_REPORT.md`. Keep incompatible or untested calls **needs review**.
23. **Selected M1 artifact** — record the CLI tag, JAR path, validated Java executable, and target
    version. Apply step 3b in `references/model-migration-approaches.md` to every source start
    listener and converted copy.

    | Artifact evidence | Action |
    |---|---|
    | One matching `TASK` finding per source listener and no invalid placement | Complete any user-approved relocation and target validation. |
    | Missing, downgraded, duplicate, or unmatched finding, or invalid placement | Block automatic compatibility. Add a source-derived `TASK` finding for each uncovered listener. It is not a converter match. Use a patched release or request approval for manual follow-up. |
    | A worker exists but the artifact or follow-up fails | Keep model readiness blocked. A worker does not validate listener placement. |

24. **Target deployment** — when the user authorizes a test target, verify its profile and version
    as described in `references/model-migration-approaches.md`. Deploy explicit converted BPMN and
    DMN paths with accepted `.form` paths and their owning BPMN in the same request. Use
    `c8ctl deploy <files...> --profile=<name> --json` or the same authorized deployment client.
    If two resources would share a deployment name, then block deployment until their names differ.
    Record a result for every resource path and each form's owner. If authorization, target-version
    evidence, deployment success, or an approved listener follow-up is missing, then block model
    readiness.

#### Process behavior

For every executable process, run the applicable assertions in `references/validation-evidence.md`.
Record justified non-applicability in `MIGRATION_REPORT.md`.

#### Project readiness checks, when code or models were migrated

Run the project-specific packaging, configuration, BPMN lint, and process-test checks in
`references/project-readiness.md`.
Use a working target-compatible C8 runtime and required Docker services for relevant process tests.
Record the workflow or command, runtime, exit code, and sanitized evidence in `MIGRATION_REPORT.md`.
Do not report project readiness from packaging success alone.

#### Summary

Present a validation summary that states the status of compilation, configuration binding,
remaining Camunda 7 imports, remaining migration TODOs, `businessKey` uses, the open items, tests,
converted models, and the findings that still need follow-up. Run the evidence validator and use its
generated gate block as the validation-readiness summary in `MIGRATION_REPORT.md`. Record project
documentation, CI readiness, and the project-readiness verdict separately.
State test verification as `verified`, `not verified (Migrate tests only)`, or `blocked` with its
reason.

### Step 5: AI Follow-up (offer after validation)

#### Verification before closing a model verdict-table row

Never set a model-finding category/impact row's verdict to **no action** or report it as resolved until this
gate passes.
Run the gate after each accepted fix and before a no-change row becomes **no action**.
Use every `converted-c8-*` BPMN or DMN copy named by the row's pre-fix findings report.
Record `Before` evidence before editing and `After` evidence after checking in
`MIGRATION_REPORT.md`.

| Check | Pass condition | Record |
|---|---|---|
| XML | Each converted copy parses with a namespace-aware XML parser. | Command, exit code, and paths |
| Conditional-event IDs | Where the target is Camunda 8.9 or later, each converted `bpmn:conditionalEventDefinition` has a nonempty `id` that does not match another XML ID. Each converted definition remains under the same event ID as its source definition. When the converter reads a nonempty source definition ID that is unique in the source document, the converted definition retains that ID. | Paths, source and converted definition IDs and owning event IDs, and validator result |
| Camunda 7 constructs | No Camunda 7 namespace element, attribute, or QName remains after cleanup. | Before-and-after counts |
| Wiring | Matching task definitions, headers, listeners, and DMN or precompute references remain. | Source-to-converted mapping and code coverage when code is in scope |
| BPMN DI | A source with DI retains its diagram, plane, shape, edge, label, bounds, waypoint, and `bpmnElement` reference data for unchanged IDs. A source without DI remains without DI. | Before-and-after counts, reference mapping, and source-DI provenance |
| FEEL | Every changed FEEL expression parses with a target-compatible parser when one is available. | Parser version, expression location, and result |
| Converter regression | Run `local <original-input> --check --csv` when the original input and recorded options are available. | Command and relevant CSV rows |
| Deployment readiness | After a model or deployment change, repeat Step 4's resource and target checks before reporting model readiness. | Resolved patterns, packaged entries, target version, and per-resource results |

Record one verification row per category/impact row with its check results and `pending`, `passed`, or
`failed` state.
Mark verification `passed` only when every applicable check passes.
If a check fails, re-open the row as **needs fix**.
If a check cannot run or a user decision remains, keep the row **needs review**.
In analyze-only mode, keep every model category/impact row **needs review**.
Never start an automatic remediation loop.

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

For model categories, work from the verdict table in `model-migration-approaches.md` step 5d.
For each M1 **needs fix** or **needs review** row, load its complete list from the Element list
reference. Use the grouped summary only to select a category.

For `needs fix` rows, sequence the follow-up work by runtime impact:

| Order | Runtime impact | Secondary order |
|---|---|---|
| 1 | **Blocking** | Highest severity present (`TASK` > `WARNING` > `REVIEW` > `INFO`), then count descending |
| 2 | **Advisory** | Highest severity present (`TASK` > `WARNING` > `REVIEW` > `INFO`), then count descending |

Present the runtime impact with every verdict-table row. Resolve all **Blocking** rows before
**Advisory** rows. Do not use severity as a substitute for runtime impact.
Within each impact, process rows with a severity before source-derived rows without one.

| Verdict | Action |
|---|---|
| **needs fix** | Resolve one verdict-table row at a time, using that row's cross-check guidance. |
| **needs review** | Collect the pending user decision before any fix. Run the gate directly when verification is the only pending action. |
| **no action** | Do not offer the row. |

- Apply an unambiguous fix directly, using the pattern catalog.
- Propose an ambiguous fix to the user. Skip whatever the user declines.
- Handle `form-data` and source-detected `formProperty` through `references/form-migration.md`:
  generate the drafts deterministically, and link only an accepted form.
- Handle the form-reference categories through `references/form-reference-migration.md`: present the
  inventory, and take one decision per integration group inside each category, grouping only owners
  that share an integration.
- When the source inventory or M1 findings report identifies a start listener, use the relocation procedure in
  `references/model-migration-approaches.md`.
- Never relocate this listener automatically.
- Confirm that the chosen target still supports execution listeners on the enclosing process or
  subprocess before you offer relocation.
- If the chosen target is earlier than Camunda 8.6, do not offer relocation.
- Keep the category **needs review** and offer manual migration for a target earlier than Camunda 8.6.
- Ask the user before editing an affected converted copy.
- Offer to move each affected listener to the nearest enclosing process or subprocess.
- Keep the category **needs review** when the user declines or verification fails.
- When a Code + models run has Diagram Converter findings, offer a dispatcher scaffold for each many-to-one
  job-type group with a **needs review** verdict and no dispatcher. Use the procedure in
  `references/composing-code-and-models.md`.
- After each batch, ask whether to commit.
- For a model-finding batch, run the verification gate before updating the verdict table in
  `MIGRATION_REPORT.md`.
- For a start-listener relocation, use the dedicated deployment and route checks in the relocation
  procedure. Run the Step 4 test suite only when the same batch changed code.

#### Action 2: delete now-redundant code

The model/code cross-check flags Camunda 7 workaround code as a deletion candidate when a finding
reports that Zeebe now provides the capability natively. See "Now-redundant workaround code" in
`references/composing-code-and-models.md`.

Deleting code is never unambiguous. Even under "Yes, fix what you can", present every deletion
candidate to the user with its reasoning: the triggering finding, what the code did, and
why it is now redundant. Delete only on an explicit confirmation. Record the confirmed deletions and
the declined candidates in `MIGRATION_REPORT.md`.

## Exit Criteria

For a full migration, report completion only when every pass condition in Step 4 holds, the evidence
gate reports `READY`, and the project-readiness verdict is `ready`. Keep complete inventories,
decisions, open items, and validation results in `MIGRATION_REPORT.md`.
The skill reports a complete migration only when no unresolved migration TODO, finding, compilation
issue, deletion candidate, or project-readiness blocker remains. No item can have `deferred` or
`blocked` status.
An open item is a team decision. It does not block completion unless it prevents an in-scope
documentation change or a required readiness check. The summary always lists every open item.
Unresolved deployment/timer preflight findings are `blocked` readiness checks, not non-blocking
`open` team decisions.
Otherwise, the skill reports the migration as incomplete and records the follow-up work.
Where the root is confirmed, follow `references/final-change-summary.md` before the final response.
