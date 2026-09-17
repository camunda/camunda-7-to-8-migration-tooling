# Composing Code + Model Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Use this when the scope is Code + models.

## Execution Order

The two paths are independent.
Run models first because the diagrams define the job types and listeners that the code must implement. (SHOULD)
Follow the user's preference.

## Cross-Check After Both Complete

Cross-reference the grouped Diagram Converter findings (see `model-migration-approaches.md` step 5) against the code migration output. First detect the mapping shape, then apply the matching check.
Run the `SKILL.md` Step 5 verification gate before assigning **no action** to a category with a
`converted-c8-*` BPMN or DMN copy.

When M2 is in scope without a Diagram Converter report, scan every `zeebe:taskDefinition/@type` in
each converted BPMN file. Read the corresponding original Camunda 7 implementation attribute and
derive the expected type from the M2 binding rules in `model-migration-approaches.md`. Create one
normalized input row with the columns `headerKey`, `original`, and `jobType` for each
original-implementation-to-emitted-type pair. Record the original C7 attribute name as `headerKey`.
Apply the same 1:1 or many-to-one check. Do not wait for `delegate-expression-as-job-type` findings,
because M2-only runs do not produce them.

### 1. Detect many-to-one job-type collapse

Build the normalized input rows from the `delegate-expression-as-job-type` and
`delegate-implementation` findings, and the M2 scan.
For a converter finding, parse the original expression and job type from its `message`. Locate the
converted BPMN element. Read its retained header key. For an M2 row, use the `headerKey`, `original`,
and `jobType` columns created above. Each normalized row has the shape:

> `headerKey`: Original C7 attribute name
> `original`: Delegate class or expression '\<original\>'
> `jobType`: '\<jobType\>'

Group the normalized rows by `jobType`:

- **1:1**: every job type maps to exactly one distinct `(headerKey, original)` pair. Apply the simple check in 2a.
- **Many-to-one**: one job type maps to multiple distinct `(headerKey, original)` pairs, so the converter collapsed several delegates onto a shared job type. Apply the dispatcher check in 2b. This shape is common at scale: one generic job type can cover thousands of expression-based service tasks in a real project.

For `delegate-implementation`, use the same distinct-pair count. One pair is 1:1.

### 2a. 1:1 mapping - simple job-type match

Job types in the converted model should match the `@JobWorker(type = ...)` values produced by the
code migration. Use the Diagram Converter output for M1 and the binding rules in
`model-migration-approaches.md` for M2. Flag mismatches for the user.

### 2b. Many-to-one mapping - dispatcher/adapter worker needed

Do NOT generate one `@JobWorker` per BPMN element for a collapsed job type. They would all subscribe to the same type and race for the same jobs.

Instead, flag for the user that the shared job type needs a single dispatcher/adapter job worker:

- One `@JobWorker(type = "<shared job type>")` for the whole group.
- It reads the retained original expression from the job's task headers. The converter always preserves it as a `zeebe:header` (inside `zeebe:taskHeaders`). Its key is the original C7 attribute name (`expression`, `delegateExpression`, or `class`). Its value is the original expression string (e.g. `${myBean.myMethod(execution)}`).
- It routes on the retained header key and value to the mapped legacy bean or method (e.g. a Spring bean lookup by name, or an explicit mapping table).

Cross-check for this shape: exactly one worker subscribes to the shared job type. Its routing covers
every distinct retained header key and original expression pair in the findings for that job
type. List uncovered pairs for the user.

Record the detected shape (1:1 vs many-to-one, per job type) in MIGRATION_REPORT.md.

#### Dispatcher scaffold

Use this table to decide whether to offer a scaffold:

| Findings | Distinct route pairs | Verdict | Dispatcher | Action |
|---|---|---|---|---|
| Missing | Any | Any | Any | Do not offer a scaffold. |
| Present | Fewer than two | Any | Any | Do not offer a scaffold. |
| Present | Two or more | **no action** or **needs review** | Any | Do not offer a scaffold. |
| Present | Two or more | **needs fix** | Present | Do not offer a scaffold. |
| Present | Two or more | **needs fix** | None | Use AskUserQuestion to offer these actions. |

| User choice | Result |
|---|---|
| **Generate a dispatcher scaffold** | Create a draft for review. |
| **I will implement the dispatcher manually** | Keep the category **needs fix**. |

Generate the scaffold only after the user selects it. Never overwrite an existing file.
Create the draft outside every configured Java source root. Use the target project's conventional
package, license header, naming, and formatting.
The source contains one `@JobWorker(type = "<shared job type>")`.
Use the project's bean-registration convention. Do not edit an existing registration source before
acceptance.
Prepopulate a routing map or switch with every distinct retained header key and original expression
pair from the findings.
Put a `TODO` in each route for the actual bean or method invocation.
Escape every model-derived value before using it in a Java string literal.
Make every TODO, missing-header, and unknown-route path fail explicitly.
Show the complete source to the user for review.
After review, use AskUserQuestion to ask the user to revise, accept, or decline the draft.
Do not treat the generation choice as acceptance.
Keep the draft outside every configured Java source root until the user completes its TODOs, accepts
it, and resolves any other subscriber.
When the user declines the draft, remove it.
After acceptance, move the draft beside migrated workers.
When the project requires an existing registration source, update it after acceptance.
Rerun the relevant Step 4 code checks and the existing dispatcher cross-check.
Keep the category **needs fix** until that check passes.

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

Each cross-check result maps to a verdict in the per-category verdict table (see `model-migration-approaches.md` step 5d). The table's cross-reference column names the matched code artifact:

- Complete job-type, dispatcher, or invoked-method coverage makes the category eligible for **no action** after the verification gate passes.
- Mismatched job types, uncovered retained header key and original expression pairs, or uncovered invoked methods: **needs fix**, which become AI follow-up work items.
- Remediation decision still pending for a category (e.g. the FEEL method-invocation option not yet chosen): **needs review**.
- A deletion candidate is **needs review**, because deleting code requires an explicit user decision.
- If no workaround exists, keep the category **needs review** until the verification gate passes.
- Generated forms with uncovered code consumers or incomplete linkage/deployment: **needs fix**. Pending form or validation decisions: **needs review**. Only accepted, validated, linked, and deployed forms with covered consumers become **no action**.

Apply the fallback when a category has no dedicated cross-check in step 5d and no named form procedure:

| Finding severity | Fallback verdict | Cross-reference |
|---|---|---|
| INFO | needs review until the verification gate passes | no dedicated cross-check |
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
