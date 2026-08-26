# Model Migration Approaches (Part B)

## Source Selection

Use the model scan from the assessment before choosing a conversion path:
- If local model files were found under the project root, use local mode. Do NOT offer or request C7 engine access.
- If no local model files were found and user selected E1, fetch definitions from C7 first.

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
java -Dfile.encoding=UTF-8 -jar <jar> local <file-or-dir> --platform-version <target-version>
```

Recommended flags:
- `--csv` - always pass this; the CSV report is the machine-readable input parsed in step 5
- `--xlsx` - optional, for human spreadsheet review
- `-o` / `--override` - overwrite pre-existing converted files
- `--check` - analyze-only (no converted diagrams exported)
- `-nr` / `--not-recursive` - disable recursive search

Other options:
- `--prefix <str>` - prefix for generated filenames (default `converted-c8-`)
- `--md` - write analysis report in markdown format

The converter writes a new file next to the source (e.g., `converted-c8-order-process.bpmn`), so originals are never mutated in place.

### 4. Surface Outputs

After the run, report:
- Converted files: list every `converted-c8-*.bpmn` / `*.dmn` produced
- Analysis findings: summarize from CLI stdout and/or CSV/XLSX report, grouped by severity (WARNING / TASK / REVIEW / INFO)
- Analysis artifacts: point user to the CSV file (always) and the XLSX file if generated

Severity counts are only a headline. Do not start per-finding work from them — parse and group the full report first (step 5).

### 5. Follow Up on Findings

REVIEW/WARNING/TASK findings remain and JUEL conversion is partial. Resolve them in the AI follow-up step, working on the `converted-c8-*` copies (never the originals).

Trust the converter's output for what it did NOT flag: job types and listener wiring it emitted are authoritative — apply manual fixes only for what the report flags. Do not second-guess or re-derive converted structures.

On a real project the report can hold thousands of rows but only a handful of distinct categories. Parse the report and group by category first — the category, not the individual row, is the unit of work for follow-up.

#### 5a. Parse the CSV report

Read `analysis-results.csv` programmatically (it is written next to the converted files when `--csv` is passed, which M1 always does). Do not rely on stdout severity counts as a substitute.

Format: `;`-separated, one header row, columns:

```
filename;elementName;elementId;elementType;severity;messageId;message;link
```

If the CSV is missing (e.g. only `analysis-results.md` was generated), re-run the converter with `--check --csv` on the same input. Analyze-only mode writes no converted files and produces the CSV quickly. The markdown report is grouped per element and carries no `messageId` column — it is for human reading, not for parsing.

#### 5b. Group findings by category

Group rows by `messageId` (the category). For each category compute:

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
| `form-reference` | 96 | one `.form` per C7 form (see 5e) | needs fix |

Rules:

- One row per category, sorted as in 5b (highest severity, then count descending).
- The cross-referenced code artifact column names the `@JobWorker`, DMN definition, or other code element the cross-check matched the category to — or `none yet` when no remediation exists. For models-only scope there is no code output to cross-reference: use `n/a` and derive the verdict from severity alone (INFO → no action, REVIEW → needs review, WARNING/TASK → needs fix).
- Every WARNING/TASK/REVIEW category must end up classified — none may be left without a verdict.
- Categories with verdict **needs fix** are the direct work items for the AI follow-up step. Categories with verdict **needs review** are also surfaced there, but only to collect the pending user decision (via AskUserQuestion) before any fix is attempted.

#### 5e. Named category: Forms

C7 form references (`camunda:formKey`, `camunda:formData` / generated forms, embedded forms) surface as findings (typically `form-reference`). Handle them as one category:

- **One C8 form per C7 form.** For every C7 form (generated or otherwise), create a Camunda 8 `.form` and reference it from the user task. Do not drop forms or merge several C7 forms into one.
- **Check what C8 forms do natively before adding a worker.** Many C7 projects carry flattening/computing service tasks that exist only because C7 forms could not bind or compute. Camunda 8 forms removed those limitations:
  - Field `key` supports path-as-key binding into nested variables (e.g. `customerInfo.firstName`) — no flattening worker needed for passthrough fields.
  - `text` components support feelers templating: `{{ }}` interpolation with full FEEL, including `{{#loop}}` — counts, joined lists, and other computed display values belong in the form itself, not in a preceding service task. The JSON property is `text`, not `content` (`content` is for `html`-type components only).
  - A dedicated `documentPreview` component (property `dataSource`, a FEEL expression over an array of document references) renders an inline preview and download link for a Document API reference — prefer it over a plain-text filename display or a hand-built HTML anchor. Wrap the reference in a one-element array here in the form's FEEL only; the process variable itself holds the plain reference.
- **Add a worker only when the form genuinely cannot do it**: real business logic, external calls, side effects. A service task that only reshapes variables for form consumption is a C7 workaround — do not port it.

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
- Conditional events natively only on 8.9+; otherwise flag
- DMN: update decision/definition namespaces and expression language as needed

Emit a findings summary mirroring CLI severities (WARNING/TASK/REVIEW/INFO) and ask for human review. Lint every rewritten BPMN file per the linting section below.

---

## Approach M3 - Online Diagram Converter (hosted)

Point user to the hosted converter:

> Upload your BPMN/DMN files at https://diagram-converter.camunda.io/, set the target version there, and download the converted results.

This path does not automate the hosted service. Once the user brings the converted files back into the project, offer the same agentic findings follow-up as in M1 step 5.

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
java -Dfile.encoding=UTF-8 -jar <jar> engine <c7-rest-url> --target-directory .camunda-migration/c7-models --platform-version <target-version> [--username <username> --password <password>] [--csv] [--xlsx]
```

For named-key acquisition, query C7 REST list endpoints with key filters, fetch each definition's `/xml` resource, write XML files to `.camunda-migration/c7-models/`, then run M1 local mode on that directory.

### 3. Handle Failures

Treat unreachable endpoint, TLS/DNS failure, 401/403, malformed XML, or empty response as a blocking error. Report URL, operation, status/error, and concrete next action. Do not silently continue or report success when any requested definition failed.

---

## Linting Converted BPMN (M1 and M2)

After any manual edit to a converted BPMN file (findings follow-up, form wiring, expression fixes), lint immediately — missing DI, overlapping flows, and disconnected nodes are cheapest to catch at edit time, not at the end.

Use bpmnlint with the Camunda compatibility ruleset matching the target version:

1. Install once in the project (or a scratch directory): `npm install -D bpmnlint zeebe-bpmn-moddle bpmnlint-plugin-camunda-compat`
2. `.bpmnlintrc`:

```json
{
  "extends": [
    "bpmnlint:recommended",
    "plugin:camunda-compat/camunda-cloud-<target-version>"
  ],
  "moddleExtensions": {
    "zeebe": "zeebe-bpmn-moddle/resources/zeebe.json"
  }
}
```

3. Run: `npx bpmnlint <converted-file>.bpmn`

Fix or record every lint error before continuing.

---

## Analyze-Only Mode

For "analyze but don't convert": run M1 with `--check --csv` (optionally `--xlsx`) to produce findings and reports with no converted files, or do an M2 read-only pass. Parse and present findings grouped by category as in M1 step 5, and stop.
