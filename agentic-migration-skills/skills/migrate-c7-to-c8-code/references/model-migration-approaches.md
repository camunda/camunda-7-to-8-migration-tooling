# Model Migration Approaches (Part B)

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## Source Selection

Use the assessment model scan before choosing a path.

- If local model files exist under the project root, use local mode. Never offer or request C7 engine access.
- If none exist and the user selected E1, fetch definitions from C7 first.

Before conversion, namespace-parse the exact original BPMN and inventory every C7 form. Route Generated Task Forms (`camunda:formData`/`formField` and direct `camunda:formProperty`) to `form-migration.md`. Route referenced forms (`camunda:formKey`, `camunda:formRef`) and user tasks or process-level none start events with no form at all to `form-reference-migration.md`. Keep source path, process id, and owner id/type so each definition can be paired with a fresh converted copy. The converter strips generated-form metadata and copies form-key references verbatim, so post-conversion discovery is too late or ambiguous.

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

If anything else is found, warn through AskUserQuestion before converting:

> Found outputs from a previous migration attempt: `<list>`. This run will not overwrite them. Fresh findings reports are written beside the source. The CLI adds a ` (n)` suffix only when the unsuffixed name already exists. Relocate fresh findings reports to `.camunda-migration/reports/` when that directory is not packaged, or to another explicitly non-packaged directory, before validation. Only this run's own outputs are used — stale files are never consumed. Diagrams whose `converted-c8-*` target already exists are skipped with an error, so for a full re-conversion, cancel and delete or move the old files first.

- **OK, proceed** — when no findings report remains under a packaged resource directory, run without `-o`/`--override`. Old files stay untouched.
- **Cancel** — stop so the user can back up or clean up first.

For local approaches (M1, M2, E1), never consume a pre-existing report or converted file found on disk. It may come from an interrupted attempt or a different `--platform-version`. The findings flow (M1 steps 3-5) works only from this session's own run. M3 is the exception: hosted-converter outputs are allowed only after the imported-report version and pairing checks in step 5.

## Approach M1 - Diagram Converter CLI + AI (recommended)

### 1. Java 21+ Prerequisite (fail fast)

Run `java -version` from `PATH`, capture stderr, and record the actual major version. The Diagram Converter CLI requires major version `21` or higher. Do not apply the OpenRewrite upper bound. If `java` is missing or below 21, request an alternate JDK home through AskUserQuestion. Validate its `bin/java` (Windows: `bin/java.exe`) and check its actual version first. If several validated compatible homes exist, choose the lowest. Prefer 21 for reproducible runs. (SHOULD) Use the validated executable and home only for the converter invocation.

> The Diagram Converter CLI requires Java 21+. Detected: `<version or "not found">`. Provide an alternate JDK home and re-run, or choose M2 (agentic AI) which needs no Java, or M3 (online converter).

Never silently skip model migration.

### 2. Resolve Latest Release and Download CLI

The CLI is published as a self-contained executable JAR named `camunda-7-to-8-diagram-converter-cli-<tag>.jar` on the GitHub releases for `camunda/camunda-7-to-8-migration-tooling`.

1. Determine the latest release tag.
2. Ensure `.camunda-migration/` exists in the project root.
3. Compute the target path: `.camunda-migration/camunda-7-to-8-diagram-converter-cli-<tag>.jar`.
4. If that JAR exists, reuse it.
5. Otherwise download from `https://github.com/camunda/camunda-7-to-8-migration-tooling/releases/download/<tag>/camunda-7-to-8-diagram-converter-cli-<tag>.jar`.

The JAR is ~30 MB. If the project is a git repo, recommend adding `.camunda-migration/` to `.gitignore`. Modify `.gitignore` only after the user confirms through AskUserQuestion.

### 3. Run the Converter

The CLI local subcommand accepts a single file or a directory (recursive by default). Always pass `--platform-version` set to the target version from the interview.

```
"<java-cmd>" -Dfile.encoding=UTF-8 -jar "<jar>" local "<file-or-dir>" --platform-version "<target-version>" --json --xlsx
```

On Windows PowerShell, prefix the command with the call operator: `& "<java-cmd>" ...`. Replace `<java-cmd>` with the validated `bin/java` path. After an alternate executable is selected, never use a bare `java` command.

Recommended flags:
- `--json` - always pass this. The JSON report is the machine-readable input for step 5. It needs a CLI release with the flag (0.3.6 or later). If the run fails with `Unknown option: '--json'`, the JAR predates it. Re-resolve the latest release (step 2).
- `--xlsx` - always pass this. The XLSX report is the human-readable report for reviewing and sharing findings with the customer.
- `-o` / `--override` - overwrite pre-existing outputs in place. Destructive — do not pass by default (see Pre-flight: Leftover Artifacts). Without it, a diagram whose converted target already exists is skipped with a `File already exists` error, and reports are written under ` (n)`-suffixed names.
- `--check` - analyze-only (no converted copies exported)
- `-nr` / `--not-recursive` - disable recursive search

Other options:
- `--prefix <str>` - prefix for generated filenames (default `converted-c8-`)
- `--md` - write analysis report in markdown format

The converter writes a new file next to the source (e.g., `converted-c8-order-process.bpmn`), so originals are never mutated in place.

Capture the exact paths of everything the run produces from the `Created ...` lines in the CLI console output (e.g. `Created analysis-results (1).json`). These paths are authoritative until the report relocation below completes. Never glob for `analysis-results.json` or `converted-c8-*` on disk, which may match stale files from a previous attempt or a different `--platform-version`.

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

Before packaging the project, inspect every resource directory that the build configures for
packaging, including `src/main/resources` when it exists. No findings report named
`analysis-results.<ext>` or `analysis-results (n).<ext>` may remain there, where `<ext>` is `.csv`,
`.json`, `.md`, or `.xlsx` and `n` is a positive integer. Keep findings reports under `.camunda-migration/reports/` only when the build does not package that
directory. Otherwise, use another explicitly non-packaged directory.

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

Trust the converter's output for what it did NOT flag. The job types and listener wiring it emitted are authoritative. Apply manual fixes only for what the report flags. Never second-guess or re-derive converted structures.

Group by category first. The category, not the individual row, is the unit of work.

#### Imported reports: verify the target platform version

Skip this check for this skill's own CLI run: it already passed the chosen `--platform-version`, and leftover local reports are never consumed (see Pre-flight: Leftover Artifacts).

This check fires only for a report deliberately imported without a fresh run — generated earlier, by someone else, or downloaded from the hosted converter (M3). Only a JSON report is consumable (see 5a — there is no CSV parsing path). If the import is CSV, markdown, or XLSX only, re-run the CLI locally with `--check --json --xlsx` on the input models. For an imported JSON report, confirm it was generated for the chosen target version before consuming it. Findings are version-dependent. Conditional events are flagged unsupported in a report targeting 8.6, but are native since 8.9. A stale report can send the user chasing findings that do not apply to their target.

Determine the report's target version:

1. Findings with `messageId` `element-available-in-future-version` name it. The message reads `Element '<name>' is not supported in Zeebe version '<report-target>'. It is available in version '<x.y>'.` — `<report-target>` is the version the report was generated against.
2. Otherwise the version cannot be determined from the content. Ask the user which `--platform-version` generated the report.

If the report's version does not match the chosen target, or cannot be determined, warn the user and offer through AskUserQuestion before grouping (5b) or any cross-checks:

- **Re-run the converter at the chosen target** (recommended) — run the step 2 CLI with `--check --json --xlsx --platform-version <target-version>` on the same input. Analyze-only mode is fast and produces fresh JSON and XLSX reports for 5a.
- **Keep the imported report** (MAY) — use it only for non-runtime grouping, record the mismatch in
  MIGRATION_REPORT.md, and perform target-aware revalidation before assigning runtime impact or
  completing the verdict table.

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

Parse it with real JSON tooling (e.g. `jq` or a built-in JSON parser), never ad-hoc string splitting.

If the JSON report is missing (e.g. only `analysis-results.md` or a CSV/XLSX was generated), re-run
the converter with `--check --json --xlsx --platform-version <target-version>` on the same input.
Capture the fallback run's `Created ...` paths and apply step 3a before parsing. The markdown and
XLSX reports are for humans. CSV is never
consumed — this skill has no CSV parsing path, and the JSON report is the only machine-readable
findings source.

#### 5b. Group findings by category

Group findings by `messageId` (the category). For each converter category compute:

- Total count, and count per severity.
- Distinct `elementType` values affected (e.g. serviceTask, sequenceFlow, multiInstanceLoopCharacteristics).
- One representative example: a `message` with its `filename` and `elementId`.
- The `link` to conversion guidance for that category.

#### 5b.1. Add source-derived findings

After grouping the available findings, run this scan only when the current approach produced a fresh
converted copy. Scan every fresh converted BPMN model with a namespace-aware XML parser. This scan
is an explicit exception to the rule that trusts unflagged converter output. For M1, M3, and E1,
run this scan after grouping the JSON findings. For M2, run it before the findings summary because
the scan adds source-derived findings to that summary. M2 does not produce a JSON report. Run this
scan for M1, M2, M3, and E1 when a fresh converted copy exists.

If M1 uses `--check`, the CLI produces no converted copy. Defer this scan and the
`blank-executable-task-job-type` and `blank-dmn-decision-id` categories until a fresh converted
copy is available. Record the deferred scan in `MIGRATION_REPORT.md`.

Match each fresh converted copy to the source model path captured in step 3. Use the source/output
model pair and BPMN element ID as the deduplication key. Do not compare source and converted
filenames as if they were the same file.

Inspect every `bpmn:serviceTask`, `bpmn:sendTask`, `bpmn:businessRuleTask`, and
`bpmn:scriptTask` with this decision table:

| Source and converted task condition | Action |
|---|---|
| `businessRuleTask` has a non-blank source `camunda:decisionRef` and a non-blank converted `zeebe:calledDecision/@decisionId` | Exclude the task from the job-type scan. |
| `businessRuleTask` has a present but blank or whitespace-only source `camunda:decisionRef`, or a non-blank source `camunda:decisionRef` with a missing or blank converted `zeebe:calledDecision/@decisionId` | Add a `blank-dmn-decision-id` source-derived finding. Do not add a `blank-executable-task-job-type` finding. |
| `businessRuleTask` has no source `camunda:decisionRef` and a converted `zeebe:calledDecision` with a missing or blank `@decisionId` | Add a `blank-dmn-decision-id` source-derived finding. Do not add a `blank-executable-task-job-type` finding. |
| `businessRuleTask` has no source `camunda:decisionRef` and a converted `zeebe:calledDecision` with a non-blank `@decisionId` | Add an `unexpected-dmn-decision` source-derived finding. Do not add a `blank-executable-task-job-type` finding. |
| `businessRuleTask` has no source `camunda:decisionRef` and no converted `zeebe:calledDecision` | Inspect the task as a job-backed task. |
| `scriptTask` has `zeebe:script` for an internal FEEL script | Exclude the task from the job-type scan. |
| Any other listed task | Inspect its extension elements for a `zeebe:taskDefinition` with a non-blank `@type`. |

When a listed task has no task definition or has a blank task-definition type, add a source-derived
finding in the `blank-executable-task-job-type` category only when no converter finding for the
paired source model and same BPMN element ID already identifies a missing job type. Record the
source/output model pair, converted file, element id, element type, and missing or blank attribute
as evidence. Add this category to the grouped summary and the verdict table even when the JSON
report contains no matching `messageId`.

For a `blank-dmn-decision-id` finding, record the source/output model pair, converted file,
element id, element type, source `camunda:decisionRef` value, and converted `decisionId` value as
evidence. Add this category to the grouped summary and the verdict table even when the JSON report
contains no matching `messageId`.

When a converter finding already identifies the missing job type for the paired source model and
element ID, use that converter finding and do not add a duplicate synthetic finding.

For an `unexpected-dmn-decision` finding, record the source/output model pair, converted file,
element id, source `camunda:decisionRef` absence, and converted decision ID as evidence. Add the
category to the grouped summary and the verdict table even when the JSON report contains no
matching `messageId`.

When M2 runs without a Diagram Converter report, also scan every source execution and task listener
against the emitted listeners. Use the M2 listener pairing rules in `SKILL.md`, including normalized
events and declaration ordinals, even when code migration is not in scope. Add a source-derived
`execution-listener-supported` or `task-listener-supported` row only for an emitted pair that
passes the target and event support matrix. Add a source-derived `camunda-script` row with Blocking
runtime impact for any paired listener that has a `camunda:script` child. Add a source-derived
`blank-listener-job-type` row with Blocking runtime impact for a paired listener whose emitted
`@type` is missing or blank. Do not add a supported-listener row for either case.
Add a synthetic `execution-listener` or `task-listener` row for every source listener without an
emitted pair. Before adding a missing-source row for an emitted listener, inspect the decision log.
Record an intentional target-only listener as `target-only-listener` when the decision log contains
its listener host, normalized event, emitted declaration ordinal, emitted type, and rationale.
Include its emitted type in the worker or handler coverage cross-check. Record an unaccounted
emitted listener under the corresponding synthetic listener category. Include these rows in the
grouped summary and verdict table. Add a source-derived `m2-task-binding` row for every emitted
task mapping. In a models-only run, record `n/a` for the code artifact. Assign `needs review` only
to paired, supported listener, target-only listener, or task-mapping rows until code coverage is
verified.

In an M2 run without a converter report, scan every source `camunda:script` child on an execution
or task listener, executable task, event condition, or input/output mapping. Add a source-derived
`camunda-script` row with Blocking runtime impact for every such child. Record the source owner,
script format, script text or reference, and converted owner as evidence. Do not add a supported
listener row when a listener script exists, including a FEEL listener script.

Source-derived categories, including `generated-form-property-source`,
`blank-executable-task-job-type`, `blank-dmn-decision-id`, `unexpected-dmn-decision`,
`blank-listener-job-type`, `m2-task-binding`, `target-only-listener`,
`execution-listener-supported`, `task-listener-supported`, source-derived `camunda-script`, and
synthetic M2 listener rows, have no converter severity.
Record `n/a` as their converter severity and use `TASK` as their effective severity for sorting.
Keep the converter severity for converter-emitted `execution-listener` and `task-listener`
findings. If the imported report target differs from the chosen target, defer runtime impact for
these findings until target-aware revalidation.

Sort categories by effective severity (TASK > WARNING > REVIEW > INFO), then count descending.

#### 5c. Present the grouped summary

Present the grouped table before any per-finding follow-up starts, and record it in MIGRATION_REPORT.md:

| Category (messageId or source category) | Severity or effective severity | Count | Element types | Example |
|---|---|---|---|---|
| `expression-method-not-possible` | REVIEW | 1,308 | sequenceFlow, exclusiveGateway | "Method invocation is not possible in FEEL: ..." in order-process.bpmn, element `Gateway_1` |

#### 5d. Emit a per-category verdict table

Before assigning verdicts, compare each finding `messageId` with the dedicated cross-check rules.
The current dedicated cross-check categories are:

| Category | Dedicated cross-check |
|---|---|
| `delegate-expression-as-job-type`, `delegate-implementation`, `expression-method-as-job-type`, `execution-listener-supported`, `task-listener-supported`, `script-job-type`, `topic`, `connector-id`, `m2-task-binding` | Check the 1:1 and many-to-one job-type mappings, worker coverage, and connector registrations in `composing-code-and-models.md` |
| `expression-method-not-possible` | Check the FEEL method-invocation remediation |
| `collection-hint` | Check for now-redundant workaround code |
| `element-available-in-future-version` | Verify the report target version |
| `execution-listener`, `execution-listener-supported`, `task-listener`, `task-listener-supported` | Match listener implementations during the workaround and listener cross-checks |

The form procedures in 5f and 5g are also dedicated handling for their named form categories.
Treat every other category as a fallback category.

#### 5d.1. Classify runtime impact

Assign `Blocking` or `Advisory` to every evidence-complete verdict-table row before assigning its
verdict. Assign `Pending` when required evidence is incomplete. Runtime impact is independent of
severity. Severity describes the urgency of follow-up. Runtime impact describes whether the finding
blocks deployment or execution on the chosen target. A `Pending` value stays outside the
Blocking/Advisory ordering and receives a `needs review` verdict.

Apply the report-target condition before category-specific rules. If the report target differs from
the chosen target, assign `Pending` runtime impact until target-aware revalidation completes.

If one `messageId` produces more than one runtime impact, split its findings into separate
verdict-table rows before assigning verdicts. Preserve the count, examples, links, and evidence
for each partition. Use the category and runtime impact together as the row identity.

Use the following rules:

| Category or validation condition | Runtime impact | Derivation |
|---|---|---|
| A report target that differs from the chosen target | **Pending** until revalidation | This rule takes precedence over every category-specific rule. A report generated for a higher target can omit findings for elements unsupported at the chosen lower target. |
| Any known category whose required source, converted-model, target, or integration evidence is incomplete before its category-specific rule can be selected | **Pending** | This rule takes precedence over a category-specific Blocking or Advisory branch. Record the missing evidence and assign `needs review`. |
| `element-not-supported`, `element-not-supported-hint` | **Blocking** | The target cannot deploy or execute the affected element. |
| `element-available-in-future-version` after target match or target-aware revalidation when the chosen target is lower than the required version | **Blocking** | Compare the report's required version with the chosen target only after the report target matches or revalidation completes. |
| `element-available-in-future-version` after target match or target-aware revalidation when the chosen target meets or exceeds the required version | **Advisory** | The target supports the element. A fresh report for that target would not emit this finding. |
| `delegate-implementation-no-default-job-type`, `delegate-expression-as-job-type-null` | **Blocking** | The converter left the executable task's job type blank. No job worker can activate that task until a type is defined. |
| A missing `zeebe:taskDefinition` or blank `zeebe:taskDefinition/@type` on a `serviceTask`, `sendTask`, non-DMN `businessRuleTask`, or non-internal `scriptTask` | **Blocking** | The converted job-backed task has no routable job type. Record this as the synthetic category `blank-executable-task-job-type` when no converter message identifies it. Exclude DMN business-rule tasks and internal FEEL script tasks because they use a called decision or an internal script instead of a job worker. |
| `blank-dmn-decision-id` on a `businessRuleTask` with a blank or whitespace-only source `camunda:decisionRef`, or with an existing converted `zeebe:calledDecision` whose `@decisionId` is missing or blank | **Blocking** | The task has no decision to resolve. Record this source-derived category instead of treating the task as a job-backed task. |
| `unexpected-dmn-decision` when a `businessRuleTask` has no source `camunda:decisionRef` but has a non-blank converted `zeebe:calledDecision/@decisionId` | **Blocking** until the mismatch has a confirmed decision-log entry and validated intent | The converted model adds a decision call without a source binding. Record the source/output evidence and remove or explicitly approve the added decision. |
| `unexpected-dmn-decision` after a confirmed decision-log entry and validated intent | **Advisory** | The added decision call is intentional. Record the decision and validation evidence. |
| `delegate-expression-as-job-type`, `delegate-implementation` in a models-only run without a code cross-check | **Pending** | The worker mapping is unverified. Record `n/a` for the code artifact and assign `needs review` until code coverage is verified. Do not infer Advisory or Blocking from an absent cross-check. |
| `delegate-expression-as-job-type` when the code cross-check confirms a 1:1 mapping for every source implementation or expression, or confirms a documented intentional type deviation with a matching worker | **Advisory** | The cross-check confirms coverage. Record the matched worker mapping and assign no action. |
| `delegate-expression-as-job-type`, `delegate-implementation` when the code cross-check confirms a many-to-one mapping with one dispatcher or adapter worker whose routing covers every distinct source implementation or expression, including any documented intentional type deviation | **Advisory** | The dispatcher or adapter covers the shared job type without competing workers. Record its routing coverage and decision-log entry, then assign no action. |
| `delegate-expression-as-job-type`, `delegate-implementation` when the code cross-check finds an uncovered implementation or expression, a missing dispatcher or adapter, or a mismatched job type without a confirmed decision-log entry and matching worker coverage | **Blocking** | The task has no verified worker mapping. The uncovered or unapproved mismatched mapping can prevent execution or route the task to the wrong worker. |
| An emitted execution-listener pair fails the target's 8.6+ or event-support check | **Blocking** | Emitted XML does not prove target support. Record the pair under the `execution-listener` category and do not mark it as supported. |
| An emitted task-listener pair fails the target's 8.8+ or mapped-event support check | **Blocking** | Emitted XML does not prove target support. Record the pair under the `task-listener` category and do not mark it as supported. |
| `expression-method-as-job-type`, `execution-listener-supported`, `task-listener-supported`, `script-job-type`, `topic`, `connector-id` in a models-only run or without a code or integration cross-check | **Pending** | The worker, handler, or connector mapping is unverified. Record `n/a` for the code artifact and assign `needs review` until coverage is verified. Do not infer Advisory or Blocking from an absent cross-check. |
| `expression-method-as-job-type`, `execution-listener-supported`, `task-listener-supported`, `script-job-type`, `topic`, `connector-id` when the code or integration cross-check confirms every source binding with a matching worker, handler, or connector through a 1:1 mapping, a complete many-to-one dispatcher or adapter, or a documented intentional type deviation with matching coverage | **Advisory** | The cross-check confirms that every converted task or listener can reach its worker, handler, or connector. Record the matched integration and assign no action. |
| `expression-method-as-job-type`, `execution-listener-supported`, `task-listener-supported`, `script-job-type`, `topic`, `connector-id` when the cross-check finds an uncovered source binding, a missing worker, handler, connector, dispatcher, or adapter, or a mismatched job type without a confirmed decision-log entry and matching coverage | **Blocking** | The converted task or listener has no verified integration. The uncovered or unapproved mismatched mapping can prevent execution or route work to the wrong handler. |
| `m2-task-binding` in a models-only run or without a code or integration cross-check | **Pending** | The task mapping is unverified. Record `n/a` for the code artifact and assign `needs review` until coverage is verified. |
| `m2-task-binding` when the code or integration cross-check confirms the source binding and emitted type, including a documented intentional type deviation with matching coverage | **Advisory** | The cross-check confirms that the converted task can reach its worker, handler, or connector. Record the matched integration and assign no action. |
| `m2-task-binding` when the cross-check finds an uncovered binding, a missing worker, handler, connector, dispatcher, or adapter, or an unapproved mismatched type | **Blocking** | The converted task has no verified integration. The uncovered or unapproved mismatch can prevent execution or route work to the wrong handler. |
| `execution-listener`, `task-listener` when the source listener is omitted or no emitted listener exists | **Blocking** | The converter cannot transform the listener and emits no Zeebe listener. The affected listener behavior cannot execute in the converted model. |
| `execution-listener`, `task-listener` when an emitted listener has no source pair and no intentional target-only decision | **Blocking** | The converted model contains unaccounted listener behavior. Require a source pair or an explicit decision-log entry before deployment. |
| `target-only-listener` with an explicit decision-log entry and validated behavior | **Advisory** | The target-only listener is an intentional remediation. Record its decision and validation evidence. |
| `blank-listener-job-type` on an emitted execution or task listener | **Blocking** | The listener has no routable job type. Record the source pair and replace or remove the blank type. |
| `correlation-key-hint` when the referenced message is used by an intermediate, boundary, or event-subprocess message catch event | **Blocking** | The converter emits no `zeebe:subscription` when no correlation key is available. The catch event cannot correlate an incoming message. |
| `correlation-key-hint` when the referenced message is used only by a process-level message start event | **Advisory** | A message start event can create a new process instance without a correlation key. Record the finding for review. |
| `correlation-key-hint` when the referenced message is not used by a message catch event | **Advisory** | No converted catch event requires an incoming correlation key. Record the finding for review. |
| `expression-execution-not-available`, `expression-method-not-possible` on conditions, called-process IDs, timers, multi-instance collections, completion conditions, DMN decision IDs (`camunda:decisionRef`), or executable DMN expressions | **Blocking** | The affected expression controls routing, process invocation, timing, loop execution, decision resolution, or decision evaluation and cannot execute in the converted model. |
| `expression-execution-not-available`, `expression-method-not-possible` on input or output mappings whose expression is evaluated when the task executes | **Blocking** | The converted task preserves an expression that cannot evaluate. The task cannot execute with the required mapped data. |
| `expression-execution-not-available`, `expression-method-not-possible` on due dates, follow-up dates, candidate users or groups, priorities, scheduling metadata, or other non-blocking attributes | **Advisory** | The affected attribute needs migration work or a decision, but it does not by itself prove that the model cannot deploy or execute. |
| `condition-expression-feel` when inspection finds a custom FEEL function that Camunda 8 does not support | **Blocking** | The condition cannot evaluate, so it cannot route execution. |
| `condition-expression-feel` when inspection finds only supported FEEL constructs | **Advisory** | The converted condition needs review, but supported FEEL can evaluate it. |
| `conditional-flow`, `resource-on-conditional-flow`, `script-on-conditional-flow`, `resource-on-conditional-event`, `script-on-conditional-event` | **Blocking** | The affected conditional flow or event cannot evaluate its condition. |
| `delete-variable-event-not-supported` when the source conditional event's `camunda:variableEvents` includes `delete`, the chosen target is 8.9 or later, and source inspection confirms that delete behavior is required | **Blocking** | The converted `zeebe:conditionalFilter` cannot trigger on delete events because C8 supports only `create` and `update`. |
| `delete-variable-event-not-supported` when the source conditional event includes `delete`, the chosen target is 8.9 or later, source inspection finds no required delete behavior, an explicit user decision records that evidence, and the converted filter retains a supported `create` or `update` trigger | **Advisory** | The converted filter drops a confirmed-unused delete trigger. Record the behavioral evidence and user decision. |
| `delete-variable-event-not-supported` when the source conditional event includes `delete`, the chosen target is 8.9 or later, and the required-delete evidence or supported-trigger check is inconclusive | **Blocking** | The converted filter may lose required delete behavior or contain no supported trigger. Keep the finding unresolved until behavioral evidence and a user decision exist. |
| `delete-variable-event-not-supported` when the source conditional event's `camunda:variableEvents` includes `delete` and the matching or revalidated target is below 8.9 | **Blocking** | The conditional event cannot deploy at this target. If the corresponding `element-available-in-future-version` finding is present, link both Blocking rows to the same event. Preserve each category's count, evidence, and link. |
| `timer-expression-not-supported`, `inclusive-gateway-join` | **Blocking** | The affected element cannot execute with the chosen target semantics. |
| `loop-cardinality` when no valid C8 `inputCollection` replaces the cardinality | **Blocking** | The converter emits no C8 loop-count attribute. Inspect `zeebe:loopCharacteristics@inputCollection` and verify that its expression represents the same iteration set. |
| `loop-cardinality` when a C8 `inputCollection` is present and source/converted inspection confirms semantic equivalence | **Advisory** | The converted loop preserves the source iteration set. Record the compared expressions and validation evidence. |
| `loop-cardinality` when a C8 `inputCollection` is present but semantic equivalence is unverified | **Pending** | The converted loop may use a different iteration set. Record the compared expressions and assign `needs review`. |
| `loop-cardinality` when a C8 `inputCollection` is present but does not preserve the source iteration set | **Blocking** | The converted loop executes a different iteration set. Record the source and converted expressions and require redesign. |
| `only-feel-supported` when the original DMN `expressionLanguage` is not the case-insensitive literal `feel` and is not exactly one of `https://www.omg.org/spec/DMN/20151101/FEEL/`, `https://www.omg.org/spec/DMN/20180521/FEEL/`, `https://www.omg.org/spec/DMN/20191111/FEEL/`, or `https://www.omg.org/spec/DMN/20211108/FEEL/` | **Blocking** | Read the source value before conversion. The converter removes this attribute from non-definition elements, so another language cannot execute. |
| `only-feel-supported` when the original DMN `expressionLanguage` is the case-insensitive literal `feel` or exactly one of `https://www.omg.org/spec/DMN/20151101/FEEL/`, `https://www.omg.org/spec/DMN/20180521/FEEL/`, `https://www.omg.org/spec/DMN/20191111/FEEL/`, or `https://www.omg.org/spec/DMN/20211108/FEEL/` | **Advisory** | The converter removes the explicit language attribute, but FEEL remains the supported language. |
| `generated-form-property-source` | **Advisory** | The source-only form-property finding needs form migration work, but it does not by itself prove a deployment or execution failure. |
| `form-data` when `camunda:formData@businessKey` is present | **Blocking** | No C8 form-js property reproduces the C7 process business-key behavior. For target 8.9 or later, require an explicit Business ID design. For target 8.8, require a tag, variable, or correlation design. Record a no-migration decision when neither design is accepted. |
| `form-data` without `camunda:formData@businessKey` | **Advisory** | The generated form needs migration work, but it does not by itself prove a deployment or execution failure. |
| A form-reference category (`form-key-embedded`, `form-key-external`, `form-key-camunda-form`, `form-key-expression`, or source-derived `c7-*`) when source inspection finds a `cam-business-key` marker | **Blocking** | The referenced form cannot preserve C7 business-key behavior. For target 8.9 or later, require an explicit Business ID design. For target 8.8, require a tag, variable, or correlation design. Record a no-migration decision when neither design is accepted. |
| A form-reference category when the form content is unavailable, dynamic, or otherwise `unknown` | **Pending** | Do not infer that no `cam-business-key` marker exists. Record the missing evidence and assign `needs review` until the content is resolved. |
| A form-reference category after source inspection confirms resolved content without a `cam-business-key` marker | **Advisory** | The referenced form needs migration work or a decision, but the source evidence does not prove a deployment or execution failure. |
| `variable-name-filter-not-supported` when the source filter variable is not referenced by the conditional event's FEEL expression | **Blocking** | The converter removes `camunda:variableName`, so the conditional event no longer triggers when that variable changes. |
| `variable-name-filter-not-supported` when the source filter variable is referenced by the conditional event's FEEL expression | **Advisory** | The FEEL condition still observes the source filter variable. Record the finding for semantic review. |
| `input-variable-not-supported` when a DMN input has no non-blank source `inputExpression` and the converted input has no non-blank `inputExpression` | **Blocking** | The converted decision input has no expression to evaluate. Record the source input and add an approved C8 input expression. |
| `input-variable-not-supported` when the source or converted DMN input has a non-blank equivalent `inputExpression` | **Advisory** | The input expression remains available for evaluation. Record the source and converted expressions for review. |
| `error-event-definition` when source inspection shows an executable task or active error path uses the definition | **Blocking** | The converter removes the unsupported C7 definition. The affected error path cannot preserve its modeled execution behavior. |
| `error-event-definition` when source inspection shows no active executable use | **Advisory** | The definition does not affect deployed execution. Record the finding for cleanup or review. |
| `error-code-no-expression`, `escalation-code-no-expression` on a referenced error or escalation definition | **Blocking** | Camunda 8 accepts only static codes. A dynamic code cannot match or emit the intended code on the related throw or catch event. |
| `error-code-no-expression`, `escalation-code-no-expression` on an unused definition | **Advisory** | The unused definition does not block deployed execution. Record the finding for cleanup or review. |
| `camunda-script` when source inspection shows that any script belongs to an executable task, listener, event, or input/output mapping | **Blocking** | The converter creates no C8 transformation for the script. Record the source script format, owner, and behavior that needs replacement. |
| `camunda-script` when source inspection shows no executable use | **Advisory** | The script does not prove a deployment or execution failure. Record the source context for cleanup or review. |
| `field-content` when source inspection finds an executable delegate or listener that depends on the dropped field and no equivalent input mapping or handler redesign covers it | **Blocking** | The converted executable behavior can lack required field input. Record the affected field and the missing replacement. |
| `field-content` when source inspection finds no executable dependency or confirms an equivalent input mapping or handler redesign | **Advisory** | The dropped field does not prove a deployment or execution failure. Record the evidence for cleanup or review. |
| `potential-starter` | **Advisory** | The converter does not carry the C7 potential-starter authorization metadata into C8. Record the access-control gap and the required operational control. |
| Every other known category not covered above, excluding form-reference categories handled by 5f and 5g, when evidence shows a deployment or execution failure | **Blocking** | Record the affected model evidence and the concrete remediation. |
| Every other known category not covered above, excluding form-reference categories handled by 5f and 5g, when evidence confirms no deployment or execution failure | **Advisory** | The finding can require migration work or a decision, but the evidence does not prove a runtime failure. |
| Every other known category not covered above, excluding form-reference categories handled by 5f and 5g, when evidence is inconclusive or missing | **Pending** | Record the missing evidence and assign **needs review**. List the category after Advisory rows. |

Apply this verdict override before the severity fallback:

| Condition | Verdict | Required action |
|---|---|---|
| `element-available-in-future-version` after target-aware revalidation confirms that the chosen target meets or exceeds the required version | **no action** | Do not offer migration work for the stale imported finding. |
| `only-feel-supported` with an original language value that matches the supported literal or one of the exact OMG FEEL URIs listed in the impact table | **no action** | Record the source language and target-aware validation. Do not offer work for removing the redundant attribute. |
| `target-only-listener` with an explicit decision log, emitted ordinal, and verified worker or handler coverage | **no action** | Record the target-only decision and integration evidence. Do not offer duplicate missing-source work. |
| `unexpected-dmn-decision` with a confirmed decision-log entry and validated intent | **no action** | Record the intentional source/output deviation and validation evidence. Do not offer unresolved-mismatch work. |
| Any row with unresolved **Blocking** runtime impact | **needs review** or **needs fix** | Never assign **no action** while blocker evidence or remediation is unresolved. Assign **no action** only after validation confirms that the deployment or execution blocker is removed, and record the validation evidence. |

If a new or unknown `messageId` appears, verify the converted model and the affected element before
assigning its impact. Use this decision table:

| Evidence for the new or unknown category | Runtime impact | Required record |
|---|---|---|
| The evidence shows a deployment or execution failure. | **Blocking** | Record the affected model evidence and add the category to the inventory. |
| The evidence confirms that no deployment or execution failure exists. | **Advisory** | Record the affected model evidence and add the category to the inventory. |
| The evidence is inconclusive or missing. | **Pending** | Record the missing evidence and assign **needs review**. List the category after Advisory rows. |

Do not promote a category to **Blocking** because its severity is TASK or WARNING. A TASK can be
**Advisory**, such as `form-data` without `camunda:formData@businessKey`. A WARNING can be
**Blocking**, such as `element-not-supported`.

For a fallback category, assign the verdict with runtime impact before severity:

| Severity and runtime impact | Default verdict |
|---|---|
| INFO with **Blocking** runtime impact and concrete work defined | needs fix |
| INFO with **Blocking** runtime impact and a pending decision | needs review |
| INFO with **Blocking** runtime impact and neither concrete work nor a pending decision defined | needs review |
| INFO with **Advisory** runtime impact | no action |
| Any severity with **Pending** runtime impact | needs review |
| REVIEW | needs review |
| WARNING or TASK | needs fix |

Set the cross-referenced code artifact to **no dedicated cross-check** for a known fallback category.
Add the finding `link` to the `Link` column and surface it as the remediation starting point.
Use the unknown-category evidence table before assigning an impact to an unknown converter
`messageId` that is absent from the inventory below. Add the inspected converter `messageId` to the
inventory before applying the known-category fallback. Keep source-derived categories outside the
converter inventory and record them in the source-derived findings inventory instead.
Never infer a category-specific cross-check from an unknown `messageId`, its message text, or a similar category.

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
execution-listener-field, execution-listener-supported, expression, expression-execution-not-available,
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
timer-expression-not-supported, topic, variable-name-filter-not-supported, version-tag
```

When the referenced converter version changes, re-sync this inventory from
`diagram-converter/core/src/main/java/io/camunda/migration/diagram/converter/message/MessageFactory.java`.
Include IDs passed through helper methods, such as the `FormKeyType` mapping, not only literal
arguments to `composeMessage`. A maintenance check should mechanically compare the extracted
`MessageFactory` IDs with this inventory and report any difference.

After grouping (and after the code cross-checks in `composing-code-and-models.md` when code is also in scope), assign each WARNING/TASK/REVIEW category-impact row exactly one verdict, and record the table in MIGRATION_REPORT.md. Include every INFO row with Blocking or Pending runtime impact. INFO rows with Advisory runtime impact are optional (MAY). If included, apply the runtime-impact override before the severity fallback. Never assign no action to an INFO row with Blocking or Pending runtime impact. Never leave findings as severity counts or a generic "findings need follow-up" note.

Verdicts:

| Verdict | Meaning | Required action |
|---|---|---|
| **no action** | The converter handled the category deterministically, the finding is purely informational (typical for INFO), or a cross-check confirmed full coverage. | Nothing to do. |
| **needs review** | A human decision is required before any fix can start. For example, choosing the remediation approach for a category or integration group (one decision per homogeneous category or group, not per row), or confirming a cross-check result. | Surface it in the AI follow-up step only to collect the pending user decision through AskUserQuestion before any fix. |
| **needs fix** | Concrete, known work remains: an uncovered cross-check item (job-type mismatch, uncovered original expressions, uncovered invoked methods) or a WARNING/TASK category with a clear remediation. | It is a direct work item for the AI follow-up step. |

| Category (messageId or source category) | Converter severity / effective severity | Runtime impact | Count | Cross-referenced code artifact | Link | Verdict |
|---|---|---|---|---|---|---|
| `expression-method-not-possible` in execution-critical contexts | REVIEW / REVIEW | Blocking | `<critical-context count>` | none yet — remediation decision pending | `<finding link>` | needs review |
| `expression-method-not-possible` in non-blocking attributes | REVIEW / REVIEW | Advisory | `<non-blocking count>` | none yet — remediation decision pending | `<finding link>` | needs review |
| `delegate-expression-as-job-type` with covered mappings | `<converter> / <effective>` | Advisory | `<covered count>` | `DelegateDispatcher` @JobWorker or 1:1 worker mapping | `<finding link>` | no action |
| `delegate-expression-as-job-type` with uncovered or unapproved mismatched mappings | `<converter> / <effective>` | Blocking | `<uncovered count>` | `DelegateDispatcher` @JobWorker or 1:1 worker mapping | `<finding link>` | needs fix |
| `form-data` without `camunda:formData@businessKey` | `<converter> / <effective>` | Advisory | `<non-business-key count>` | one `.form` per C7 Generated Task Form (`camunda:formData` / direct `camunda:formProperty`, see 5f) | `<finding link>` | needs fix |
| `form-data` with `camunda:formData@businessKey` | `<converter> / <effective>` | Blocking | `<business-key count>` | one `.form` per C7 Generated Task Form (`camunda:formData` / direct `camunda:formProperty`, see 5f) | `<finding link>` | needs fix |
| `form-key-embedded` without `cam-business-key` | `<converter> / <effective>` | Advisory | 14 | none yet — keep/rebuild decision pending (see 5g) | `<finding link>` | needs review |
| `form-key-external` without `cam-business-key` | `<converter> / <effective>` | Advisory | 31 | `LoanFormsController` custom app — integration owner assigned and confirmation pending (see 5g) | `<finding link>` | needs fix |
| `c7-generic-task-form` | n/a / TASK | Advisory | 8 | n/a — no finding, source-derived inventory (see 5g) | n/a | needs review |

Rules:

- Use one row per category, sorted as in 5b. If a category has mixed runtime impacts, use one row
  per category-impact partition.
- Add the converter severity and effective severity to every row. Record `n/a / TASK` for a
  source-derived row without converter severity. Use the converter severity as the effective
  severity for converter-derived rows unless a dedicated rule assigns another value.
- Add the `Runtime impact` value before assigning the verdict. Runtime impact does not replace the
  verdict.
- The cross-referenced code artifact column names the `@JobWorker`, DMN definition, or other code element the cross-check matched, or `none yet` when no remediation exists. For models-only scope there is no code to cross-reference: use `n/a`. For a fallback category, write `no dedicated cross-check` in this column. Do not derive a fallback verdict from severity alone. Apply the runtime-impact override in 5d.1 first. Map INFO to no action only for Advisory rows. Map INFO with Blocking impact to needs fix when concrete work is defined, and to needs review when work is undefined or a decision is pending. Map REVIEW to needs review. Map WARNING/TASK to needs fix. Apply the procedure-defined lifecycle instead to source-derived synthetic categories and to `c7-*` categories that split a legacy generic `form-key` finding. Those categories have no independent converter severity.
- Include `blank-executable-task-job-type` even when the JSON report has no matching `messageId`.
  Give it `Blocking` runtime impact, `TASK` effective severity, `needs fix` verdict, and no
  dedicated cross-check.
- Include `blank-dmn-decision-id` even when the JSON report has no matching `messageId`.
  Give it `Blocking` runtime impact, `TASK` effective severity, `needs fix` verdict, and no
  dedicated cross-check.
- Include `unexpected-dmn-decision` when the converted model has a called decision without a source
  decision reference. Give it `n/a / TASK` severity, Blocking impact until the decision is recorded,
  and `needs review` or `needs fix` according to the decision-log state.
- Copy each finding's `link` into the `Link` column. For a source-derived category without a finding link, write `n/a` or a category-specific remediation link. For a fallback category, present that link as the remediation starting point.
- Classify every WARNING/TASK/REVIEW category and every INFO category with Blocking runtime
  impact. Never leave one without a verdict. Classify every source-derived category with `n/a`
  converter severity.
- `form-data` is a special **needs fix** category even though the converter behaved correctly: the missing artifact is a separate C8 form. Keep it needs fix until `form-migration.md` has generated, reviewed, linked, validated, and covered the form with deployment.
- A source-only `camunda:formProperty` definition from an older or imported report that lacks the current `form-data` finding uses the synthetic category `generated-form-property-source`. Give it the same verdict lifecycle as `form-data`.
- Form *reference* categories are never **no action** just because the converter copied the reference. See 5g for their verdict lifecycle.
- The table above is illustrative, not a template to copy: every category present in *this* run gets its own row. In particular, each specific form-key category the converter emitted (`form-key-embedded`, `form-key-external`, `form-key-camunda-form`, `form-key-expression`) is a separate row with its own verdict. They are different migrations and routinely land on different verdicts. When only the legacy generic `form-key` finding exists, use one source-derived `c7-*` row per form-key classification instead. Add a synthetic `c7-generic-task-form` row when the source scan found form-free owners.

#### 5e. Strip converter annotations from converted models

After every finding has a verdict, remove the temporary converter annotations from the fresh `converted-c8-*` copies. The verdict table and `MIGRATION_REPORT.md` are the durable record. Never leave the report embedded in the deployable model.

Use a namespace-aware XML parser or XML tooling, never regular expressions. For each converted BPMN/DMN file:

- Remove every `conversion:*` element, including `conversion:message`, `conversion:reference`, and `conversion:referencedBy`. Remove `conversion:*` attributes such as `conversion:converterVersion`.
- Remove the `conversion` namespace declaration after no `conversion` element or attribute remains.
- Remove empty `bpmn:extensionElements` left behind by the annotation removal.
- Remove `xmlns:camunda` (or another declaration for the C7 BPMN (`http://camunda.org/schema/1.0/bpmn`) or DMN (`http://camunda.org/schema/1.0/dmn`) namespace) only when no remaining element, attribute, or QName-valued attribute uses that namespace. Preserve and report any genuine remaining C7 QName instead of making it undeclared.
- Remove a BPMN definitions-level `expressionLanguage` attribute when it is the leftover C7 XPath declaration. Do not remove a valid DMN expression language or an expression attribute before resolving its finding.

Reparse every cleaned file. Fail the cleanup if it is not well-formed, or if any `conversion:*` node or attribute or unused C7 namespace declaration remains. Run this step before model validation and before linking or deploying generated forms.

#### 5f. Generate and review Camunda 8 forms

Run `form-migration.md` for every source Generated Task Form from the pre-conversion inventory. That procedure uses the original BPMN as source, writes deterministic draft `.form` files, inserts visible warnings for unresolved mappings, asks the user about semantic gaps, and edits the fresh converted BPMN only after explicit acceptance.

Never infer a form from a `form-data` message. Never mark the finding resolved merely because the converter removed it. Never link a form that still lacks the user's required decisions.

Then run `form-reference-migration.md` for every referenced form (embedded, external, Camunda Form, dynamic) and for every user task or process-level none start event with no form at all. That procedure inventories each reference, collects one decision per integration group within each category, relinks Camunda Forms, and rebuilds a C8 form only when the user explicitly asks.

#### 5g. Named category: Forms

Every C7 form type reaches this step, and each one is handled differently. Generated Task Forms (`camunda:formData` and source-only `camunda:formProperty`) are the `form-data` / `generated-form-property-source` workflow in 5f above. Everything else is a *referenced* form and runs through `form-reference-migration.md`:

| Report category | Source classification | Converter finding | Handling |
|---|---|---|---|
| `c7-embedded-html-form` | `embedded:` form key | `form-key-embedded` (older releases: `form-key`) | Inventory, classify simple/complex, then keep-or-rebuild decision |
| `c7-camunda-form-reference` | `camunda-forms:` form key | `form-key-camunda-form` (older releases: `form-key`) | Convert the `.form` and relink by `formId` + `bindingType` |
| `c7-camunda-form-reference` | `camunda:formRef` | no finding for literal values. Expression values may emit an expression-transformation finding | Convert the `.form`, read its own schema id, report any mismatch with a literal `formRef` instead of silently rewriting, and record the binding decision |
| `c7-external-form-reference` | form key with no known type | `form-key-external` (older releases: `form-key`) | Keep the reference for a custom application, or rebuild as a Camunda Form |
| `c7-dynamic-form-reference` | form key built from an expression | `form-key-expression` (older releases: `form-key`) | Stays `needs review`. Enumerate the possible values with the user first |
| `c7-generic-task-form` | no form metadata at all | no finding | Inventory and let the user choose |

Use the specific converter messageId as the verdict-table category when it corroborates the source classification. If only the legacy generic `form-key` finding exists, use the source-derived `c7-*` category to keep the form types separate. Use the synthetic `c7-*` name when no finding exists, the same convention as `generated-form-property-source`.

Never collapse these into one `form-reference` category. Never mark any of them **no action** because the converter copied a reference. A copied reference is not a working C8 form. Classify from the original BPMN source, not from findings alone. A report can be stale, imported, or produced by an older converter release that emitted a single generic `form-key` finding for all four form types.

- **One C8 form per C7 form.** For every C7 form the user chooses to migrate, create a C8 `.form` and reference it from its owning user task or start event. Never drop forms or merge several C7 forms into one.
- **Never rebuild a form unsolicited.** Offer the rebuild, ask one decision per integration group within each category, and generate only after an explicit instruction. Embedded HTML/JavaScript is never translated automatically.
- **Check what C8 forms do natively before adding a worker.** Many C7 projects carry flattening/computing service tasks that exist only because C7 forms could not bind or compute. C8 forms removed those limitations:
  - Field `key` supports path-as-key binding into nested variables (e.g. `customerInfo.firstName`), so no flattening worker is needed for passthrough fields.
  - `text` components support FEEL templating: `{{ }}` interpolation with full FEEL, including `{{#loop}}`. Counts, joined lists, and other computed display values belong in the form itself, not in a preceding service task. The JSON property is `text`, not `content` (`content` is for `html`-type components only).
  - A dedicated `documentPreview` component (property `dataSource`, a FEEL expression over an array of document references) renders an inline preview and download link for a Document API reference. Prefer it over a plain-text filename display or a hand-built HTML anchor. (SHOULD) When the process variable holds one document-reference object, wrap it in a one-element array in the form's FEEL only. Never change the variable to a form-specific array or a filename.
- **Add a worker only when the form genuinely cannot do it**: real business logic, external calls, side effects. A service task that only reshapes variables for form consumption is a C7 workaround. Never port it.

## JUEL Method-Invocation Worker Adapters

For every model approach, use a new thin `*Worker` adapter component for a Spring bean method invoked
by JUEL. Compare the adapter's fully qualified class name with the original Java source baseline,
recorded as fully qualified class names. Never add `@JobWorker` to an existing domain or service
class from the C7 source. Keep the domain logic in the existing bean and delegate to it from the
adapter. The code checklist defines the remediation and validation rules. Use this reference shape:

```java
@Component
public class SampleBeanWorker {
  @Autowired private SampleBean sampleBean;

  @JobWorker(type = "sampleBean")
  public Map<String, Object> someMethod(@Variable(name = "y") String y) {
    return Map.of("theAnswer", sampleBean.someMethod(y));
  }
}
```

The baseline comparison is authoritative. A class that existed in the C7 source is not an adapter,
even when its name ends with `Worker`. Record the baseline match and the replacement adapter in
`MIGRATION_REPORT.md`.

Apply this rule to JUEL method-invocation findings in M1, M2, M3, and E1. E1 uses the M1 local
conversion flow after it acquires the source models.

## Approach M2 - Agentic AI (direct XML rewrite)

Use when Java 21 is unavailable, the user wants to review every change, or the CLI cannot handle a case.

Fetch the current diagram-conversion guidance:
`https://raw.githubusercontent.com/camunda/camunda-docs/main/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter.md`

Before rewriting the first model, collect the M2 `scriptJobType` input. Ask the user for the job
type used by non-internal script tasks. Use `scriptTask` when the user accepts the default. Record
the selected value and its source in `MIGRATION_REPORT.md`. Do not derive the value from
`scriptFormat` or invent a different type during model validation.

### Job type naming

Use the Diagram Converter naming rules as the binding convention for M2. Apply the rule that matches
the original Camunda 7 implementation attribute.

| Camunda 7 source | Job type rule | Example |
|---|---|---|
| `camunda:delegateExpression` or `camunda:expression` with a bean reference | Remove the `${...}` or `#{...}` wrapper. Keep the first path segment unchanged. Capitalize the first character of each later path segment. | `${sampleBean}` becomes `sampleBean`. |
| `camunda:delegateExpression` or `camunda:expression` with a method invocation (expression method) | Unwrap the expression. Replace each `.` with an uppercase first character of the following segment. Remove the `(...)` argument list after the camel-case transformation. | `${sampleBean.someMethod(x)}` becomes `sampleBeanSomeMethod`. |
| `camunda:class` on a job-backed task | Take the class name after the final dot. Decapitalize its first character. | `com.example.SampleDelegate` becomes `sampleDelegate`. |
| `camunda:delegateExpression` on an execution or task listener | Copy the converter's extracted listener implementation unchanged. Do not apply task job-type camel-case rules. | `${sampleListener}` becomes `sampleListener`. |
| `camunda:expression` on an execution or task listener | Copy the listener implementation value unchanged. Do not apply task job-type camel-case rules. | `${sampleListener.handle(execution)}` remains the listener implementation value. |
| `camunda:topic` on an external task | Copy the topic value without changing it. | `invoice-processing` remains `invoice-processing`. |
| `camunda:connectorId` | Copy the connector ID unchanged as the job type. Verify that a matching connector registration or explicit connector handler exists. | `http-json` remains `http-json`. |
| Non-internal `bpmn:scriptTask` | Use the configured M2 script job type unchanged. Preserve `bpmn:scriptFormat` as binding evidence. | A `groovy` script uses the configured script job type and retains `groovy` as its format. |
| `camunda:class` on an execution or task listener | Copy the fully qualified listener implementation unchanged. Do not decapitalize the class name. | `com.example.SampleListener` remains `com.example.SampleListener`. |

For JUEL method-invocation findings, apply the shared [worker adapter rule](#juel-method-invocation-worker-adapters).

Treat a job type that differs from this table as an intentional deviation only when
`MIGRATION_REPORT.md` records the source file and element, the original implementation, the emitted
job type, and the confirmed rationale. Record the same decision for a custom or shared job type that
has no source binding in the table. Do not replace a method-specific type with the bean-only type.

For each in-scope diagram, produce a new `converted-c8-<name>.bpmn`/`.dmn` (never edit the original), applying:

- `camunda:` namespace/extension elements to `zeebe:` equivalents (task definitions/job types, IO mappings, headers)
- remove C7 generated-form elements from the converted copy after their source inventory is captured. `form-migration.md` creates separate standard `.form` resources.
- Execution/task listeners to `zeebe:executionListeners` / user task listeners
- JavaDelegate/expression references to job types (or blank, to be filled)
- Simple JUEL to FEEL for pure data expressions. Flag bean-invoking expressions for manual work.
- Never translate complex script or Groovy condition logic into FEEL automatically. Preserve the source for review, and require an explicit worker/service-task or other user-approved redesign.
- Conditional events are native only on 8.9+. Otherwise flag them.
- DMN: update decision/definition namespaces and expression language as needed

Emit a findings summary mirroring CLI severities (WARNING/TASK/REVIEW/INFO), and ask for human review. Lint every rewritten BPMN file per the linting section below. After the converted copy exists, run `form-migration.md` and `form-reference-migration.md` against the original/converted pair.

## Approach M3 - Online Diagram Converter (hosted)

Point the user to the hosted converter:

> Upload your BPMN/DMN files at https://diagram-converter.camunda.io/, set the target version there, and download the converted results.

This path does not automate the hosted service. Once the user brings the converted files back, offer the same findings follow-up as M1 step 5. For machine-readable findings, use the hosted converter's 'Download JSON' button. It produces the same `analysis-results.json` the CLI writes. Its CSV/markdown/XLSX downloads are not parsed (see 5a). The imported-report version check in step 5 applies.

Generated-form follow-up also requires the exact original BPMN and an unambiguous pairing to each downloaded converted BPMN. Ask for either missing artifact rather than reconstructing C7 form metadata from the report.

## Approach E1 - Camunda 7 Engine Source (only when no local models found)

### 1. Ask for C7 Access

Use AskUserQuestion to request:

- The C7 engine REST base URL, including the `/engine-rest` context path when applicable.
- Authentication: no authentication, or Basic authentication username/password.
- Obtain secrets through the agent's secure credential mechanism. Never write them to MIGRATION_REPORT.md or commit them.

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

## Linting Converted BPMN (M1 and M2)

After any manual BPMN edit (findings follow-up, form wiring, expression fixes), lint immediately with bpmnlint and the Camunda compatibility ruleset for the target version. Missing DI, overlaps, and disconnected flows are cheapest to catch at edit time.

```sh
npm install -D bpmnlint zeebe-bpmn-moddle bpmnlint-plugin-camunda-compat   # once
npx bpmnlint <converted-file>.bpmn
```

`.bpmnlintrc`: extend `bpmnlint:recommended` and `plugin:camunda-compat/camunda-cloud-<target-major>-<target-minor>` (for example, `camunda-cloud-8-9` for target 8.9), and set `moddleExtensions.zeebe` to `zeebe-bpmn-moddle/resources/zeebe.json`. Fix or record every lint error before continuing.

## Analyze-Only Mode

For "analyze but don't convert": run M1 with `--check --json --xlsx` (no converted files), or do an M2 read-only pass. Parse and present findings grouped by category as in M1 step 5. Include the namespace-derived Generated Task Form inventory, the referenced-form inventory from `form-reference-migration.md`, and likely decision categories. Do not create `.form` files or edit BPMN. Then stop.
