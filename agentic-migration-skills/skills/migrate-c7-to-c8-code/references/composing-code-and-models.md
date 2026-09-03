# Composing Code + Model Migration

When the scope is Code + models:

## Execution Order

The two paths are independent. A reasonable default is models first (diagrams define the job types/listeners the code must implement), then code, but follow the user's preference.

## Cross-Check After Both Complete

Cross-reference the grouped Diagram Converter findings (see `model-migration-approaches.md` step 5) against the code migration output. First detect the mapping shape, then apply the matching check.

### 1. Detect many-to-one job-type collapse

Take all rows in the `delegate-expression-as-job-type` category. Each message has the shape:

> Delegate class or expression '\<original\>' has been transformed to job type '\<jobType\>'.

Extract the original expression and the job type from each row's `message` and group by job type:

- **1:1**: every job type maps to exactly one original expression — apply the simple check in 2a.
- **Many-to-one**: one job type maps to multiple distinct original expressions — the converter collapsed several delegates onto a shared job type. Apply the dispatcher check in 2b. This shape is common at scale: in real projects a single generic job type can cover thousands of expression-based service tasks.

Also treat the `delegate-implementation` category (emitted when the converter ran with a configured default job type) as inherently many-to-one: every row shares the same job type.

### 2a. 1:1 mapping - simple job-type match

Job types emitted by the Diagram Converter should match the `@JobWorker(type = ...)` values produced by the code migration. Flag mismatches for the user.

### 2b. Many-to-one mapping - dispatcher/adapter worker needed

Do NOT generate one `@JobWorker` per BPMN element for a collapsed job type - they would all subscribe to the same type and race for the same jobs.

Instead, flag for the user that the shared job type needs a single dispatcher/adapter job worker:

- One `@JobWorker(type = "<shared job type>")` for the whole group.
- The worker reads the retained original expression from the job's task headers. The converter always preserves it as a `zeebe:header` entry (inside `zeebe:taskHeaders`) whose key is the original C7 attribute name (`expression`, `delegateExpression`, or `class`) and whose value is the original expression string (e.g. `${myBean.myMethod(execution)}`).
- The worker routes on that header value to the correct legacy bean/method (e.g. via a Spring bean lookup by name, or an explicit mapping table).

The cross-check for this shape: verify exactly one worker subscribes to the shared job type, and that its routing covers every distinct original expression found in the findings rows for that job type. List uncovered expressions for the user.

Record the detected shape (1:1 vs many-to-one, per job type) in MIGRATION_REPORT.md.

### 3. FEEL method-invocation category

Take all rows with messageId `expression-method-not-possible` (message contains "Method invocation is not possible in FEEL"). Regardless of element type — sequence-flow condition expressions, `multiInstanceLoopCharacteristics` `collection`/completion conditions, callActivity `calledElement`, timer expressions, input/output parameters, or job/user-task attributes (assignee, dueDate, priority, ...) — the root cause is identical: a JUEL expression invoked a Java method — on a bean or on a plain variable (e.g. `${execution.getVariable("a").size()}`) — which FEEL cannot do. The remediation pattern is identical too: a preceding job worker, execution listener, or DMN business rule table computes the value into a plain variable that FEEL can read.

Handle these rows as ONE named category, not individually:

- Group every occurrence under the named category **FEEL method-invocation** and surface it with a single total count (optionally broken down per element type via the `elementType` column / message context prefix). Never list occurrences row by row — this is often the single largest work item in a real report.
- Present one recommended decision point for the whole category, listing ALL options: **precompute via job worker** (default) vs **compute via execution listener** (no visible shape added) vs **refactor into DMN** (business-rule logic) vs the exceptional **JUEL job worker** fallback (full decision process: `code-transform-checklist.md` item 7). Let the user decide once per category, or per sub-group of occurrences sharing one invoked method/expression.
- Cross-check against the code migration output: extract each distinct invoked method/expression from the findings' `message` column and verify the chosen remediation covers it — a `@JobWorker` (service task or execution listener) that computes the value into a variable, or a DMN definition referenced by a preceding business rule task. List invoked methods with no remediation as uncovered.

Record the category, its total count, the decision taken, and any uncovered invoked methods in MIGRATION_REPORT.md.

### 4. Generated-form code and behavior

For every `form-data` or `generated-form-property-source` item, cross-check the code inventory
before accepting the generated form:

- Locate `FormFieldValidator` implementations and validator beans/classes named by
  `camunda:constraint name="validator"`.
- Locate `FormService`, `TaskFormData`, `StartFormData`, `FormField`, `FormProperty`,
  `submitTaskForm`, `submitStartForm`, and form REST API consumers.
- Locate code that depends on a form field becoming the business key, custom field properties,
  C7 Java `Date`/`Long` values, form-property aliases/expressions, or server-side validation.
- Verify the chosen form mapping and any worker/listener/API redesign cover every consumer.

Use `form-migration.md` to collect decisions. Uncovered consumers are **needs fix**; a pending
mapping or enforcement decision is **needs review**. A generated form is not accepted merely
because its JSON renders.

### 4a. Referenced-form code and behavior

For every `c7-embedded-html-form`, `c7-external-form-reference`, `c7-camunda-form-reference`,
`c7-dynamic-form-reference`, and `c7-generic-task-form` item, cross-check the code inventory
before closing the category:

- Locate the custom application or Tasklist customization that resolves the reference, and any code
  that builds a form key at runtime for a dynamic reference.
- Locate `FormService`, `submitTaskForm`, `submitStartForm`, `/form-variables`, and Camunda 7 task
  REST API clients. These are the callers a kept external application depends on, and they must all
  be rewritten against the Camunda 8 Orchestration Cluster API.
- Locate code that serves or packages embedded form HTML (for example resources under
  `src/main/webapp/forms`) so the user can decide what happens to those files.

Use `form-reference-migration.md` to collect decisions. A category is **needs review** until its
procedure-specific decision or prerequisite is complete: a remediation decision for embedded,
external, and generic forms; form discovery and a binding decision for Camunda Form references; or
possible-value enumeration before a decision for dynamic references. It is then **needs fix** until
the relevant work is finished: a rebuilt form is accepted and linked, a custom-application
integration is confirmed by a named owner, or a Camunda Form is converted, relinked, and deployed.
If the `.form` file cannot be found for a Camunda Form reference, keep the row `blocked` and the
category at **needs review** until the user resolves that prerequisite.
A kept reference is never **no action** on the strength of the converter having copied it.

### 5. Now-redundant workaround code (deletion candidates)

Some findings do not describe missing support but the opposite: a C7-side workaround is obsolete because Zeebe now provides the capability natively. Detect this family primarily by `messageId`, falling back to `message` content — rows whose `message` contains "now natively possible with Zeebe" — to catch future family members whose `messageId` is not yet known. Today the family has one member: `collection-hint` ("Collecting results in a multi instance is now natively possible with Zeebe. Please review.", TASK), emitted once per converted multi-instance `camunda:collection`.

For each row in this family, hunt for the code that manually implemented what Zeebe now does natively, and flag it as a **deletion candidate**. For multi-instance result collection, the typical C7 workaround shape is an execution listener on the multi-instance body's `end` event, or a delegate inside the body, that appends each instance's result to an aggregate collection variable:

1. Resolve the element from the row's `filename` + `elementId` — this is the multi-instance activity.
2. Find listeners and delegates attached to that element: rows from the `execution-listener` / `execution-listener-supported` categories whose `elementId` matches the finding row's `elementId` name the listener implementation; `delegate-expression-as-job-type` rows with the same `elementId` name the delegate and its job type. Map both to classes via the code inventory (SKILL.md step 2) and, after code migration, to the corresponding `@JobWorker`s.
3. Inspect each candidate's body: if its purpose is to aggregate instance results into a collection variable (read a per-instance result, append to a list, write it back), it is a deletion candidate — Zeebe collects results natively via `outputCollection`/`outputElement` on `zeebe:loopCharacteristics`.
4. Record every deletion candidate (file, class/method, the finding row that triggered it) in MIGRATION_REPORT.md. Never delete code during the cross-check.

A candidate is only safe to delete once the converted diagram actually uses the native capability (for multi-instance results: `outputCollection`/`outputElement` are set — the converter does not set them automatically) or the user confirms the aggregation is no longer needed. Both are user decisions, collected in the Step 5 AI Follow-up flow.

### 6. Assign verdicts to the verdict table

Each cross-check result maps to a verdict in the per-category verdict table (see `model-migration-approaches.md` step 5d), with the matched code artifact named in the table's cross-reference column:

- 1:1 job-type match confirmed, dispatcher covering every original expression, or every invoked method covered by a remediation: **no action** (the category is fully covered).
- Mismatched job types, uncovered original expressions, or uncovered invoked methods: **needs fix** — these become AI follow-up work items.
- Remediation decision still pending for a category (e.g. the FEEL method-invocation option not yet chosen): **needs review**.
- Deletion candidates recorded for a now-redundant workaround category: **needs review** — removing code always requires an explicit user decision. When no workaround code exists for any row in such a category, the finding is informational: **no action**.
- Generated forms with uncovered code consumers or incomplete linkage/deployment: **needs fix**. Pending form or validation decisions: **needs review**. Only accepted, validated, linked, and deployed forms with covered consumers become **no action**.

## Deployment Wiring

After both complete, ask whether to wire deployment of converted files in application code via AskUserQuestion:

- **Yes, add/update @Deployment for converted files** (recommended when code scope includes a Spring Boot app) - add or update `@Deployment(resources = ...)` so it targets only converted resources with explicit recursive classpath patterns. Include accepted generated forms when present, for example: `@Deployment(resources = {"classpath*:**/converted-c8-*.bpmn", "classpath*:**/converted-c8-*.dmn", "classpath*:**/converted-c8-*.form"})`. Never target original diagrams, draft forms, or declined forms.
- **No, I will handle deployment outside app startup** - leave code unchanged and record this decision in MIGRATION_REPORT.md.

## Report Keeping

Keep both inventories and both sets of results in `MIGRATION_REPORT.md` in the confirmed project root.
