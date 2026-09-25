# Interview Questions Reference

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## Pre-Interview Detection

Pick a candidate project root (use provided argument or current working directory), then:
1. Detect build tool from `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle)
2. Glob for models: `**/*.bpmn`, `**/*.bpmn20.xml`, `**/*.dmn`, `**/*.dmn11.xml`

This shapes the scope question. The confirmed scan after Q1 gates whether to offer C7 engine options.

## Question Batching Rules

- At most 4 questions per prompt
- Every question with `options` must have at least 2 options
- Batch: Prompt 1 = Q1, then re-scan, Prompt 2 = Q2+Q3, Prompt 3 = conditional Q4/Q5/Q5a/Q6

Ask Question 7 in a separate prompt after Step 2 identifies an HTTP topology. Do not add it to
Prompt 3 because that prompt can already contain four questions.

---

## Question 1 - Project Location

Confirm the detected project root. Provide two options:
- Use `<detected path>` (recommended)
- Enter a different path

---

## Question 2 - Target Camunda 8 Version

Options (user cannot select anything else):
- 8.10 (next version, not yet GA) - includes all features from 8.8 and 8.9
- 8.9 (latest stable) - adds Business ID (business key successor), BPMN conditional events, global user task listeners, batch delete, History/Identity Data Migrator
- 8.8 - first version with unified Orchestration Cluster API, CamundaClient, and Camunda Process Test. No Business ID (use tags), no conditional events.

Record the concrete major.minor and use it throughout. Also pass to Diagram Converter as `--platform-version`.

---

## Question 3 - Migration Scope

Tailor wording to the detection. Options:
- Code + models (recommended when both present, default)
- Code only - Java/Spring code, runs Part A
- Models only - BPMN/DMN diagrams, runs Part B
- Assessment only - scan and report, no changes

When no local model files found: keep Code only as default recommendation. Offer C7 engine source only if user explicitly selects Code + models or Models only.

---

## Question 4 - Code Migration Approach

Include only if code files present and user selected code migration. Options:
- A. OpenRewrite (recipe-assisted) + AI - use for repeated, supported, syntactic transformations or a
  deterministic first diff. Expect scaffolding, TODOs, and cleanup.
- B. AI only (AI-first, recommended with a capable coding model) - use for semantic, mixed, or
  complex code. Use it when OpenRewrite cannot run. Model quality affects the result.
- C. Assessment only - scan codebase and produce report, no code changes

---

## Question 5 - Model Source and Migration Approach

Include only if user selected model migration.

### If local model files found (show M1-M3):
- M1. Diagram Converter CLI (deterministic) + AI (recommended) - requires Java 21+, produces converted files plus analysis reports
- M2. Agentic AI - AI rewrites BPMN/DMN XML directly. Use when Java 21 unavailable.
- M3. Online Diagram Converter (hosted) - upload at https://diagram-converter.camunda.io/

### If no local model files found (show E1-E2):
- E1. Camunda 7 engine (recommended) - fetch definitions from C7 REST API
- E2. Provide a model path - wait for user to provide another file/directory

## Question 5a - Model Execution Mode

Include only when the user selected **Models only** and M1 or M2.

Options:
- **Analyze-only** - Run the M1 `--check` or M2 read-only pass. Report findings. Do not create
  converted copies, forms, or fixes.
- **Convert models** - Run the selected model approach. Continue with form and finding follow-up.

---

## Question 6 - Build Tool

Include only if scope includes code, approach is A, and detection was ambiguous (both Maven and Gradle found, or neither). If exactly one detected, state it rather than asking.

## Question 7 - Application and Engine HTTP Topology

Ask after the Step 2 code inventory only when the project has a Spring web server, an application
HTTP endpoint, a health check, or a Camunda 7 Engine REST call.

Show the application and management ports, application-owned routes, Engine REST routes, outbound
Engine REST call sites, health dependencies, and known consumers from the inventory. Ask the user to
confirm the target application port, Camunda REST base address, and authentication mode.

For every application endpoint or Engine REST call, ask the user to select a target:

| Target | Use when |
|---|---|
| Camunda 8 Orchestration Cluster API | A documented C8 operation replaces the C7 Engine REST call. |
| Deliberate application API | The route is part of the application's own contract. |
| Manual migration follow-up | The replacement or endpoint-removal plan needs a decision. |

Do not offer a proxy or recreation of the C7 `/engine-rest` API. See
`http-topology-migration.md` for the inventory, decision record, and validation.
