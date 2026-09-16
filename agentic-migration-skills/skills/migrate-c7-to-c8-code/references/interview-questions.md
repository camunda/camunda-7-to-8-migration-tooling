# Interview Questions Reference

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## Pre-Interview Detection

Pick a candidate project root (use provided argument or current working directory), then:
1. Detect build tool from `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle)
2. Glob for models: `**/*.bpmn`, `**/*.bpmn20.xml`, `**/*.dmn`, `**/*.dmn11.xml`

This shapes the scope question. The confirmed scan after Q1 gates whether to offer C7 engine options.

## Question Batching Rules

- At most 4 questions per AskUserQuestion call
- Every question with `options` must have at least 2 options
- Batch: Call 1 = Q1, then re-scan, Call 2 = Q2+Q3, Call 3 = conditional Q4/Q5/Q6.
Ask Q7 separately after the Step 3 form procedures finalize statuses.

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
- A. OpenRewrite (deterministic) + AI (recommended) - runs recipes first, then AI resolves remaining TODOs
- B. AI only - AI migrates everything directly. Use when OpenRewrite cannot run.
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

Any of M1-M3 can run in analyze-only mode first (`--check` flag).

---

## Question 6 - Build Tool

Include only if scope includes code, approach is A, and detection was ambiguous (both Maven and Gradle found, or neither). If exactly one detected, state it rather than asking.

## Question 7 - Form Deployment Decision

Include when the selected model scope produces an existing Camunda 8 form, accepted form, rebuilt
form, or relinked form with a
deployable owner. Ask and record the branch per form. Group forms only when they have identical
targets, request states, authorization states, and deployment decisions. Record the fields in
`MIGRATION_REPORT.md`:

Ask: **"Which deployment state applies to `<form-owner>`?"** Use these options:

- **No target** — no deployment is in scope.
- **Target and authorized request** — provide the target, request, and authorization.
- **Target but request not made** — provide the target and current authorization state.
- **Target but authorization unavailable** — provide the target, request state, and authorization.
- **Target declined** — provide the target, request, authorization, and decline decision.

For every selected target, collect the concrete target value through the secure deployment
mechanism. The report-safe target field must redact credential-like URL query values and URL userinfo
passwords with `<redacted>`, using the same rules as the form-reference inventory. Never write the
unredacted target to `MIGRATION_REPORT.md` or any committed artifact.

- **No deployment target** — record `target=none`, `request=not applicable`,
  `authorization=not applicable`, `deployment decision=out of scope`, and
  `deployment=not applicable`.
- **Target, explicit request, and authorization** — record the target, request, authorization,
  and `deployment=pending` before final cleanup. Record the deployment result after final cleanup.
- **Target without request** — record the target, `request=not requested`, authorization state,
  and `deployment=pending`. Obtain the request before deployment.
- **Target with unavailable authorization** — record the target, request, `authorization=unavailable`,
  and `deployment=pending`. Require a supported alternate binding or an explicit
  external-deployment plan before closing.
- **Target with decline** — record the target, request, authorization state,
  `deployment decision=declined`, and `deployment=pending`. Require a supported alternate binding
  or an explicit external-deployment plan before closing.
