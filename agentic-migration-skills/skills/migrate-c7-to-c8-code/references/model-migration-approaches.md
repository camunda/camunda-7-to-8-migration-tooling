# Model Migration Approaches (Part B)

## Source Selection

Use the model scan from the assessment before choosing a conversion path:
- If local model files were found under the project root, use local mode. Do NOT offer or request C7 engine access.
- If no local model files were found and user selected E1, fetch definitions from C7 first.

---

## Deterministic conversion and human review

- Prefer the official Diagram Converter whenever it supports the model. Always pass the exact selected target platform version, and do not hand-edit namespaces as a substitute when the converter applies.
- A converted file is not automatically safe to deploy. Treat every TASK/WARNING/REVIEW finding, unsafe or partial transformation, unsupported behavior, and generated Task Form as an explicit human-review item in `MIGRATION_REPORT.md`.
- If Camunda 8 has no safe representation for a C7 construct, stop and ask the user for a decision. Do not invent a namespace, expression, or behavior mapping.

---

## Pre-flight: Leftover Artifacts

Before any local approach (M1, M2, E1), scan the project for outputs of previous migration attempts:

- `converted-c8-*.bpmn` / `converted-c8-*.dmn` (or the `--prefix` equivalent)
- `analysis-results.csv` / `.json` / `.md` / `.xlsx`, including ` (n)`-suffixed siblings such as `analysis-results (1).json` — a sure sign of a previous run

The `.camunda-migration/` CLI JAR is an intentional cache, not a leftover — never flag it.

If anything is found, warn via AskUserQuestion before converting:

> Found outputs from a previous migration attempt: `<list>`. This run will not overwrite them. The fresh analysis report is written alongside under a ` (n)`-suffixed name, and only this run's own outputs are used — stale files are never consumed. Diagrams whose `converted-c8-*` target already exists are skipped with an error, so for a full re-conversion, cancel and delete or move the old files first.

- **OK, proceed** - run without `-o`/`--override`; old files stay untouched.
- **Cancel** - stop so the user can back up or clean up first.

Never consume a pre-existing report or converted file found on disk: it may come from an interrupted attempt or a different `--platform-version`. The findings flow (M1 steps 3-5) works only from the artifacts of this session's own run.

---

## Approach M1 - Diagram Converter CLI + AI (recommended)

### 1. Java 21+ Prerequisite (fail fast)

Detect whether Java is installed and record its major version. If java is missing or major version < 21, STOP and explain:

> The Diagram Converter CLI requires Java 21+. Detected: `<version or "not found">`. Install Java 21+ and re-run, or choose M2 (agentic AI) which needs no Java, or M3 (online converter).

Do not silently skip model migration.

### 2. Resolve Latest Release and Download CLI

The CLI is published as a self-contained executable JAR named `camunda-7-to-8-diagram-converter-cli-<tag>.jar` on the GitHub releases for `camunda/camunda-7-to-8-migration-tooling`.

Steps:
1. Determine the latest release tag
2. Ensure `.camunda-migration/` exists in the project root
3. Compute target path: `.camunda-migration/camunda-7-to-8-diagram-converter-cli-<tag>.jar`
4. If that JAR already exists, reuse it
5. Otherwise download from: `https://github.com/camunda/camunda-7-to-8-migration-tooling/releases/download/<tag>/camunda-7-to-8-diagram-converter-cli-<tag>.jar`

The JAR is ~30 MB. If the project is a git repo, recommend adding `.camunda-migration/` to `.gitignore`; only modify `.gitignore` after the user confirms via AskUserQuestion.

### 3. Run the Converter

The CLI local subcommand accepts a single file or a directory (recursive by default). Always pass `--platform-version` set to the target version from the interview.

```
java -Dfile.encoding=UTF-8 -jar <jar> local <file-or-dir> --platform-version <target-version> --json --xlsx
```

Recommended flags:
- `--json` - always pass this; the JSON report is the machine-readable input parsed in step 5. Requires a CLI release that includes the flag (0.3.6 or later) — if the run fails with `Unknown option: '--json'`, the downloaded JAR predates it; re-resolve the latest release (step 2).
- `--xlsx` - always pass this; the spreadsheet is the human-readable report for reviewing and sharing findings with the customer
- `-o` / `--override` - overwrite pre-existing outputs in place. Destructive — do not pass by default (see Pre-flight: Leftover Artifacts). Without it, a diagram whose converted target already exists is skipped with a `File already exists` error, and reports are written under ` (n)`-suffixed names.
- `--check` - analyze-only (no converted diagrams exported)
- `-nr` / `--not-recursive` - disable recursive search

Other options:
- `--prefix <str>` - prefix for generated filenames (default `converted-c8-`)
- `--md` - write analysis report in markdown format

The converter writes a new file next to the source (e.g., `converted-c8-order-process.bpmn`), so originals are never mutated in place.

Capture the exact paths of everything the run produces from the `Created ...` lines in the CLI console output (e.g. `Created analysis-results (1).json`). These paths are the authoritative inputs for steps 4 and 5 — never glob for `analysis-results.json` or `converted-c8-*` on disk, which may match stale files from a previous attempt or a different `--platform-version`.

### 4. Surface Outputs

After the run, report:
- Converted files: list every `converted-c8-*.bpmn` / `*.dmn` produced (from the captured `Created ...` lines)
- Skipped files: any `File already exists` errors, naming the stale targets — those diagrams were NOT converted; offer to re-run once the user removes the stale copies (see Pre-flight: Leftover Artifacts)
- Analysis findings: summarize from CLI stdout and/or the JSON report, grouped by severity (WARNING / TASK / REVIEW / INFO)
- Analysis artifacts: point the user to the XLSX report — it is the human-readable artifact for reviewing and sharing findings with the customer. The JSON report is the machine-readable input for step 5.

Severity counts are only a headline. Do not start per-finding work from them — parse and group the full report first (step 5).

### 5. Follow Up on Findings

REVIEW/WARNING/TASK findings remain and JUEL conversion is partial. Resolve them in the AI follow-up step, working on the `converted-c8-*` copies (never the originals).

On a real project the report can hold thousands of rows but only a handful of distinct categories. Parse the report and group by category first — the category, not the individual row, is the unit of work for follow-up.

#### Imported reports: verify the target platform version

Skip this check when the report comes from this skill's own CLI run — that run already passed the chosen `--platform-version`, and leftover reports from earlier local runs are never consumed at all (see Pre-flight: Leftover Artifacts).

This check fires only for a report deliberately imported without a fresh run — generated earlier, by someone else, or downloaded from the hosted converter (M3, which produces the same file via its 'Download JSON' button). Only a JSON report is consumable (see 5a — there is no CSV parsing path); if the import is CSV, markdown, or XLSX only, re-run the CLI locally with `--check --json --xlsx` on the input models instead. For an imported JSON report, confirm it was generated for the currently chosen target platform version before consuming it. Findings are version-dependent: e.g. conditional events are flagged as unsupported in a report targeting 8.6 but are native since 8.9, so a stale report can send the user chasing findings that don't apply to their target.

Determine the report's target version:

1. Findings with `messageId` `element-available-in-future-version` name it: the message reads `Element '<name>' is not supported in Zeebe version '<report-target>'. It is available in version '<x.y>'.` — `<report-target>` is the version the report was generated against.
2. If no such findings exist, the version can't be determined from the content — ask the user which `--platform-version` the report was generated with.

If the report's version doesn't match the chosen target, or it can't be determined, warn the user and offer via AskUserQuestion before grouping (5b) or any cross-checks:

- **Re-run the converter at the chosen target** (recommended) - run the CLI from step 2 with `--check --json --xlsx --platform-version <target-version>` on the same input. Analyze-only mode is fast, writes no converted files, and produces fresh JSON and XLSX reports for 5a.
- **Keep the imported report** - proceed as-is and record in MIGRATION_REPORT.md that the findings were generated against a different or unknown target version.

#### 5a. Parse the JSON report

Read the JSON report programmatically, using the exact path captured from this run's `Created ...` output line (it is written next to the converted files when `--json` is passed, which M1 always does — under a ` (n)`-suffixed name when a stale report exists). Never parse a pre-existing `analysis-results.json` found on disk. Do not rely on stdout severity counts as a substitute.

Format: a JSON array with one object per finding, fields:

```
filename, elementName, elementId, elementType, severity, messageId, message, link
```

Parse it with real JSON tooling (e.g. `jq` or the runtime's built-in JSON parser) — never with ad-hoc string splitting. JSON needs no quoting or escaping workarounds, so the parsed findings are identical on every run regardless of which agent or model executes the skill.

If the JSON report is missing (e.g. only `analysis-results.md` or a CSV/XLSX was generated), re-run the converter with `--check --json --xlsx` on the same input. Analyze-only mode writes no converted files and produces both reports quickly. The markdown and XLSX reports are for human reading; CSV is not consumed at all — this skill has no CSV parsing path, and the JSON report is the only machine-readable findings source. (The hosted converter in M3 produces the same JSON file via its 'Download JSON' button.)

#### 5b. Group findings by category

Group findings by `messageId` (the category). For each category compute:

- Total count, and count per severity
- Distinct `elementType` values affected (e.g. serviceTask, sequenceFlow, multiInstanceLoopCharacteristics)
- One representative example: a `message` with its `filename` and `elementId`
- The `link`, pointing at conversion guidance for that category

Sort categories by highest severity (TASK > WARNING > REVIEW > INFO), then by count descending.

#### 5c. Present the grouped summary

Present the grouped table before any per-finding follow-up work starts, and record it in MIGRATION_REPORT.md:

| Category (messageId) | Severity | Count | Element types | Example |
|---|---|---|---|---|
| `expression-method-not-possible` | REVIEW | 1,308 | sequenceFlow, exclusiveGateway | "Method invocation is not possible in FEEL: ..." in order-process.bpmn, element `Gateway_1` |

This grouped structure is the foundation for everything that follows: cross-checking categories against the code migration output, per-category verdicts, and category-specific handling all consume this table.

#### 5d. Emit a per-category verdict table

After grouping (and after the code cross-checks in `composing-code-and-models.md` when code is also in scope), assign each WARNING/TASK/REVIEW category exactly one verdict and record the table in MIGRATION_REPORT.md. INFO categories may be included as well, typically with verdict no action, but are not required. Do not leave findings as severity counts or a generic "findings need follow-up" note — on a real project with thousands of rows, the verdict table is what makes review tractable.

Verdicts:

- **no action** — nothing to do: the converter handled the category deterministically, the finding is purely informational (typical for INFO), or a cross-check confirmed full coverage.
- **needs review** — a human decision is required before any fix can start: e.g. choosing the remediation approach for a category (one decision per category, not per row), or confirming a cross-check result.
- **needs fix** — concrete, known work remains: an uncovered cross-check item (job-type mismatch, uncovered original expressions, uncovered invoked methods) or a WARNING/TASK category with a clear remediation.

| Category (messageId) | Count | Cross-referenced code artifact | Verdict |
|---|---|---|---|
| `expression-method-not-possible` | 2,137 | none yet — remediation decision pending | needs review |
| `delegate-expression-as-job-type` | 2,491 | `DelegateDispatcher` @JobWorker (routes 38/42 expressions) | needs fix |
| `form-reference` | 96 | n/a | no action |

Rules:

- One row per category, sorted as in 5b (highest severity, then count descending).
- The cross-referenced code artifact column names the `@JobWorker`, DMN definition, or other code element the cross-check matched the category to — or `none yet` when no remediation exists. For models-only scope there is no code output to cross-reference: use `n/a` and derive the verdict from severity alone (INFO → no action, REVIEW → needs review, WARNING/TASK → needs fix).
- Every WARNING/TASK/REVIEW category must end up classified — none may be left without a verdict.
- Categories with verdict **needs fix** are the direct work items for the AI follow-up step. Categories with verdict **needs review** are also surfaced there, but only to collect the pending user decision (via AskUserQuestion) before any fix is attempted.

---

## Approach M2 - Agentic AI (direct XML rewrite)

Use when Java 21 is unavailable, user wants to review every change, or CLI cannot handle a case.

Fetch current diagram-conversion guidance:
`https://raw.githubusercontent.com/camunda/camunda-docs/main/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter.md`

For each in-scope diagram, produce a new `converted-c8-<name>.bpmn`/`.dmn` (never edit original) applying:
- `camunda:` namespace/extension elements to `zeebe:` equivalents (task definitions/job types, IO mappings, headers)
- Execution/task listeners to `zeebe:executionListeners` / user task listeners
- JavaDelegate/expression references to job types (or blank, to be filled)
- Simple JUEL to FEEL for pure data expressions; flag bean-invoking expressions for manual work
- Complex Groovy or script logic on sequence flows must move to an upstream service task/job worker; do not synthesize a FEEL expression.
- Conditional events natively only on 8.9+; otherwise flag
- DMN: update decision/definition namespaces and expression language as needed

Emit a findings summary mirroring CLI severities (WARNING/TASK/REVIEW/INFO) and ask for human review.

---

## Forms and data semantics

Apply these checks to every model migration approach:

- Inventory every C7 form, including generated Task Forms. Migrate each to a standard C8 form and link it from the converted BPMN. Generated Task Forms must be explicitly flagged for this skill to handle manually; do not treat them as automatically converted.
- For a C7 `FileValue`, use a Document API reference rather than a filename-only reference.
- Preserve path-as-key mappings and their FEEL semantics. Surface unsupported form validation rules for user review instead of silently dropping or inventing them.
- Replace stable C7 business keys with C8 tags by default. If the key changes during execution, use a `businessKey` process variable instead. Verify any target-specific alternative against the selected version and record the decision in `MIGRATION_REPORT.md`.

---

## Approach M3 - Online Diagram Converter (hosted)

Point user to the hosted converter:

> Upload your BPMN/DMN files at https://diagram-converter.camunda.io/, set the target version there, and download the converted results.

This path does not automate the hosted service. Once the user brings the converted files back into the project, offer the same agentic findings follow-up as in M1 step 5. For the machine-readable findings, use the hosted converter's 'Download JSON' button — it produces the same `analysis-results.json` the CLI writes; its CSV/markdown/XLSX downloads are not parsed (see 5a). The imported-report version check in step 5 applies.

---

## Approach E1 - Camunda 7 Engine Source (only when no local models found)

### 1. Ask for C7 Access

Use AskUserQuestion to request:
- The C7 engine REST base URL, including `/engine-rest` context path when applicable
- Authentication: no authentication or Basic authentication username/password
- Obtain secrets through the agent's secure credential mechanism; never write them to MIGRATION_REPORT.md or commit them
- Caution: the CLI's `--password` flag exposes secrets in shell history; use temporary/dedicated credentials

Also ask whether to fetch all latest process/decision definitions or only named keys.

### 2. Fetch and Convert

For the all-latest case, download/reuse the CLI as in M1, create `.camunda-migration/c7-models`, and invoke:

```
java -Dfile.encoding=UTF-8 -jar <jar> engine <c7-rest-url> --target-directory .camunda-migration/c7-models --platform-version <target-version> --json --xlsx [--username <username> --password <password>]
```

For named-key acquisition, query C7 REST list endpoints with key filters, fetch each definition's `/xml` resource, write XML files to `.camunda-migration/c7-models/`, then run M1 local mode on that directory.

### 3. Handle Failures

Treat unreachable endpoint, TLS/DNS failure, 401/403, malformed XML, or empty response as a blocking error. Report URL, operation, status/error, and concrete next action. Do not silently continue or report success when any requested definition failed.

---

## Analyze-Only Mode

For "analyze but don't convert": run M1 with `--check --json --xlsx` to produce findings and reports with no converted files, or do an M2 read-only pass. Parse and present findings grouped by category as in M1 step 5, and stop.
