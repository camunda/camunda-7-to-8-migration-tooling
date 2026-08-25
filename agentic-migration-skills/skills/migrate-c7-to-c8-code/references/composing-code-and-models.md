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

## Deployment Wiring

After both complete, ask whether to wire deployment of converted files in application code via AskUserQuestion:

- **Yes, add/update @Deployment for converted files** (recommended when code scope includes a Spring Boot app) - add or update `@Deployment(resources = ...)` so it targets only converted resources with explicit recursive classpath patterns. Example: `@Deployment(resources = {"classpath*:**/converted-c8-*.bpmn", "classpath*:**/converted-c8-*.dmn"})`. Never target original diagrams.
- **No, I will handle deployment outside app startup** - leave code unchanged and record this decision in MIGRATION_REPORT.md.

## Report Keeping

Keep both inventories and both sets of results in `MIGRATION_REPORT.md` in the confirmed project root.
