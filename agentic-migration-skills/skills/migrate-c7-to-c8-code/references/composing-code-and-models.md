# Composing Code + Model Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Use this when the scope is Code + models.

## Execution Order

The two paths are independent.
Run models first because the diagrams define the job types and listeners that the code must implement. (SHOULD)
Follow the user's preference.

## Cross-Check After Both Complete

Cross-reference the grouped Diagram Converter findings (see `model-migration-approaches.md` step 5) against the code migration output. First detect the mapping shape, then apply the matching check.

When the user selects Approach C, the skill keeps this cross-check report-only. The skill does not offer
or generate a dispatcher scaffold. The skill records the category, its routing gaps, and the
recommended Approach A or B in `MIGRATION_REPORT.md`.

When M1 runs with `--check` and produces no converted copy, keep this cross-check report-only. Do not
offer or generate a dispatcher scaffold until a paired converted copy is available. Record the
missing copy and the required rerun in `MIGRATION_REPORT.md`.

When M2 is in scope without a Diagram Converter report, scan every `zeebe:taskDefinition/@type` in
each converted BPMN file. Read the corresponding original Camunda 7 implementation attribute and
derive the expected type from the M2 binding rules in `model-migration-approaches.md`. Create one
normalized input row with the columns `filename`, `elementId`, `headerKey`, `original`, and
`jobType` for each delegate attribute or external-task topic. For delegate attributes, read
`headerKey` and `original` from the matching `zeebe:header` on the converted element. For an
external-task topic, set `headerKey` to `topic` and `original` to the topic value. Apply the same
1:1 or many-to-one check. Do not wait for `delegate-expression-as-job-type` findings, because
M2-only runs do not produce them. If a delegate row has no header matching the original C7
attribute and value, then mark the row incomplete. For a topic row, retain a non-empty `jobType`
row without a `topic` header for the initial grouping. A 1:1 topic row can use its `jobType` for
the simple check. If its job-type group has another distinct pair, mark the topic row incomplete
and require a retained `topic` header before offering a dispatcher scaffold.

### 1. Detect many-to-one job-type collapse

Build the normalized input rows from the `delegate-expression-as-job-type`, `delegate-implementation`,
and `topic` findings and the M2 scan. A findings report's `filename` identifies the source model, not
the converted copy. Resolve it to the exact converted copy path captured from the converter output,
using the configured prefix (`converted-c8-` by default) when necessary. Pair each finding with its
converted BPMN element by that path and `elementId`. Read the emitted job type from its task
definition and the original C7 key and value from its `zeebe:header`.
For a converter finding, use the paired header instead of parsing only its `message`. For a
`delegate-implementation` finding, retain the original class or expression from its binding
context or the paired original source attribute, then resolve its header pair on the converted
element. For an M2 row, use the original source attribute and `jobType` created above, and verify
the same pair in the converted element. For a `topic` finding, set `headerKey` to `topic` and
`original` to the paired `camunda:topic` value. Record a missing retained `topic` header, but keep
the row for the initial job-type grouping. A 1:1 topic row can use its `jobType` for the simple
check. If the group has another distinct pair, treat the missing topic header as incomplete and
require it before offering a dispatcher scaffold. Do not classify a topic and delegate or class
that share a job type as 1:1 without that routing discriminator. Each normalized row has the shape:

> `filename`: Converted BPMN file
> `elementId`: Converted element identifier
> `headerKey`: Original C7 attribute name
> `original`: Delegate class or expression '\<original\>'
> `jobType`: '\<jobType\>'

Identify incomplete rows before and after grouping:

| Row state | Condition | Action |
|---|---|---|
| **Incomplete before grouping** | A row has a missing or blank `jobType`, or a delegate row lacks a matching retained header pair. | Exclude the row from collapse grouping. Keep its category **needs fix** until model migration supplies a non-empty type or the user chooses one in the Step 5 AI Follow-up flow. The model must also retain the header pair. |
| **Incomplete after grouping** | A topic row lacks a retained `topic` header and its job-type group has another distinct pair. | Keep the shared group and its category **needs fix**. Do not offer a dispatcher scaffold until model migration retains the header. |

While any incomplete row or group remains in a category, do not offer or generate a dispatcher
scaffold for any group in that category. Resolve the row through model migration or the Step 5
AI Follow-up flow. Then rebuild the normalized rows and regroup before applying the mapping and verdict
checks.

Build one shared job-type inventory from the remaining normalized rows across all three categories.
Group the inventory by `jobType`, then classify each job-type group by its distinct
`(headerKey, original)` pairs. A shared job-type group can contain rows from multiple categories.
Assign one mapping and dispatcher verdict to each shared job-type group. Project that verdict and its
evidence into every affected category. Do not let a category-local 1:1 result override a shared
many-to-one group.

| Mapping | Group condition | Action |
|---|---|---|
| **1:1** | A job-type group contains one distinct `(headerKey, original)` pair. | Apply the simple check in 2a. |
| **Many-to-one** | A job-type group contains multiple distinct `(headerKey, original)` pairs. | Apply the dispatcher check in 2b. The converter collapsed several delegates onto a shared job type. |

This shape is common at scale. One generic job type can cover thousands of expression-based service tasks in a real project.

For rows in a `delegate-implementation` category, apply the same pair-count rule within the shared
inventory. A shared default job type does not by itself make a category many-to-one.

### 2a. 1:1 mapping - simple job-type match

Before either mapping check, enumerate every existing `@JobWorker` registration and resolve its
effective type. When an annotation omits `type`, use the annotated method name. Use this effective
type for the 1:1 comparison and for duplicate-subscriber detection.

Use this table for each 1:1 job-type group:

| Effective workers for the job type | Condition | Verdict and action |
|---|---|---|
| None | No worker has the job type as its effective type. | Mark **needs fix** and identify the missing worker. |
| Exactly one | The worker's effective type matches the job type. | Mark **no action** for the worker mapping. |
| Exactly one | The worker's effective type does not match the job type. | Mark **needs fix** and flag the mismatch for the user. |
| More than one | Multiple workers have the job type as their effective type. | Mark **needs fix** and require the user to consolidate the duplicate subscribers. |

Use the Diagram Converter output for M1 and the binding rules in `model-migration-approaches.md` for
M2.

### 2b. Many-to-one mapping - dispatcher/adapter worker needed

Do NOT generate one `@JobWorker` per BPMN element for a collapsed job type. They would all subscribe to the same type and race for the same jobs.

Instead, flag for the user that the shared job type needs a single dispatcher/adapter job worker:

- One `@JobWorker(type = "<shared job type>")` for the whole group.
- It reads the retained original expression from the job's task headers. The converted element must preserve it as a `zeebe:header` (inside `zeebe:taskHeaders`). Its key is the original C7 attribute name (`expression`, `delegateExpression`, `class`, or `topic`). Its value is the original expression or topic string (e.g. `${myBean.myMethod(execution)}`). For M2, verify or add the matching delegate or topic header during model migration before scaffolding.
- It routes on that header value to the correct legacy bean or method (e.g. a Spring bean lookup by name, or an explicit mapping table).

Cross-check for this shape: exactly one worker subscribes to the shared job type. Its routing covers
every distinct `(headerKey, original)` pair in the normalized rows for that job type across all three
categories. List uncovered pairs for the user.

Record the detected shape (1:1 vs many-to-one, per job type) in MIGRATION_REPORT.md.

When a shared job-type group has a **needs fix** verdict, process it independently. Propagate its
verdict to every affected category before assigning category verdicts.

Before asking for a decision, use the effective-type inventory to identify registrations whose
effective type resolves to the shared type. If any registration already subscribes to that type,
then stop scaffold generation. Do not create a second subscriber.

Use `.camunda-migration/generated-worker-drafts/` under the confirmed project root as the default
quarantine directory. Allow an explicit user override only when it remains outside runtime source
sets and every source tree scanned for `@JobWorker`. If `MIGRATION_REPORT.md` records a path, reuse
it on later invocations unless the user explicitly overrides it. Record the selected path in
`MIGRATION_REPORT.md` before scanning or generating.

Before generating, scan the selected quarantine directory for a prior draft whose worker annotation
uses the shared type. If one exists, stop and ask the user whether to reuse, complete, or remove
that draft. Do not create another draft or collision variant until the prior draft is resolved.

Assign a cross-check verdict to each shared job-type group before assigning the category verdict.
Offer generation only for a group with a **needs fix** verdict and no effective worker. Do not offer
generation for a group with a **no action** or **needs review** verdict.

Use this decision table for each shared job type:

| Verdict | Effective worker for the shared type | Action |
|---|---|---|
| **no action** | Any | Do not offer a scaffold. Record the covered pairs. |
| **needs review** | Any | Collect the pending user decision before offering a scaffold. |
| **needs fix** | None | Use AskUserQuestion to ask whether to **Generate a dispatcher scaffold** (SHOULD) or **I will implement the dispatcher manually** (MAY). In the generation prompt, show the shared job type, every retained header key, and the distinct original expressions grouped by retained key. |
| **needs fix** | Exactly one | Do not offer generation. Ask the user to extend the registration if it is a dispatcher, or merge or remove the non-dispatcher before creating one. |
| **needs fix** | More than one | Do not offer generation. Ask the user to consolidate registrations to exactly one dispatcher. Extend one dispatcher and merge or remove every other registration before resolving the group. |

Generate the scaffold only after the user chooses the first option. Write the draft to a quarantine
directory outside every runtime source set and every source tree scanned for `@JobWorker`
registrations. Use the project's conventional package, license header, naming, and formatting.
Derive the class and file names from the exact shared job type with a deterministic sanitizer. Make
the class name a legal Java identifier and the file name a safe path segment.
Append `Worker` to the sanitized base unless it already ends with `Worker`, and use that same
`Worker` stem for the class and file.
Include a stable hash of the original job type to prevent collisions between sanitized names.
Resolve the proposed quarantine path and verify that it stays inside the chosen quarantine directory
before writing. Resolve the eventual runtime path separately before moving the accepted source. If
either path escapes its intended directory, stop and ask the user to choose a safe directory. Never
overwrite an existing file. If the proposed path exists, choose a new collision-safe class and file
name from the same candidate stem, then tell the user which file was created. Never reuse the
original class name with a renamed file.

The generated Java source must contain exactly one `@JobWorker(type = "<shared job type>")`. Use
the project's worker registration convention, such as `@Component` for Spring. Use a method
signature compatible with its Camunda 8 SDK. Read each original expression from the retained
`zeebe:header` using its original C7 key. Prepopulate a routing map or switch with one entry for
every distinct normalized `(headerKey, original)` pair for the shared job type, grouped by retained
key. Use Java
string-literal escaping for every generated route key and for the shared job type in the
annotation. Escape quotes, backslashes, line breaks, and other control characters before writing
the source. Put a `TODO` in every route for the actual legacy bean or method invocation. Make each
TODO route fail explicitly until its implementation exists. Add an explicit missing-or-unknown-header
path that also fails instead of silently accepting or auto-completing an unroutable job.

After generation, present the complete source or diff to the user for explicit review. Keep the draft
in quarantine while the user reviews it. Do not treat review approval as approval to enable the
draft. Replace every TODO route with its actual legacy invocation while the draft remains quarantined.
After every known route is implemented, ask the user to accept the completed source. On acceptance,
remove draft-only markers, move the source into the intended worker source tree, and run the
applicable formatter, compile, and test checks before deployment. If the user rejects the scaffold,
remove the draft or keep it outside every scanned source tree. Do not leave the file beside the
migrated sources or let a later scan treat it as an existing subscriber. Then rerun the same
cross-check used for hand-written dispatchers. Record each validation result in MIGRATION_REPORT.md.
The scaffold is not a completed remediation. Keep the category **needs fix** while any known route
has an unresolved TODO, placeholder, or unconditional throw in a generated or hand-written
dispatcher, the cross-check finds an uncovered pair, or any applicable formatter, compile, or test
check fails. Do not count the required missing-or-unknown-header failure as a known-route throw.
Mark the category **no action** only after the cross-check confirms coverage, every known route
invokes its mapped implementation without an unresolved TODO, placeholder, or unconditional throw in
that route, and all applicable post-generation checks pass. Keep the required missing-or-unknown-
header guard. Record the generated file and uncovered implementation work in MIGRATION_REPORT.md.

### 3. FEEL method-invocation category

Take all rows with messageId `expression-method-not-possible` (message contains "Method invocation is not possible in FEEL"). These are the model-side occurrences of FEEL method-invocation (`code-transform-checklist.md` item 7): a JUEL expression invoked a Java method, on a bean or a plain variable (e.g. `${execution.getVariable("a").size()}`). The category applies regardless of element type: sequence-flow condition expressions, `multiInstanceLoopCharacteristics` `collection`/completion conditions, callActivity `calledElement`, timer expressions, input/output parameters, or job/user-task attributes (assignee, dueDate, priority, ...). The remediation is the same in every case: a preceding job worker, execution listener, or DMN business rule table computes the value into a plain variable that FEEL can read.

Handle these rows as ONE named category, not one by one:

- Group every occurrence under the named category **FEEL method-invocation** and surface one total count. Break it down per element type via the `elementType` column or message context prefix (MAY). Never list occurrences row by row. This is often the single largest work item in a real report.
- Present one recommended decision point for the whole category, listing ALL options: **precompute via job worker** (default), **compute via execution listener** (no visible shape added), **refactor into DMN** (business-rule logic), or the exceptional **JUEL job worker** fallback. The full decision process is `code-transform-checklist.md` item 7. Let the user decide once per category, or per sub-group of occurrences sharing one invoked method/expression.
- Cross-check against the code migration output. Extract each distinct invoked method/expression from the findings' `message` column. Check that the chosen remediation covers each one. The remediation is either a `@JobWorker` (service task or execution listener) that computes the value into a variable, or a DMN definition referenced by a preceding business rule task. List invoked methods with no remediation as uncovered.

Record the category, its total count, the decision taken, and any uncovered invoked methods in MIGRATION_REPORT.md.

### 4. Generated-form code and behavior

For every `form-data` or `generated-form-property-source` item, cross-check the code inventory before accepting the generated form:

- Locate `FormFieldValidator` implementations and validator beans or classes named by `camunda:constraint name="validator"`.
- Locate `FormService`, `TaskFormData`, `StartFormData`, `FormField`, `FormProperty`, `submitTaskForm`, `submitStartForm`, and form REST API consumers.
- Locate code that depends on a form field becoming the business key, custom field properties, C7 Java `Date`/`Long` values, form-property aliases/expressions, or server-side validation.
- Check that the chosen form mapping and any worker/listener/API redesign cover every consumer.

Use `form-migration.md` to collect decisions. Uncovered consumers are **needs fix**. A pending mapping or enforcement decision is **needs review**. A generated form is not accepted merely because its JSON renders.

### 4a. Referenced-form code and behavior

For every `c7-embedded-html-form`, `c7-external-form-reference`, `c7-camunda-form-reference`, `c7-dynamic-form-reference`, and `c7-generic-task-form` item, cross-check the code inventory before closing the category:

- Locate the custom application or Tasklist customization that resolves the reference, and any code that builds a form key at runtime for a dynamic reference.
- Locate `FormService`, `submitTaskForm`, `submitStartForm`, `/form-variables`, and Camunda 7 task REST API clients. A kept external application depends on these callers, and they must all be rewritten against the Camunda 8 Orchestration Cluster API.
- Locate code that serves or packages embedded form HTML (for example resources under `src/main/webapp/forms`) so the user can decide what happens to those files.

Use `form-reference-migration.md` to collect decisions. A category stays **needs review** until its procedure-specific decision or prerequisite is complete. Embedded, external, and generic forms need a remediation decision. Camunda Form references need form discovery and a binding decision. Dynamic references need possible-value enumeration before a decision. The category is then **needs fix** until the relevant work is finished: a rebuilt form is accepted and linked, a custom-application integration is confirmed by a named owner, or a Camunda Form is converted, relinked, and deployed. If the `.form` file cannot be found for a Camunda Form reference, keep the row `blocked` and the category at **needs review** until the user resolves that prerequisite. A kept reference is never **no action** on the strength of the converter having copied it.

### 5. Now-redundant workaround code (deletion candidates)

Some findings describe the opposite of missing support: a C7-side workaround is obsolete because Zeebe now provides the capability natively. Detect this family primarily by `messageId`. Fall back to `message` content for rows whose `message` contains "now natively possible with Zeebe". This catches future family members whose `messageId` is not yet known. Today the family has one member: `collection-hint` ("Collecting results in a multi instance is now natively possible with Zeebe. Please review.", TASK), emitted once per converted multi-instance `camunda:collection`.

For each row in this family, find the code that manually implemented what Zeebe now does natively, and flag it as a **deletion candidate**. For multi-instance result collection, the typical C7 workaround is an execution listener on the multi-instance body's `end` event, or a delegate inside the body, that appends each instance's result to an aggregate collection variable:

1. Resolve the element from the row's `filename` + `elementId`. This is the multi-instance activity.
2. Find listeners and delegates attached to that element. Rows from the `execution-listener` / `execution-listener-supported` categories whose `elementId` matches the finding row's `elementId` name the listener implementation. `delegate-expression-as-job-type` rows with the same `elementId` name the delegate and its job type. Map both to classes via the code inventory (SKILL.md step 2), and after code migration to the corresponding `@JobWorker`s.
3. Inspect each candidate's body. If its purpose is to aggregate instance results into a collection variable (read a per-instance result, append to a list, write it back), it is a deletion candidate. Zeebe collects results natively via `outputCollection`/`outputElement` on `zeebe:loopCharacteristics`.
4. Record every deletion candidate (file, class/method, the finding row that triggered it) in MIGRATION_REPORT.md. Never delete code during the cross-check.

A candidate is safe to delete only once the converted copy actually uses the native capability (for multi-instance results, `outputCollection`/`outputElement` are set, which the converter does not set automatically), or the user confirms the aggregation is no longer needed. Both are user decisions, collected in the Step 5 AI Follow-up flow.

### 6. Assign verdicts to the verdict table

Use this table only for categories with normalized rows from sections 1–2. This cross-check includes
`delegate-expression-as-job-type`, `delegate-implementation`, and `topic` findings. Do not apply this
table to categories with dedicated procedures, including `delegate-implementation-no-default-job-type`,
`collection-hint`, and form categories. Use those procedures to assign their verdicts. An empty
normalized-row set never produces **no action**.

Before assigning a category verdict, include every shared job-type group that contains a row in the
category. Use the shared group verdict and evidence. Do not recompute a category-local verdict from
`messageId` rows alone. Record the matched worker registration, dispatcher source, or generated draft
for each shared job-type group in `MIGRATION_REPORT.md`.

| Evidence across every normalized row and shared job type | Cross-referenced code artifact | Verdict |
|---|---|---|
| Every normalized row has a non-empty `jobType` and either a matching retained `(headerKey, original)` pair or is a 1:1 topic row checked directly by `jobType`, and every shared job-type group containing a row from the category has either exactly one confirmed 1:1 worker match or exactly one dispatcher covering every distinct `(headerKey, original)` pair with no unresolved TODO, placeholder, or unconditional throw in any known route and passing all applicable validation checks. | Record the matched worker registration or dispatcher source in `MIGRATION_REPORT.md`. | **no action** |
| Any normalized row has a missing or blank `jobType`, any delegate row lacks a matching retained `(headerKey, original)` pair, any many-to-one topic group lacks a retained `topic` header, any shared job-type group does not have exactly one effective worker, any 1:1 worker mismatch exists, any shared job-type group has an uncovered pair or an unresolved TODO, placeholder, or unconditional throw in a known route, any invoked method is uncovered, or any applicable validation check fails. | Record the existing or missing worker artifact and the unresolved implementation work in `MIGRATION_REPORT.md`. | **needs fix**, which becomes an AI follow-up work item. |
- Remediation decision still pending for a category (e.g. the FEEL method-invocation option not yet chosen): **needs review**.
- Deletion candidates recorded for a now-redundant workaround category: **needs review**, because removing code always requires an explicit user decision. When no workaround code exists for any row in such a category, the finding is informational: **no action**.
- Generated forms with uncovered code consumers or incomplete linkage/deployment: **needs fix**. Pending form or validation decisions: **needs review**. Only accepted, validated, linked, and deployed forms with covered consumers become **no action**.

Apply the fallback when a category has no dedicated cross-check in step 5d and no named form procedure:

| Finding severity | Fallback verdict | Cross-reference |
|---|---|---|
| INFO | no action | no dedicated cross-check |
| REVIEW | needs review | no dedicated cross-check |
| WARNING or TASK | needs fix | no dedicated cross-check |

Copy the finding's `link` into the verdict table's `Link` column. Surface that link as the
remediation starting point. Do not infer a category-specific cross-check from an unknown
`messageId`, its message text, or a similar category.

## Deployment Wiring

After both complete, ask via AskUserQuestion whether to wire deployment of converted files in application code:

- **Yes, add/update @Deployment for converted files** (recommended when code scope includes a Spring Boot app) - add or update `@Deployment(resources = ...)` so it targets only converted resources with explicit recursive classpath patterns. Include accepted generated forms when present, for example: `@Deployment(resources = {"classpath*:**/converted-c8-*.bpmn", "classpath*:**/converted-c8-*.dmn", "classpath*:**/converted-c8-*.form"})`. Never target original diagrams, draft forms, or declined forms.
- **No, I will handle deployment outside app startup** - leave code unchanged and record this decision in MIGRATION_REPORT.md.

## Report Keeping

Keep both inventories and both sets of results in `MIGRATION_REPORT.md` in the confirmed project root.
