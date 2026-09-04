# Model Migration Approaches (Part B)

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

## Source Selection

Use the assessment model scan before choosing a path.

- If local model files exist under the project root, use local mode. Never offer or request C7 engine access.
- If none exist and the user selected E1, fetch definitions from C7 first.

Before conversion, namespace-parse the exact original BPMN and inventory every C7 form. Route Generated Task Forms (`camunda:formData`/`formField` and direct `camunda:formProperty`) to `form-migration.md`. Route referenced forms (`camunda:formKey`, `camunda:formRef`) and user tasks or process-level none start events with no form at all to `form-reference-migration.md`. Keep source path, process id, and owner id/type so each definition can be paired with a fresh converted output. The converter strips generated-form metadata and copies form-key references verbatim, so post-conversion discovery is too late or ambiguous.

## Pre-flight: Leftover Artifacts

Before any local approach (M1, M2, E1), scan for outputs of previous migration attempts:

- `converted-c8-*.bpmn` / `converted-c8-*.dmn` (or the `--prefix` equivalent)
- accepted generated forms beside converted BPMN, and drafts under `.camunda-migration/generated-form-drafts/`
- `analysis-results.csv` / `.json` / `.md` / `.xlsx`, including ` (n)`-suffixed siblings such as `analysis-results (1).json` — a sure sign of a previous run

Never flag the `.camunda-migration/` CLI JAR — an intentional cache, not a leftover.

If anything is found, warn through AskUserQuestion before converting:

> Found outputs from a previous migration attempt: `<list>`. This run will not overwrite them. The fresh analysis report is written alongside under a ` (n)`-suffixed name, and only this run's own outputs are used — stale files are never consumed. Diagrams whose `converted-c8-*` target already exists are skipped with an error, so for a full re-conversion, cancel and delete or move the old files first.

- **OK, proceed** — run without `-o`/`--override`. Old files stay untouched.
- **Cancel** — stop so the user can back up or clean up first.

For local approaches (M1, M2, E1), never consume a pre-existing report or converted file found on disk. It may come from an interrupted attempt or a different `--platform-version`. The findings flow (M1 steps 3-5) works only from this session's own run. M3 is the exception: hosted-converter outputs are allowed only after the imported-report version and pairing checks in step 5.

## Approach M1 - Diagram Converter CLI + AI (recommended)

### 1. Java 21+ Prerequisite (fail fast)

Run `java -version` from `PATH`, capture stderr, and record the actual major version. The Diagram Converter CLI requires major version `21` or higher. Do not apply the OpenRewrite upper bound. If `java` is missing or below 21, request an alternate JDK home through AskUserQuestion. Validate its `bin/java` (Windows: `bin/java.exe`) and check its actual version first. If several validated compatible homes exist, choose the lowest (prefer 21) for reproducible runs. Use the validated executable and home only for the converter invocation.

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
- `--check` - analyze-only (no converted diagrams exported)
- `-nr` / `--not-recursive` - disable recursive search

Other options:
- `--prefix <str>` - prefix for generated filenames (default `converted-c8-`)
- `--md` - write analysis report in markdown format

The converter writes a new file next to the source (e.g., `converted-c8-order-process.bpmn`), so originals are never mutated in place.

Capture the exact paths of everything the run produces from the `Created ...` lines in the CLI console output (e.g. `Created analysis-results (1).json`). These paths are the authoritative inputs for steps 4 and 5. Never glob for `analysis-results.json` or `converted-c8-*` on disk, which may match stale files from a previous attempt or a different `--platform-version`.

### 4. Surface Outputs

After the run, report:
- Converted files: every `converted-c8-*.bpmn` / `*.dmn` produced (from the captured `Created ...` lines).
- Skipped files: any `File already exists` errors, naming the stale targets. Those diagrams were NOT converted. Offer to re-run once the user removes the stale copies (see Pre-flight: Leftover Artifacts).
- Analysis findings: summarize from CLI stdout and/or the JSON report, grouped by severity (WARNING / TASK / REVIEW / INFO).
- Analysis artifacts: point the user to the XLSX report (human-readable), and note the JSON report is the step 5 input.
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
- **Keep the imported report** — proceed as-is and record in MIGRATION_REPORT.md that the findings target a different or unknown version.

#### 5a. Parse the JSON report

Read the JSON report programmatically, using the exact path captured from this run's `Created ...` line. It is written next to the converted files when `--json` is passed (which M1 always does), under a ` (n)`-suffixed name when a stale report exists. Never parse a pre-existing `analysis-results.json` found on disk. Never rely on stdout severity counts instead.

Format: a JSON array with one object per finding, fields:

```
filename, elementName, elementId, elementType, severity, messageId, message, link
```

Parse it with real JSON tooling (e.g. `jq` or a built-in JSON parser), never ad-hoc string splitting.

If the JSON report is missing (e.g. only `analysis-results.md` or a CSV/XLSX was generated), re-run the converter with `--check --json --xlsx` on the same input. The markdown and XLSX reports are for humans. CSV is never consumed — this skill has no CSV parsing path, and the JSON report is the only machine-readable findings source.

#### 5b. Group findings by category

Group findings by `messageId` (the category). For each category compute:

- Total count, and count per severity.
- Distinct `elementType` values affected (e.g. serviceTask, sequenceFlow, multiInstanceLoopCharacteristics).
- One representative example: a `message` with its `filename` and `elementId`.
- The `link` to conversion guidance for that category.

Sort categories by highest severity (TASK > WARNING > REVIEW > INFO), then count descending.

#### 5c. Present the grouped summary

Present the grouped table before any per-finding follow-up starts, and record it in MIGRATION_REPORT.md:

| Category (messageId or source category) | Severity | Count | Element types | Example |
|---|---|---|---|---|
| `expression-method-not-possible` | REVIEW | 1,308 | sequenceFlow, exclusiveGateway | "Method invocation is not possible in FEEL: ..." in order-process.bpmn, element `Gateway_1` |

#### 5d. Emit a per-category verdict table

After grouping (and after the code cross-checks in `composing-code-and-models.md` when code is also in scope), assign each WARNING/TASK/REVIEW category exactly one verdict, and record the table in MIGRATION_REPORT.md. INFO categories are optional (MAY). If included, they typically take verdict no action. Never leave findings as severity counts or a generic "findings need follow-up" note.

Verdicts:

| Verdict | Meaning | Required action |
|---|---|---|
| **no action** | The converter handled the category deterministically, the finding is purely informational (typical for INFO), or a cross-check confirmed full coverage. | Nothing to do. |
| **needs review** | A human decision is required before any fix can start. For example, choosing the remediation approach for a category or integration group (one decision per homogeneous category or group, not per row), or confirming a cross-check result. | Surface it in the AI follow-up step only to collect the pending user decision through AskUserQuestion before any fix. |
| **needs fix** | Concrete, known work remains: an uncovered cross-check item (job-type mismatch, uncovered original expressions, uncovered invoked methods) or a WARNING/TASK category with a clear remediation. | It is a direct work item for the AI follow-up step. |

| Category (messageId or source category) | Count | Cross-referenced code artifact | Verdict |
|---|---|---|---|
| `expression-method-not-possible` | 2,137 | none yet — remediation decision pending | needs review |
| `delegate-expression-as-job-type` | 2,491 | `DelegateDispatcher` @JobWorker (routes 38/42 expressions) | needs fix |
| `form-data` | 96 | one `.form` per C7 Generated Task Form (`camunda:formData` / direct `camunda:formProperty`, see 5f) | needs fix |
| `form-key-embedded` | 14 | none yet — keep/rebuild decision pending (see 5g) | needs review |
| `form-key-external` | 31 | `LoanFormsController` custom app — integration owner confirmed (see 5g) | needs fix |
| `c7-generic-task-form` | 8 | n/a — no finding, source-derived inventory (see 5g) | needs review |

Rules:

- One row per category, sorted as in 5b.
- The cross-referenced code artifact column names the `@JobWorker`, DMN definition, or other code element the cross-check matched, or `none yet` when no remediation exists. For models-only scope there is no code to cross-reference: use `n/a`. Derive a converter finding's initial verdict from severity alone (INFO → no action, REVIEW → needs review, WARNING/TASK → needs fix). Apply the procedure-defined lifecycle instead to source-derived synthetic categories and to `c7-*` categories that split a legacy generic `form-key` finding. Those categories have no independent converter severity.
- Classify every WARNING/TASK/REVIEW category. Never leave one without a verdict.
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
  - A dedicated `documentPreview` component (property `dataSource`, a FEEL expression over an array of document references) renders an inline preview and download link for a Document API reference. Prefer it over a plain-text filename display or a hand-built HTML anchor. When the process variable holds one document-reference object, wrap it in a one-element array in the form's FEEL only. Never change the variable to a form-specific array or a filename.
- **Add a worker only when the form genuinely cannot do it**: real business logic, external calls, side effects. A service task that only reshapes variables for form consumption is a C7 workaround. Never port it.

## Approach M2 - Agentic AI (direct XML rewrite)

Use when Java 21 is unavailable, the user wants to review every change, or the CLI cannot handle a case.

Fetch the current diagram-conversion guidance:
`https://raw.githubusercontent.com/camunda/camunda-docs/main/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter.md`

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
4. Record every corresponding definition id/key against that source path so the converted output can be paired exactly.
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
