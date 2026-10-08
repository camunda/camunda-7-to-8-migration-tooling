# Model Migration Approaches (Part B)

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## Source Selection

Use the assessment model scan before choosing a path.

- If local model files exist under the project root, use local mode. Never offer or request C7 engine access.
- If none exist and the user selected E1, fetch definitions from C7 first.

Inventory every C7 form from the exact original BPMN before conversion, as `SKILL.md` Step 2 requires. Keep source path, process id, and owner id/type so each definition can be paired with a fresh converted copy. The converter strips generated-form metadata and copies form-key references verbatim, so post-conversion discovery is too late or ambiguous.

## Call-Activity Variable Scope

Compare each C7 call's `camunda:in`, `camunda:out`, and delegated variable-mapping contract with the converted copy.
C7 `camunda:variableMappingClass` and `camunda:variableMappingDelegateExpression` attributes define delegated variable mappings.
These mappings can supply outputs without `camunda:out`.
When either attribute is present, include its contract in the input/output comparison.
If the skill cannot establish a delegated contract, then keep the call **needs review** and ask the
user to decide its scope.
Assign propagation flags only after the skill confirms a compatible C8 mapping for an established contract or user-approved scope.
Camunda 8.9 supports [call-activity input/output mappings](https://docs.camunda.io/docs/components/modeler/bpmn/call-activities/#variable-mappings).
Check the target's support before removing a mapping flagged as unavailable.
Without any variable mappings, C7 passes no variables in either direction.
Camunda 8 copies all variables by default.

| C7 contract | Camunda 8 mapping |
|---|---|
| Selected parent inputs | Set `propagateAllParentVariables="false"` and add a `zeebe:input` for each selected value. |
| No C7 input mappings | Set `propagateAllParentVariables="false"` without input mappings. |
| All parent inputs | Keep all-parent propagation only when C7 sends the same scope. |
| Selected child outputs | Set `propagateAllChildVariables="false"` and add a `zeebe:output` for each returned value. |
| No C7 output mappings | Set `propagateAllChildVariables="false"` without output mappings. |
| All child outputs | Set `propagateAllChildVariables="true"` only when C7 returns the same scope. |
| No compatible mapping | Ask the user to decide the scope. Do not widen selected inputs or outputs. If the approved scope has no compatible C8 mapping, keep the call **needs review** and leave its propagation flags unassigned. |

Record one row per call activity in `MIGRATION_REPORT.md`: its ID, called process, C7 inputs and
outputs, C8 mappings and propagation flags, child identity intent, and validation evidence.
The [Business ID](https://docs.camunda.io/docs/components/concepts/process-instance-creation/#business-id)
passes to a call-activity child in 8.9 independently of variables. If the C7 child needs a distinct
business key, then keep that difference **needs review** until the user selects an alternative.

Test with a selected input and an extra parent-only variable. Check that the child receives only
the selected input, and check its Business ID separately. If deployment blocks the test, then
record the blocker and keep scope parity **needs review**.

## Pre-flight: Leftover Artifacts

Before any local approach (M1, M2, E1), scan for outputs of previous migration attempts:

- `converted-c8-*.bpmn` / `converted-c8-*.dmn` (or the `--prefix` equivalent)
- accepted generated forms beside converted BPMN, and drafts under `.camunda-migration/generated-form-drafts/`
- `analysis-results.<ext>` and `analysis-results (n).<ext>` findings reports, where `n` is a positive integer and `<ext>` is `.csv`, `.json`, `.md`, or `.xlsx`

Never flag the `.camunda-migration/` CLI JAR — an intentional cache, not a leftover.

A packaged resource directory is any resource directory that Maven or Gradle includes in an application artifact. Include `src/main/resources` when it exists.

Treat report files already under `.camunda-migration/reports/` as intentional non-packaged artifacts
only after confirming that the build does not package that directory. If the build packages that
directory, treat its findings reports as packaged artifacts and stop before conversion. Otherwise,
do not include them in the packaged-resource warning. Never consume local reports already there as
this run's reports.

If a findings report exists under a packaged resource directory, stop before conversion and ask the user to move or remove it. Do not offer **OK, proceed** while it remains there.

If anything else is found, warn the user before converting:

> Found outputs from a previous migration attempt: `<list>`. This run will not overwrite them. Fresh findings reports are written beside the source. The CLI adds a ` (n)` suffix only when the unsuffixed name already exists. Relocate fresh findings reports to `.camunda-migration/reports/` when that directory is not packaged, or to another explicitly non-packaged directory, before validation. Only this run's own outputs are used — stale files are never consumed. Diagrams whose `converted-c8-*` target already exists are skipped with an error, so for a full re-conversion, cancel and delete or move the old files first.

- **OK, proceed** — when no findings report remains under a packaged resource directory, run without `-o`/`--override`. Old files stay untouched.
- **Cancel** — stop so the user can back up or clean up first.

For local approaches (M1, M2, E1), never consume a pre-existing report or converted file found on disk. It may come from an interrupted attempt or a different `--platform-version`. The findings flow (M1 steps 3-5) works only from this session's own run. M3 is the exception: hosted-converter outputs are allowed only after the imported-report version and pairing checks in step 5.

## Approach M1 - Diagram Converter CLI + AI (recommended)

### 1. Java 21+ Prerequisite (fail fast)

Validate the CLI runtime with the Java runtime procedure in `SKILL.md` before the download.
The Diagram Converter CLI supports Java 21 or later with no upper bound.
Record `Java 21+ (no upper bound)`, the executable path, and the actual major in `MIGRATION_REPORT.md`.
Use the validated absolute executable for every CLI invocation.
Never replace it with bare `java` or another executable.
If no Java 21+ runtime is available, then show this message:

> The Diagram Converter CLI requires Java 21 or later. Detected: `<major version or "not found">`.
> Provide a Java 21+ home, choose M2 (agentic AI), or choose M3 (online converter).

Never silently skip model migration.

### 2. Resolve Latest Release and Download CLI

The CLI is published as a self-contained executable JAR named `camunda-7-to-8-diagram-converter-cli-<tag>.jar` on the GitHub releases for `camunda/camunda-7-to-8-migration-tooling`.

1. Determine the latest release tag.
2. Ensure `.camunda-migration/` exists in the project root.
3. Compute the target path: `.camunda-migration/camunda-7-to-8-diagram-converter-cli-<tag>.jar`.
4. If that JAR exists and its `local --help` lists `--json`, reuse it.
5. Otherwise download from `https://github.com/camunda/camunda-7-to-8-migration-tooling/releases/download/<tag>/camunda-7-to-8-diagram-converter-cli-<tag>.jar`.
6. Run `local --help` with the validated Java executable and confirm that the selected JAR lists `--json`.

The JAR is ~30 MB. The skill records its release tag and exact path with the target version in
`MIGRATION_REPORT.md`. A latest release tag alone does not prove that the selected artifact supports
each detected model pattern. Where the project is a git repo, the skill recommends adding
`.camunda-migration/` to `.gitignore`. (SHOULD) The skill modifies `.gitignore` only after the user
confirms.

Release 0.3.6 does not support `--json`. Release 0.3.7 introduced this option.
If the latest release does not list `--json`, stop and report a CLI capability blocker.

### 3. Run the Converter

The CLI local subcommand accepts a single file or a directory (recursive by default).
Always pass `--platform-version` set to the target version from the interview.

```
"<java-executable>" -Dfile.encoding=UTF-8 -jar "<jar>" local "<file-or-dir>" --platform-version "<target-version>" --json --xlsx
```

On Windows PowerShell, prefix the command with the call operator: `& "<java-executable>" ...`. Set `<java-executable>` to the validated absolute path from step 1.

Recommended flags:
- `--json` - always pass this. Step 5 reads this report as JSON.
  If the selected JAR rejects it, record the release tag and exact error.
  Classify this as a CLI capability failure, not a Java failure.
- `--xlsx` - always pass this. The XLSX report is the human-readable report for reviewing and sharing findings with the customer.
- `-o` / `--override` - overwrite pre-existing outputs in place. Destructive — do not pass by default (see Pre-flight: Leftover Artifacts). Without it, a diagram whose converted target already exists is skipped with a `File already exists` error, and reports are written under ` (n)`-suffixed names.
- `--check` - analyze-only (no converted copies exported)
- `-nr` / `--not-recursive` - disable recursive search

Other options:
- `--prefix <str>` - prefix for generated filenames (default `converted-c8-`)
- `--md` - write analysis report in markdown format

The converter writes a new file next to the source (e.g., `converted-c8-order-process.bpmn`), so originals are never mutated in place.

Capture the exact paths of everything the run produces from the `Created ...` lines in the CLI console output (e.g. `Created analysis-results (1).json`). These paths are authoritative until the report relocation below completes. Never glob for `analysis-results.json` or `converted-c8-*` on disk, which may match stale files from a previous attempt or a different `--platform-version`.
Record the exact command, exit code, stdout, and stderr in `MIGRATION_REPORT.md`.
If the Java preflight passed and the CLI exits nonzero, report the CLI error without changing the Java verdict.

### 3a. Relocate findings reports

The CLI writes converted copies and findings reports beside the input. A report under a packaged
resource directory is included in a Maven or Gradle application artifact.

After the CLI exits, create `.camunda-migration/reports/` in the project root when the build does
not package that directory. Otherwise, create another explicitly non-packaged reports directory in
the project root. Move every fresh findings report captured from a `Created ...` line into that
directory before validation, including ` (n)`-suffixed names. Keep every `converted-c8-*` file beside
its source model.

Do not overwrite an existing file in the chosen reports directory. Choose an available ` (n)`-suffixed
name and use the moved path as the authoritative report path. If relocation fails, stop model
validation and report the error. Do not claim a complete migration.

### 3b. Check the selected artifact

Match the source start-listener inventory against the selected JAR's JSON report.
For directory input, normalize path separators before matching the full path relative to the input directory.
For single-file input, match the filename.
Never use a basename match for a directory input.
Require `messageId` `execution-listener-on-start-event` and `TASK` severity.
Match the start-event ID and implementation type/value in the finding message.
Count repeated implementations separately.
If a finding cannot map to one source listener, then block automatic compatibility.

Use these values when matching a source listener to a finding:

| Source listener implementation | Finding message type | Finding message value |
|---|---|---|
| `camunda:class="value"` | `class` | Attribute value |
| `camunda:expression="value"` | `expression` | Attribute value |
| `camunda:delegateExpression="value"` | `delegateExpression` | Attribute value |
| Nested `camunda:script` with `scriptFormat="value"` | `script` | Script format |
| No implementation attribute or nested script | `null` | `null` |

Parse every fresh converted copy with a namespace-aware XML parser.
Reject a direct `zeebe:executionListener eventType="start"` on any `bpmn:startEvent`.

| Evidence from this run | Action |
|---|---|
| One matching `TASK` finding per source listener and no invalid placement | Complete step 5d.3 for affected listeners, then validate the target. |
| A finding is missing, downgraded, duplicated, or unmatched | Block automatic compatibility. Add a source-derived `TASK` finding in `MIGRATION_REPORT.md` for each uncovered listener. Do not add it to the converter JSON report or count it as a converter match. |
| Invalid placement has no matching C7 source listener | Block model readiness. Manual relocation cannot repair an unknown source listener. |
| A converted copy retains invalid placement | Block automatic compatibility even when a worker exists. |

If either check fails, then keep model readiness blocked and offer a patched release or manual follow-up.
For a patched release, move this run's copies outside packaged directories before reconversion.
Follow the leftover-artifact pre-flight and rerun all original models with the same target and options.
Repeat these checks and use only the new run's findings reports and converted copies.
Never overwrite existing files or mix results from different releases.
Apply step 5d.3 only to inventoried listeners after user approval.
Keep model readiness blocked until that follow-up and target validation pass.

### 4. Surface Outputs

After the run and report relocation, report:
- Converted files: every `converted-c8-*.bpmn` / `*.dmn` produced (from the captured `Created ...` lines).
- Skipped files: any `File already exists` errors, naming the stale targets. Those diagrams were NOT converted. Offer to re-run once the user removes the stale copies (see Pre-flight: Leftover Artifacts).
- Analysis findings: summarize from CLI stdout and/or the JSON report, grouped by severity (WARNING / TASK / REVIEW / INFO).
- Analysis artifacts: point the user to the relocated XLSX report (human-readable), and note the relocated JSON report is the step 5 input.
- Generated Task Forms: source owners discovered before conversion. They stay manual `form-data` follow-up items until the form procedure completes.
- Referenced forms: the embedded, external, Camunda Form, dynamic, and form-free owners discovered before conversion. They stay open until their category decision and follow-up work complete.

Severity counts are only a headline. Never start per-finding work from them. Parse and group the full report first (step 5).

### 5. Follow Up on Findings

REVIEW/WARNING/TASK findings remain and JUEL conversion is partial. Resolve them in the AI follow-up step, working on the `converted-c8-*` copies, never the originals.

Trust unflagged converter output except source start listeners from step 3b and inventoried
call-activity variable scope.
The job types and listener wiring it emitted are authoritative elsewhere.
Apply manual fixes only for findings, inventoried start listeners, or call-activity scope mismatches.
Never second-guess or re-derive other converted structures.

#### Verification gate

Never set a model-finding category/impact row's verdict to **no action** or report it as resolved
until this gate passes.
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
| Converter regression | When the original input, CLI release, and recorded options are available, run `local <original-input> --check --csv`. The `--check` mode exports no converted copy, so the run is supplementary evidence. It does not compare an edited converted copy with its source or prove a manual remediation. | Command and relevant CSV rows |
| Deployment readiness | After a model or deployment change, repeat the resource and target checks in "Model validation" before reporting model readiness. | Resolved patterns, packaged entries, target version, and per-resource results |

Record one verification row per category/impact row with its check results and `pending`, `passed`, or
`failed` state.
Mark verification `passed` only when every applicable check passes.
If a check fails, re-open the row as **needs fix**.
If a check cannot run or a user decision remains, keep the row **needs review**.
In analyze-only mode, keep every model category/impact row **needs review**.
Never start an automatic remediation loop.

#### Imported reports: verify the target platform version

Skip this check for this skill's own CLI run: it already passed the chosen `--platform-version`, and leftover local reports are never consumed (see Pre-flight: Leftover Artifacts).

This check fires only for a report deliberately imported without a fresh run — generated earlier, by someone else, or downloaded from the hosted converter (M3). Only a JSON report is consumable (see 5a — there is no CSV parsing path). If the import is CSV, markdown, or XLSX only, re-run the CLI locally with `--check --json --xlsx` on the input models. For an imported JSON report, confirm it was generated for the chosen target version before consuming it. Findings are version-dependent. Conditional events are flagged unsupported in a report targeting 8.6, but are native since 8.9. A stale report can send the user chasing findings that do not apply to their target.

Determine the report's target version:

1. Findings with `messageId` `element-available-in-future-version` name it. The message reads `Element '<name>' is not supported in Zeebe version '<report-target>'. It is available in version '<x.y>'.` — `<report-target>` is the version the report was generated against.
2. Otherwise the version cannot be determined from the content. Ask the user which `--platform-version` generated the report.

If the report's version does not match the chosen target, or cannot be determined, warn the user and offer these options before grouping (5b) or any cross-checks:

- **Re-run the converter at the chosen target** (recommended) — run the step 2 CLI with `--check --json --xlsx --platform-version <target-version>` on the same input. Analyze-only mode is fast and produces fresh JSON and XLSX reports for 5a.
- **Keep the imported report** (MAY) — use it only for non-runtime grouping. Record the target
  mismatch or unknown target in `MIGRATION_REPORT.md`. If the original input is available, re-run
  the converter at the chosen target without `--check`. Use its JSON report for runtime impact and
  the verdict table. Use its converted copy for target-support tests. Otherwise, request the input
  from the user and stop.

#### 5a. Parse the JSON report

Read the JSON report programmatically at the authoritative path. For a local M1 or E1 run, use the
path captured after step 3a relocation. For an imported M3 report, use the downloaded JSON path
after the version and pairing checks in step 5. The local path may include a ` (n)` suffix when a
stale report exists. Never parse a pre-existing local findings report found on disk. Never rely on
stdout severity counts instead.

Format: a JSON array with one object per finding, fields:

```
filename, elementName, elementId, elementType, severity, messageId, message, link
```

A future converter can define a documented runtime-impact JSON field. Preserve it when present.
Parse it with real JSON tooling (e.g. `jq` or a built-in JSON parser), never ad-hoc string splitting.

If the JSON report is missing (e.g. only `analysis-results.md` or a CSV/XLSX was generated), re-run
the converter with `--check --json --xlsx --platform-version <target-version>` on the same input.
Capture the fallback run's `Created ...` paths and apply step 3a before parsing. The markdown and
XLSX reports are for humans. CSV is never
consumed — this skill has no CSV parsing path, and the JSON report is the only machine-readable
findings source.

#### 5b. Group findings by category

Group findings by `messageId` (the category). For each category compute:

- Total count, and count per severity.
- Distinct `elementType` values affected (e.g. serviceTask, sequenceFlow, multiInstanceLoopCharacteristics).
- One representative example: a `message` with its `filename` and `elementId`.
- The `link` to conversion guidance for that category.

Sort categories by highest severity (TASK > WARNING > REVIEW > INFO), then count descending.

Write one local artifact at `.camunda-migration/findings-by-category.json` when that directory is
not packaged.
Otherwise write it in another explicitly non-packaged local directory.
Record its actual path in each M1 converter category's Element list cell. Never commit it.
For each M1 run, generate it from the current parsed JSON report.
Never use a pre-existing artifact as input.
The JSON object maps each `messageId` category to every finding in that category.
Never add a source inventory entry to this artifact.
Keep only `filename`, `elementId`, `elementType`, `message`, and a documented runtime-impact field when present.

#### 5c. Present the grouped summary

Present the grouped table before any per-finding follow-up starts, and record it in MIGRATION_REPORT.md:

| Category (messageId or source category) | Severity | Count | Element types | Example |
|---|---|---|---|---|
| `expression-method-not-possible` | REVIEW | 1,308 | sequenceFlow, exclusiveGateway | "Method invocation is not possible in FEEL: ..." in order-process.bpmn, element `Gateway_1` |

#### 5d. Emit a per-category verdict table

Before assigning verdicts, compare each finding `messageId` with the dedicated cross-check rules.
The current dedicated cross-check categories are:

| Category | Dedicated cross-check |
|---|---|
| `delegate-expression-as-job-type`, `delegate-implementation` | Check the 1:1 and many-to-one job-type mappings in `composing-code-and-models.md` |
| `expression-method-not-possible` | Check the FEEL method-invocation remediation |
| `collection-hint` | Check for now-redundant workaround code |
| `element-available-in-future-version` | Verify the report target version |
| `element-not-supported-hint` | Verify target support for the affected element |
| `conditional-flow` | Verify target support for the converted flow and condition |
| `execution-listener`, `execution-listener-supported` | Match listener implementations during the workaround and listener cross-checks |
| `execution-listener-on-start-event` | Ask for confirmed relocation to the nearest enclosing process or subprocess, then verify the converted listener |

The form procedures in 5f are also dedicated handling for their named form categories.
Treat every other category as a fallback category.

#### 5d.1. Classify runtime impact

Add `Runtime impact` to each verdict-table row before its verdict. It states whether the selected
target can deploy and execute the affected element or condition. Do not derive runtime impact from
severity.

Where a documented converter runtime-impact field is present, use its value. Record its name and
value in `Impact evidence`. Do not infer an undocumented field. Otherwise, apply the first matching
rule in this table.

For target support, deploy every fresh converted copy named by the row. Record each target,
deployment identifier, and result in `Impact evidence`. After a deployment succeeds, start
instances that reach every Element list ID from that copy. Record each execution result.

While a conditional event's scope is active, make its condition evaluate to `true`.
Check that the event path executes. A deployment or linter pass alone does not prove that the event
fired.
Record the target version, deployment identifier, event ID, trigger inputs, and execution result in
`Impact evidence`. If a safe Camunda 8.9+ cluster is unavailable, keep the row **needs review**.

For `element-not-supported-hint` and `conditional-flow`, use **Advisory** and **no action** only
after the target-support test and verification gate pass. If a test failure identifies an affected
element or condition, use **Blocking** and **needs fix**. If the test is unavailable, incomplete,
or blocked by another failure, use **Blocking** and **needs review**.

| Category or condition | Runtime impact | Evidence |
|---|---|---|
| `element-not-supported` | **Blocking** | The target cannot deploy or execute the element. |
| `element-not-supported-hint` or `conditional-flow` after a passed target-support test | **Advisory** | The target supports the affected element or condition. |
| `element-not-supported-hint` or `conditional-flow` without a passed target-support test | **Blocking** | Target support is not confirmed. |
| `element-available-in-future-version` below the required target | **Blocking** | The required target is unavailable. |
| `element-available-in-future-version` at or above the required target | n/a | Omit the verdict-table row. |
| `delegate-implementation-no-default-job-type` or `delegate-expression-as-job-type-null` | **Blocking** | The job type is blank or missing. |
| A job-worker activity without a nonblank task type | **Blocking** | The job cannot execute. |
| A confirmed job-type or dispatcher mapping that is incomplete or mismatched for `delegate-expression-as-job-type` or `delegate-implementation` | **Blocking** | The job cannot execute. |
| `delegate-expression-as-job-type` or `delegate-implementation` without worker-route evidence | **Blocking** | Keep the row **needs review**. |
| `camunda-script` | **Blocking** | The converter did not transform the script. |
| `script` or `script-job-type` without worker-route evidence | **Blocking** | The task cannot execute. |
| `resource-on-conditional-flow`, `script-on-conditional-flow`, `resource-on-conditional-event`, or `script-on-conditional-event` | **Blocking** | The condition cannot execute. |
| `timer-expression-not-supported`, `inclusive-gateway-join`, or `loop-cardinality` | **Blocking** | The element cannot retain its execution semantics. |
| `execution-listener-on-start-event` | **Blocking** | The target rejects the listener placement until the user accepts a relocation and the relocation checks pass. |
| `in-out-business-key` | **Advisory** | The converter maps a supported process business key. |
| `in-out-business-key-not-supported` | **Advisory** | The call activity loses business-id propagation. |
| A form category, including `form-data`, `generated-form-property-source`, or form references | **Advisory** | Form work is not a deployment or execution blocker. |
| Another known category with nonblocking evidence. An emitted job needs worker/connector-route evidence. A condition needs execution evidence. | **Advisory** | Record the evidence. |
| Another category | **Blocking** | No evidence confirms safe deployment and execution. |

**Blocking** in a form procedure means migration-blocking, not runtime-blocking.

For a fallback category, assign the default verdict from the finding severity:

| Severity | Default verdict |
|---|---|
| INFO | needs review until the verification gate passes |
| REVIEW | needs review |
| WARNING or TASK | needs fix |

Set the cross-referenced code artifact to **no dedicated cross-check** for a fallback category.
Add the finding `link` to the `Link` column and surface it as the remediation starting point.
Apply the same fallback when a report contains a category that is absent from the inventory below.
Never infer a category-specific cross-check from the category name or message text.

#### 5d.2. Converter category inventory

This inventory records the `messageId` values produced by `MessageFactory` in converter version
`0.3.6-SNAPSHOT`. It is the known-category list, not a list of dedicated cross-checks:

```text
all-in-signal-event, attribute-not-supported, attribute-removed, called-element-ref-binding,
called-element-ref-version-tag, camunda-script, collection, collection-hint, condition-expression-feel,
conditional-event-definition-generated-id, conditional-flow, connector-hint, connector-id,
correlation-key-hint, data-migration-listener-added, decision-ref-binding, decision-ref-version-tag,
delegate-expression-as-job-type, delegate-expression-as-job-type-null, delegate-implementation,
delegate-implementation-no-default-job-type, delete-variable-event-not-supported,
element-available-in-future-version, element-not-supported, element-not-supported-hint, element-variable,
error-code-no-expression, error-event-definition, escalation-code-no-expression, execution-listener,
execution-listener-field, execution-listener-on-start-event, execution-listener-supported, expression,
expression-execution-not-available,
expression-method-as-job-type, expression-method-not-possible, failed-job-retry-time-cycle,
failed-job-retry-time-cycle-error, failed-job-retry-time-cycle-removed, field-content,
form-already-camunda-8, form-component-unknown, form-data, form-juel-expression,
form-key-camunda-form, form-key-embedded, form-key-expression, form-key-external, form-ref-binding,
form-schema-version-missing, form-schema-version-outdated, in-all-hint, in-out-business-key,
in-out-business-key-not-supported, inclusive-gateway-join, input-output-parameter-feel-script,
input-output-parameter-is-no-expression, input-variable-not-supported, internal-script,
job-priority-collision, local-variable-propagation-not-supported-hint, modeler-template,
loop-cardinality, modeler-template-version, number-type, old-in-all-hint, only-feel-supported, out-all-hint,
potential-starter, priority-invalid, priority-not-migrated, priority-scales-merged, property,
resource, resource-on-conditional-event, resource-on-conditional-flow, result-variable-business-rule,
result-variable-internal-script, result-variable-rest, script, script-format, script-job-type,
script-on-conditional-event, script-on-conditional-flow, task-listener, task-listener-supported,
timer-expression-not-supported, topic, user-task-priority-collision, user-task-priority-not-migrated,
variable-name-filter-not-supported, version-tag
```

After grouping and runtime-impact partitioning, assign each category/impact row exactly one verdict.
Include INFO categories.
When code is in scope, complete the code cross-checks before assigning the verdict.
Record the table in `MIGRATION_REPORT.md`.
Never leave findings as severity counts or a generic "findings need follow-up" note.
For each M1 **needs fix** or **needs review** row, reference its complete element list.
The grouped summary identifies the category.

Verdicts:

| Verdict | Meaning | Required action |
|---|---|---|
| **no action** | The converter handled the category deterministically or a cross-check shows full coverage. The shared verification gate passed. | Nothing to do. |
| **needs review** | A human decision or verification is pending. A user decision is required before any fix starts. | Collect the pending user decision before any fix. Run the verification gate directly when it is the only pending action. |
| **needs fix** | Concrete, known work remains: an uncovered cross-check item (job-type mismatch, uncovered retained header key and original expression pairs, uncovered invoked methods) or a WARNING/TASK category with a clear remediation. | It is a direct work item for the AI follow-up step. Resolve one verdict-table row at a time, using that row's cross-check guidance. |

| Category (messageId or source category) | Runtime impact | Count | Element list | Code artifact | Impact evidence | Link | Verdict |
|---|---|---|---|---|---|---|---|
| `element-not-supported` | Blocking | 12 | `.camunda-migration/findings-by-category.json`: `element-not-supported` | none yet | Category rule | `<finding link>` | needs fix |
| `delegate-expression-as-job-type` (uncovered mappings) | Blocking | `<count>` | `.camunda-migration/findings-by-category.json`: matching IDs | `DelegateDispatcher` | Cross-check: route missing | `<finding link>` | needs fix |
| `delegate-expression-as-job-type` (covered mappings) | Advisory | `<count>` | `.camunda-migration/findings-by-category.json`: matching IDs | `DelegateDispatcher` | Cross-check: all routes exist | `<finding link>` | no action |
| `form-data` | Advisory | `<count>` | `.camunda-migration/findings-by-category.json`: `form-data` | one `.form` per form | Form procedure | `<finding link>` | needs fix |

Rules:

- Use one row per category and runtime impact. Each split row lists its matching element IDs.
- Sort verdict-table rows by runtime impact: **Blocking**, then **Advisory**.
- Within each impact, sort rows by highest severity and count.
- Within each impact, put source-derived rows without a severity after rows with one.
- For an M1 converter category, Element list names the actual artifact path and the category key.
- For an M1 source-derived category, Element list names its category in the source inventory in `MIGRATION_REPORT.md`.
- The Code artifact column names the matched `@JobWorker`, DMN definition, or other code element.
- Impact evidence names the rule, target-support test, or cross-check that set runtime impact.
- Write `none yet` when no remediation exists.
- For models-only scope, write `n/a`.
- Use the preceding severity table for a converter finding.
- Apply the procedure-defined lifecycle to source-derived synthetic categories.
- Apply it to `c7-*` categories that split a legacy generic `form-key` finding.
- These categories have no independent converter severity.
- Copy each finding's `link` into the `Link` column.
- Keep each `form-data` and `generated-form-property-source` row **needs fix** until `form-migration.md` completes.
- Form *reference* categories are never **no action** just because the converter copied the reference. `form-reference-migration.md` defines their names and verdict lifecycle.
- Use one or more rows for each category in this run. Keep specific form-key categories separate.

#### 5d.3. Relocate unsupported start-event listeners

Treat each source `event="start"` listener on a `bpmn:startEvent` as **Blocking** and **needs review**
until the user accepts relocation and its checks pass.
Resolve its source path and event ID from the inventory, even if the selected JAR omitted its finding.
Read the original C7 source because an older JAR may drop the listener or emit invalid placement.

Find the nearest enclosing target in the original source:

| Source shape | Target |
|---|---|
| Process-level start event | The enclosing `bpmn:process` |
| Embedded or event-subprocess start event | The nearest enclosing `bpmn:subProcess` |
| Multiple start events share the target | Tell the user that the moved listener runs for every start in that target scope |

Resolve the target platform version before offering relocation:

| Target version | Action |
|---|---|
| `8.6` or later | Offer the process or subprocess relocation |
| Earlier than `8.6` | Do not create a `zeebe:executionListener`; keep the finding **needs review** and offer manual migration |
| Missing or invalid | Ask for a target version; keep the finding **needs review** until the version is confirmed |

Present one decision for each affected start event or group with the same target:

| User choice | Action |
|---|---|
| **Move the listener to the enclosing process or subprocess** | Recreate the equivalent Camunda 8 listener on the target scope |
| **Keep it as a manual migration task** | Do not edit the converted copy and keep the finding **needs review** |

Do not edit the original C7 source.
Do not edit a converted copy before the user accepts the move.
When the user accepts the move, edit only the fresh converted copy.
Remove any invalid listener on the selected start event before adding its approved replacement.
Never remove listeners from another start event.
Create or reuse the target's `bpmn:extensionElements` and `zeebe:executionListeners` elements.
Recreate every affected `camunda:executionListener` as a `zeebe:executionListener` on the target scope.
Set `eventType="start"`.
Use the converter's existing implementation-to-type mapping.

| Camunda 7 source | Camunda 8 listener |
|---|---|
| `delegateExpression="${name}"` | `type="name"` |
| `class="name"` or `expression="name"` | `type="name"` |
| `event="start"` | `eventType="start"` |
| Static listener fields supported by the converter | `zeebe:taskHeaders` entries |

Preserve every listener field and attribute that the converter maps to the Camunda 8 listener.
Record each unmapped field or attribute as a migration TODO.
Keep the category **needs review** when a field or attribute is unmapped.
If the original listener has no implementation that the converter maps, then keep the category **needs review**.
Move only listeners belonging to the selected start event.

After an accepted move, run the relocation checks below on the affected converted copy.
Do not block this category on an unrelated project test failure.
Run the Step 4 test suite only when the same batch changed code.
Run the target-support deployment and execution checks from 5d.1 for the affected converted copy.
Record the deployment identifier and execution result.
Keep the category **needs review** when deployment or execution cannot run.
Confirm that the converted copy parses.
Confirm that the start event has no unsupported start listener.
Confirm that the target scope has the recreated listener.
Confirm that the listener's worker or connector route remains covered.
Keep the category **needs review** until every applicable check passes.

Record the source path, start-event ID, target process or subprocess ID and name, listener
implementations, user decision, converted-copy path, and verification evidence in
`MIGRATION_REPORT.md`. Set the verdict to **no action** only after the relocation checks and any
applicable verification gate pass. If the user declines or a required check cannot run, then keep
model readiness **blocked**.

#### 5e. Strip converter annotations from converted models

After every finding has a verdict, remove the temporary converter annotations from the fresh `converted-c8-*` copies. The verdict table and `MIGRATION_REPORT.md` are the durable record. Never leave the report embedded in the deployable model.

For each converted BPMN/DMN file:

- Remove every `conversion:*` element, including `conversion:message`, `conversion:reference`, and `conversion:referencedBy`. Remove `conversion:*` attributes such as `conversion:converterVersion`.
- Remove the `conversion` namespace declaration after no `conversion` element or attribute remains.
- Remove empty `bpmn:extensionElements` left behind by the annotation removal.
- Remove `xmlns:camunda` (or another declaration for the C7 BPMN (`http://camunda.org/schema/1.0/bpmn`) or DMN (`http://camunda.org/schema/1.0/dmn`) namespace) only when no remaining element, attribute, or QName-valued attribute uses that namespace. Preserve and report any genuine remaining C7 QName instead of making it undeclared.
- Remove a BPMN definitions-level `expressionLanguage` attribute when it is the leftover C7 XPath declaration. Do not remove a valid DMN expression language or an expression attribute before resolving its finding.

Reparse every cleaned file. Fail the cleanup if it is not well-formed, or if any `conversion:*` node or attribute or unused C7 namespace declaration remains. Run this step before model validation and before linking or deploying generated forms.

#### 5f. Generate and review Camunda 8 forms

Run `form-migration.md` for every source Generated Task Form from the pre-conversion inventory.
Never infer a form from a `form-data` message. Never mark the finding resolved merely because the
converter removed it.

Then run `form-reference-migration.md` for every referenced form (embedded, external, Camunda Form,
dynamic) and for every form-free owner.

#### 5g. Camunda 8 form capabilities

- **One C8 form per C7 form.** For every C7 form the user chooses to migrate, create a C8 `.form` and reference it from its owning user task or start event. Never drop forms or merge several C7 forms into one.
- **Check what C8 forms do natively before adding a worker.** Many C7 projects carry flattening/computing service tasks that exist only because C7 forms could not bind or compute. C8 forms removed those limitations:
  - Field `key` supports path-as-key binding into nested variables (e.g. `customerInfo.firstName`), so no flattening worker is needed for passthrough fields.
  - `text` components support FEEL templating: `{{ }}` interpolation with full FEEL, including `{{#loop}}`. Counts, joined lists, and other computed display values belong in the form itself, not in a preceding service task. The JSON property is `text`, not `content` (`content` is for `html`-type components only).
  - A dedicated `documentPreview` component (property `dataSource`, a FEEL expression over an array of document references) renders an inline preview and download link for a Document API reference. Prefer it over a plain-text filename display or a hand-built HTML anchor. (SHOULD) When the process variable holds one document-reference object, wrap it in a one-element array in the form's FEEL only. Never change the variable to a form-specific array or a filename.
- **Add a worker only when the form genuinely cannot do it**: real business logic, external calls, side effects. A service task that only reshapes variables for form consumption is a C7 workaround. Never port it.

## JUEL Method-Invocation Worker Adapters

For every model approach, apply the worker-adapter rule in `code-transform-checklist.md` item 7 to a
Spring bean method invoked by JUEL. E1 uses the M1 local conversion flow after it acquires the source
models.

## Approach M2 - Agentic AI (direct XML rewrite)

Use when Java 21 is unavailable, the user wants to review every change, or the CLI cannot handle a case.

Fetch the current diagram-conversion guidance listed in `pattern-catalog-sources.md`.

### Job type naming

Use the Diagram Converter naming rules as the binding convention for M2. Apply the rule that matches
the original Camunda 7 implementation attribute.

| Camunda 7 source | Job type rule | Example |
|---|---|---|
| `camunda:delegateExpression` or `camunda:expression` with a bean reference | Remove the `${...}` or `#{...}` wrapper. Keep the first path segment unchanged. Capitalize the first character of each later path segment. | `${sampleBean}` becomes `sampleBean`. |
| `camunda:delegateExpression` or `camunda:expression` with a method invocation (expression method) | Unwrap the expression. Replace each `.` with an uppercase first character of the following segment. Remove the `(...)` argument list after the camel-case transformation. | `${sampleBean.someMethod(x)}` becomes `sampleBeanSomeMethod`. |
| `camunda:class` | Take the class name after the final dot. Decapitalize its first character. | `com.example.SampleDelegate` becomes `sampleDelegate`. |
| `camunda:topic` on an external task | Copy the topic value without changing it. | `invoice-processing` remains `invoice-processing`. |

For JUEL method-invocation findings, apply the [worker adapter rule](#juel-method-invocation-worker-adapters).

Treat a job type that differs from this table as an intentional deviation only when
`MIGRATION_REPORT.md` records the source file and element, the original implementation, the emitted
job type, and the confirmed rationale. Record the same decision for a custom or shared job type that
has no source binding in the table. Do not replace a method-specific type with the bean-only type.

For each in-scope diagram, produce a new `converted-c8-<name>.bpmn`/`.dmn` (never edit the original), applying:

- `camunda:` namespace/extension elements to `zeebe:` equivalents (task definitions/job types, IO mappings, headers)
- Where the converted BPMN uses a `zeebe:` element or attribute, reuse an existing `zeebe` declaration on `bpmn:definitions` or declare `xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"` there before writing the converted copy.
- Convert each call activity's variable contract, including a delegated variable-mapping contract, with [Call-Activity Variable Scope](#call-activity-variable-scope).
- Where the target version is 8.5 or later, convert every Camunda 7 `bpmn:userTask` to a Camunda 8 user task. Ensure that the task has a `bpmn:extensionElements` container. Create the container when it is missing, then add exactly one `<zeebe:userTask />` child.
- Where the target version is 8.5 or later and the user task is form-free, still add `<zeebe:userTask />`. Do not infer a job-worker task from the absence of form metadata.
- Where the target version is 8.5 or later, preserve compatible assignment, schedule, form, and task-listener metadata in the corresponding Zeebe extensions.
- If any user-task semantic is unsupported, then preserve the task as a Camunda user task and record a finding with the source element and required manual action. Never silently replace that task with a legacy `io.camunda.zeebe:userTask` job.
- Where the target version is before 8.5, do not add `<zeebe:userTask />`. Preserve the source implementation and record that modern user-task support is unavailable.
- remove C7 generated-form elements from the converted copy after their source inventory is captured. `form-migration.md` creates separate standard `.form` resources.
- Execution/task listeners to `zeebe:executionListeners` / user task listeners
- JavaDelegate/expression references to job types (or blank, to be filled)
- Simple JUEL to FEEL for pure data expressions. Flag bean-invoking expressions for manual work.
- Never translate complex script or Groovy condition logic into FEEL automatically. Preserve the source for review, and require an explicit worker/service-task or other user-approved redesign.
- Conditional events are native only on 8.9+. Otherwise flag them.
- Where the target is Camunda 8.9 or later, meet the conditional-event ID condition of the
  [verification gate](#verification-gate). If a source definition ID is empty or nonunique, then
  generate a collision-free ID without changing existing event IDs, sequence-flow IDs, or BPMN DI
  references.
- Run the conditional-event ID check independently of BPMN lint.
- Reject the converted copy if the ID check fails, even when lint reports no ID error.
- DMN: update decision/definition namespaces and expression language as needed
- Preserve existing BPMN DI instead of reconstructing it from the rewritten semantic tree.
- Before rewriting, parse the source with a namespace-aware XML parser and record counts for diagrams, planes, shapes, edges, labels, bounds, and waypoints.
- Preserve every BPMN DI node, attribute, child geometry, BPMN DI namespace binding, and `bpmnElement` reference for unchanged semantic IDs.
- When a semantic ID changes, update each matching DI reference and record the source ID, target ID, and mapping in `MIGRATION_REPORT.md`.
- If a semantic ID has no safe DI mapping, record a blocking or review finding before continuing.
- When the source has no BPMN DI, leave the converted copy without BPMN DI and record that provenance in `MIGRATION_REPORT.md`. Do not manufacture a layout.

Emit a findings summary mirroring CLI severities (WARNING/TASK/REVIEW/INFO), and ask for human review. Lint every rewritten BPMN file per the linting section below.

Before resolving the model findings, check every converted `bpmn:userTask` against the user-task
rules above. No `zeebe:taskDefinition` exists on a converted user task unless the user explicitly
selected a job-based replacement and `MIGRATION_REPORT.md` records the decision.

The user may explicitly request a job-based replacement for a user task. Record the request, the source task id, and the resulting job type before removing the Camunda user-task marker. A bare Camunda 7 user task has no such request and remains a Camunda 8 user task.

## Approach M3 - Online Diagram Converter (hosted)

Point the user to the hosted converter:

> Upload your BPMN/DMN files at https://diagram-converter.camunda.io/, set the target version there, and download the converted results.

This path does not automate the hosted service. Once the user brings the converted files back, offer the same findings follow-up as M1 step 5. For machine-readable findings, use the hosted converter's 'Download JSON' button. It produces the same `analysis-results.json` the CLI writes. Its CSV/markdown/XLSX downloads are not parsed (see 5a). The imported-report version check in step 5 applies.

## Approach E1 - Camunda 7 Engine Source (only when no local models found)

### 1. Ask for C7 Access

Ask the user for:

- The C7 engine REST base URL, including the `/engine-rest` context path when applicable.
- Authentication: no authentication, or Basic authentication username/password.
- Obtain credentials through the host's secure credential mechanism. Never commit them.

Also ask whether to fetch all latest process/decision definitions or only named keys.

### 2. Fetch and Convert

Create `.camunda-migration/c7-models`. Query the C7 REST process-definition and decision-definition list endpoints, using `latestVersion=true` for the all-latest case and key filters for named acquisition. For every selected definition:

1. Fetch its `/xml` resource through the secure authentication mechanism. Parse the JSON response and extract the nonempty `bpmn20Xml` or `dmnXml` string. Never write the JSON envelope as a diagram.
2. Group definitions by resource name and exact XML payload. A deployment resource may contain multiple processes/decisions, so persist each unique resource/payload pair once, not once per definition. If one resource name has distinct payloads, derive collision-safe deterministic filenames from the sorted definition keys.
3. Write only the extracted XML payload to a deterministic `.bpmn`/`.dmn` path under `.camunda-migration/c7-models`.
4. Record every corresponding definition id/key against that source path so the converted copy can be paired exactly.
5. Run M1 local mode on `.camunda-migration/c7-models`.

Treat the fetched XML as the original source for `form-migration.md` and `form-reference-migration.md`, and keep the exact mapping between fetched and converted paths. Do not use the CLI `engine` subcommand here: it does not persist the raw source XML needed to generate forms safely. Embedded form HTML and `.form` files are not part of the fetched BPMN, so referenced form content is normally unavailable in this mode. Record it as such rather than treating the reference as resolved.

### 3. Handle Failures

Treat an unreachable endpoint, a TLS/DNS failure, a 401/403, malformed XML, or an empty response as a blocking error. Report the URL, the operation, the status/error, and the concrete next action. Never silently continue or report success when any requested definition failed.

## Target deployment: verify the target version

Confirm the target and profile with the user before deployment.
When c8ctl is configured, run `c8ctl which profile` and confirm the returned profile.
Run `c8ctl get topology --profile=<name> --json` with that profile.
Otherwise, use an authorized deployment client to read the same topology metadata.
Compare `gatewayVersion` and every `brokers[].version` with the declared target's major and minor
version. Read those fields from the
[Orchestration Cluster REST API topology response](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-swagger/).
Use the confirmed profile or client for both this check and deployment.

| Verification result | Action |
|---|---|
| Every reported version matches the declared target. | Record the non-secret target identifier and all reported versions in `MIGRATION_REPORT.md`. Continue with deployment. |
| A version is missing or malformed, no brokers exist, or any major or minor version differs. | Block deployment and model readiness. Ask the user to select a matching target. |

When the user authorizes a test target, deploy explicit converted BPMN and DMN paths with accepted
`.form` paths and their owning BPMN in the same request. Use
`c8ctl deploy <files...> --profile=<name> --json` or the same authorized deployment client.
If two resources would share a deployment name, then block deployment until their names differ.
Record a result for every resource path and each form's owner. If authorization, target-version
evidence, deployment success, or an approved listener follow-up is missing, then block model
readiness.

## Linting Converted BPMN (M1 and M2)

After any manual BPMN edit (findings follow-up, form wiring, expression fixes), lint immediately with bpmnlint and the Camunda compatibility ruleset for the target version. Missing DI, overlaps, and disconnected flows are cheapest to catch at edit time.

```sh
npm install -D bpmnlint zeebe-bpmn-moddle bpmnlint-plugin-camunda-compat   # once
npx bpmnlint <converted-file>.bpmn
```

`.bpmnlintrc`: extend `bpmnlint:recommended` and `plugin:camunda-compat/camunda-cloud-<target-major>-<target-minor>` (for example, `camunda-cloud-8-9` for target 8.9), and set `moddleExtensions.zeebe` to `zeebe-bpmn-moddle/resources/zeebe.json`. Fix or record every lint error before continuing.

## Model validation (Step 4)

Lint every in-scope BPMN and DMN model with the target-compatible ruleset, not only models that the
skill edited. After every manual BPMN edit, lint the converted copy again.

1. A `converted-c8-*` file exists for every in-scope diagram, unless the run is analyze-only.
2. Every original file is intact and was never overwritten.
3. No findings report named `analysis-results.<ext>` or `analysis-results (n).<ext>` remains under
   a packaged resource directory, as defined in
   [Pre-flight: Leftover Artifacts](#pre-flight-leftover-artifacts). Every fresh findings report from
   this run sits in the non-packaged reports directory of step 3a.
4. Every WARNING, TASK, REVIEW, and INFO finding is fixed or classified in the step 5d verdict table.
   In an M1 run, every **needs fix** or **needs review** row references its complete Element list.
   A flat "fixed or recorded" note is not enough.
5. Every source Generated Task Form is `accepted`, `blocked`, or `declined`, including a
   form-property-only definition. None is silently omitted. Every accepted form passes the checks in
   "Deployment and validation" in `form-migration.md`, including a target-compatible schema and
   form-js check where the tooling exists. No draft, blocked, or declined form is linked or deployed.
   Every semantic gap and every user decision is recorded.
6. Every referenced form and every form-free owner has a recorded per-category decision and a final
   status of `kept`, `relinked`, `accepted`, `declined`, `deferred`, or `blocked`. The in-progress
   statuses `pending` and `draft` must not remain. A `deferred` or `blocked` item stays open follow-up
   work. A kept external reference is never reported as a completed migration, and no category is
   closed as **no action** because the converter copied a reference.
7. Every relinked or rebuilt form is referenced by `zeebe:formDefinition@formId` with a recorded
   binding decision, and the copied Camunda 7 `externalReference` or `formKey` is gone from that
   element. See `form-reference-migration.md`.
8. Once the verdict table is complete, the converted copies hold no `conversion:*` node, no
   `conversion:*` attribute, no unused Camunda 7 namespace declaration, and no leftover BPMN
   definitions-level XPath `expressionLanguage` attribute. See step 5e.
9. For every converted BPMN with source BPMN DI, the converted copy preserves the source diagram,
   plane, shape, edge, label, bounds, waypoint, and `bpmnElement` reference data for unchanged
   semantic IDs.
10. For every converted BPMN without source BPMN DI, the skill does not create layout data and
    records the absent source DI as provenance in `MIGRATION_REPORT.md`.
11. When a semantic rewrite changes an ID referenced by BPMN DI, the skill updates the reference or
    records a blocking or review finding when it cannot reconcile the reference.
12. When the model uses M2, inspect every `zeebe:taskDefinition/@type`. Derive the expected type
    from the original implementation attribute with the [M2 job type naming](#job-type-naming) rules.
    Treat a mismatch without the decision-log entry that those rules require as a validation failure.
13. When the model uses M2, the skill runs the expression-prefix validator for every source and
    converted pair. The skill supplies one `--pair` argument for every in-scope BPMN or DMN model:

    ```sh
    python3 "<skill-directory>/scripts/validate_model_expressions.py" \
      --pair "<source.bpmn>" "<converted-c8-source.bpmn>"
    ```

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
14. For each call activity, compare the converted scope with its original inputs and outputs, as
    [Call-Activity Variable Scope](#call-activity-variable-scope) defines. Check every
    `bpmn:callActivity` in every `converted-c8-*.bpmn` file. For each call with a compatible C8
    mapping, require its `zeebe:calledElement` to set `propagateAllChildVariables` explicitly to
    `true` or `false`. Do not pass readiness validation while a call remains **needs review**.
15. **Selected M1 artifact** — record the CLI tag, JAR path, validated Java executable, and target
    version. Step 3b passes for every source start listener and converted copy. A worker does not
    validate listener placement.
16. **Target deployment** — when the user authorizes a test target, verify and deploy as
    [Target deployment](#target-deployment-verify-the-target-version) defines.

## Analyze-Only Mode

For "analyze but don't convert": run M1 with `--check --json --xlsx` (no converted files), or do an M2 read-only pass. Parse and present findings grouped by category as in M1 step 5. Include the namespace-derived Generated Task Form inventory, the referenced-form inventory from `form-reference-migration.md`, and likely decision categories. Do not create `.form` files or edit BPMN. Then stop.
