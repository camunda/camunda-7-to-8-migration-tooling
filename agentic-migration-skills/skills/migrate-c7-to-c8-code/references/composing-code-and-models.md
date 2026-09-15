# Composing Code + Model Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Use this when the scope is Code + models.

## Execution Order

The two paths are independent.
Run models first because the diagrams define the job types and listeners that the code must implement. (SHOULD)
Follow the user's preference.

## Cross-Check After Both Complete

Cross-reference the grouped Diagram Converter findings (see `model-migration-approaches.md` step 5) against the code migration output. First detect the mapping shape, then apply the matching check.

When M2 is in scope without a Diagram Converter report, scan every `zeebe:taskDefinition/@type` in
each converted BPMN file. Read the corresponding original Camunda 7 implementation attribute and
derive the expected type from the M2 binding rules in `model-migration-approaches.md`. Create one
normalized input row with the columns `original` and `jobType` for each
original-implementation-to-emitted-type pair. Apply the same 1:1 or many-to-one check. Do not wait
for `delegate-expression-as-job-type` findings, because M2-only runs do not produce them.

### 1. Detect many-to-one job-type collapse

Build the normalized input rows from the `delegate-expression-as-job-type` findings and the M2 scan.
For a converter finding, parse the original expression and job type from its `message`. For an M2
row, use the `original` and `jobType` columns created above. Each normalized row has the shape:

> `original`: Delegate class or expression '\<original\>'
> `jobType`: '\<jobType\>'

Group the normalized rows by `jobType`:

- **1:1**: every job type maps to exactly one original expression. Apply the simple check in 2a.
- **Many-to-one**: one job type maps to multiple distinct original expressions, so the converter collapsed several delegates onto a shared job type. Apply the dispatcher check in 2b. This shape is common at scale: one generic job type can cover thousands of expression-based service tasks in a real project.

Also treat the `delegate-implementation` category (emitted when the converter ran with a configured default job type) as inherently many-to-one: every row shares the same job type.

### 2a. 1:1 mapping - simple job-type match

Job types in the converted model should match the `@JobWorker(type = ...)` values produced by the
code migration. Use the Diagram Converter output for M1 and the binding rules in
`model-migration-approaches.md` for M2. Flag mismatches for the user.

### 2b. Many-to-one mapping - dispatcher/adapter worker needed

Do NOT generate one `@JobWorker` per BPMN element for a collapsed job type. They would all subscribe to the same type and race for the same jobs.

Instead, flag for the user that the shared job type needs a single dispatcher/adapter job worker:

- One `@JobWorker(type = "<shared job type>")` for the whole group.
- It reads the retained original expression from the job's task headers. The converter always preserves it as a `zeebe:header` (inside `zeebe:taskHeaders`). Its key is the original C7 attribute name (`expression`, `delegateExpression`, or `class`). Its value is the original expression string (e.g. `${myBean.myMethod(execution)}`).
- It routes on that header value to the correct legacy bean or method (e.g. a Spring bean lookup by name, or an explicit mapping table).

Cross-check for this shape: exactly one worker subscribes to the shared job type. Its routing covers every distinct original expression in the findings rows for that job type. List uncovered expressions for the user.

Record the detected shape (1:1 vs many-to-one, per job type) in MIGRATION_REPORT.md.

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

- 1:1 job-type match confirmed, dispatcher covering every original expression, or every invoked method covered by a remediation: **no action** (the category is fully covered).
- Mismatched job types, uncovered original expressions, or uncovered invoked methods: **needs fix**, which become AI follow-up work items.
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

Where the selected code approach can mutate application code, ask via AskUserQuestion whether to
wire deployment of converted files in application code. If the selected code approach is assessment
only, then do not ask this question and preserve existing deployment wiring.

- **Yes, add/update deployment for converted files**:
  - Branch on the deployment mechanism, not the application framework.
  - Where deployment uses Spring Boot `@Deployment`, use `@Deployment(resources = ...)`.
  - Where deployment uses an explicit `CamundaClient` command, use that command's resource source.
  - Apply the inventory, coverage, and co-location rules to the explicit resource list.

    Use this decision table before editing deployment declarations:

    | Deployment mechanism | Deployment inventory | Resource authority | Required action |
    |---|---|---|---|
    | Spring Boot `@Deployment` | Non-empty | The selected build's application-artifact resource mapping | Resolve packaged classpath paths, then apply the Spring Boot pattern and coverage rules. |
    | Spring Boot `@Deployment` | Empty | Existing deployment declaration | Preserve existing deployment wiring. |
    | Explicit `CamundaClient` command | Non-empty | The source used by the explicit deployment command | Supply each inventory entry explicitly, then apply the command coverage and co-location rules. |
    | Explicit `CamundaClient` command | Empty | Existing deployment command | Preserve existing deployment wiring. |
    | No deployment mechanism | Non-empty | A user-selected deployment mechanism | Create Spring Boot `@Deployment` or an explicit `CamundaClient` command, then apply its coverage rules. |

    Apply only the row that matches the application and inventory.
  - If the inventory is non-empty and no deployment mechanism exists, ask the user to choose a
    mechanism before applying the decision table.
  - Where deployment uses Spring Boot `@Deployment`, treat every resource directory that the
    selected build's effective resource mapping includes in its application artifact as a packaged
    resource directory.
  - Where deployment uses Spring Boot `@Deployment`, treat `src/main/resources` as packaged only
    when it exists and the selected build's effective resource mapping retains its default
    inclusion.
  - Where deployment uses an explicit `CamundaClient` command, validate each inventory entry
    against the source used by the explicit deployment command instead of requiring a packaged
    classpath resource.
  - Where the selected model approach is M1 or E1, record BPMN and DMN converted copy paths from `Created ...` lines.
  - Where the selected model approach is M2, record each converted copy path after writing the converted copy.
  - Where the selected model approach is M3, record each validated hosted converted copy path after
    pairing it with its original.
  - Filter the recorded converted paths to BPMN and DMN files before building the model deployment inventory.
  - Exclude findings reports and other non-deployable artifacts from the deployment inventory.
  - Add every form with a recorded `bindingType=deployment` and a terminal status of `accepted` or `relinked` to the deployment inventory.
  - Where deployment uses Spring Boot `@Deployment`, record each deployment-bound form's final
    project-relative path in the deployment inventory.
  - Where deployment uses an explicit `CamundaClient` command, record each deployment-bound form's
    actual command source reference in the deployment inventory.
  - Record each deployment-bound form's owning converted BPMN path.
  - Record every existing deployment pattern in `MIGRATION_REPORT.md` before editing.
  - Store one current report row per pattern with its owning declaration, exact pattern, source
    path, and `migration-managed` marker.
  - Preserve a prior `migration-managed` marker for an existing pattern.
  - Before applying the preservation rule to a pattern without a prior marker, inspect
    `MIGRATION_REPORT.md` and migration history for legacy skill ownership.
  - If a pattern matches a legacy skill-generated pattern, ask the user to confirm its ownership.
    Record `migration-managed=true` only after confirmation.
  - If legacy ownership is not confirmed, record `migration-managed=false` and ask the user whether
    to remove or replace the pattern before reporting success.
  - Never edit an existing pattern recorded as `migration-managed=false`. Add a distinct pattern
    with `migration-managed=true` when migration wiring needs another match.
  - When updating a pattern recorded as `migration-managed=true`, keep the marker true and replace
    its current report row instead of creating conflicting current rows.
  - Record each added pattern with `migration-managed=true`.
  - Before a later run applies the preservation rule, reload the current pattern rows and markers
    from `MIGRATION_REPORT.md`.
  - Where deployment uses Spring Boot `@Deployment`, confirm that each inventory entry is
    under a packaged resource directory before adding its deployment pattern.
  - Where deployment uses Spring Boot `@Deployment`, prefer packaging the existing directory
    for each inventory entry when it contains only inventory entries. (SHOULD)
  - If deployment uses Spring Boot `@Deployment` and an inventory entry is outside a packaged
    resource directory, then copy only the recorded converted file and associated deployment-bound
    forms to a dedicated packaged resource directory, or configure precise build includes for those
    files.
  - If two recorded resources share a basename, preserve source subdirectories or choose distinct
    collision-safe destination filenames.
  - Never overwrite a destination resource during this copy.
  - Keep each original source model unchanged in its original location.
  - Update every recorded path after copying a resource.
  - Where deployment uses Spring Boot `@Deployment`, add a deployment pattern only when its
    resource type has an inventory entry under a packaged resource directory.
  - Where deployment uses Spring Boot `@Deployment`, add or update a migration-managed
    deployment pattern only when its packaged-classpath matches are limited to inventory entries.
  - Where deployment uses Spring Boot `@Deployment` and the inventory is non-empty, confirm that
    every migration-managed deployment pattern matches at least one inventory entry.
  - Where deployment uses Spring Boot `@Deployment`, normalize each recorded project-relative
    path to `/` separators.
  - Where deployment uses Spring Boot `@Deployment`, resolve the packaged classpath-relative
    path from the selected build's application-artifact resource mapping.
  - Where deployment uses Spring Boot `@Deployment` and the mapping strips a source
    resource-directory prefix, such as `src/main/resources/`, remove that prefix before deriving a
    classpath pattern.
  - Where deployment uses Spring Boot `@Deployment` and the mapping adds a target prefix,
    retain that prefix before deriving a classpath pattern.
  - Where deployment uses Spring Boot `@Deployment`, derive each deployment pattern from the
    normalized converted paths or the selected `--prefix`.
  - Where deployment uses Spring Boot `@Deployment`, derive each model pattern from its
    recorded filename suffix.
  - Where deployment uses Spring Boot `@Deployment` and the recorded paths use the default
    `converted-c8-` prefix and the `.bpmn` suffix, use
    `classpath*:**/converted-c8-*.bpmn` only after scanning every runtime classpath root, including
    dependency JARs, confirming that every match is a recorded inventory entry, and confirming
    that the resolver matches root-level resources in the packaged layout.
  - Prefer explicit resolved paths or an application-specific classpath path when any dependency
    match exists, root-level resolver behavior is unverified, or the complete runtime classpath
    cannot be verified. (SHOULD)
  - Where deployment uses Spring Boot `@Deployment` and the recorded paths use full suffixes
    such as `.bpmn20.xml` or `.dmn11.xml`, include those suffixes in the patterns.
  - Where deployment uses Spring Boot `@Deployment` and a packaged converted DMN file is
    recorded, add a DMN pattern.
  - Where deployment uses Spring Boot `@Deployment`, derive each form pattern from its
    normalized recorded deployment-bound form path, including relinked forms.
  - Where deployment uses Spring Boot `@Deployment` and a form with a recorded
    `bindingType=deployment` is packaged, add the `.form` pattern.
  - If the Spring Boot deployment inventory is non-empty, then migration-managed patterns must not
    target original diagrams, draft forms, blocked forms, declined forms, or resource types with no migration
    inventory entry.
  - If the Spring Boot deployment inventory is empty, then preserve existing deployment wiring.
  - Preserve pre-existing deployment entries that are not migration-managed.
- **No, I will handle deployment outside app startup** - leave code unchanged and record this decision in MIGRATION_REPORT.md.

## Deployment Validation

Validate the current deployment wiring before reporting success. Run liveness checks for existing
wiring for every deployment option. Require application-startup coverage only when the user chooses
to update application deployment wiring.

- Where deployment uses Spring Boot `@Deployment`, confirm that every effective deployment pattern
  matches at least one packaged classpath resource.
- Where the user chose **Yes, add/update deployment for converted files** and deployment uses
  Spring Boot `@Deployment`, confirm that every inventory entry matches at least one effective
  deployment pattern, including preserved patterns.
- Where the user chose **Yes, add/update deployment for converted files** and deployment uses an
  explicit `CamundaClient` command, confirm that every inventory entry is supplied by that command.
- Where the user chose **Yes, add/update deployment for converted files**, validate that each
  deployment-bound form and its owning converted BPMN share the same deployment declaration or
  invocation.
- Where the user chose **No**, the selected code approach is assessment only, or code migration is
  out of scope, record each inventory entry's external deployment source and coverage in
  `MIGRATION_REPORT.md` instead of requiring application-startup coverage. Record the intended
  co-location of each deployment-bound form and its owning converted BPMN.
- If a preserved `migration-managed=false` pattern matches an original diagram, draft form,
  blocked form, declined form, or resource type without a migration inventory entry, stop and ask
  the user to remove, narrow, or explicitly retain the pattern. Record the decision in
  `MIGRATION_REPORT.md` before reporting success.

## Report Keeping

Keep both inventories and both sets of results in `MIGRATION_REPORT.md` in the confirmed project root.
