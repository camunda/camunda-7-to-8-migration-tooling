# Interview Questions Reference

## Pre-Interview Detection

Before asking questions, pick a candidate project root (use provided argument or current working directory), then:
1. Detect build tool by checking for `pom.xml` (Maven) or `build.gradle` / `build.gradle.kts` (Gradle)
2. Glob for models: `**/*.bpmn`, `**/*.bpmn20.xml`, `**/*.dmn`, `**/*.dmn11.xml`

This shapes the scope question. The confirmed scan after Q1 gates whether C7 engine options are offered.

## Question Batching Rules

- At most 4 questions per AskUserQuestion call
- Every question with `options` must have at least 2 options
- Batch: Call 1 = Q1, then re-scan, Call 2 = Q2+Q3, Call 3 = conditional Q4/Q5/Q6

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

Tailor wording to what was detected. Options:
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

Any of M1-M3 can run in analyze-only mode first (--check flag).

---

## Question 6 - Build Tool

Include only if scope includes code, approach is A, and detection was ambiguous (both Maven and Gradle found, or neither). If exactly one detected, state it rather than asking.
