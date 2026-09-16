# Code Transform Checklist

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

This checklist defines every code transformation item. Approach A runs OpenRewrite first (it covers items 3, 4, and partially item 2), then uses this checklist for the rest. Approach B works the full checklist by hand.

Confirm each item before the next. Ask the user before each commit.

---

## OpenRewrite output: de-recipe cleanup

Approach A runs this section after OpenRewrite. The cleanup removes recipe artifacts while preserving
the worker's job type, inputs, outputs, and behavior. Load
`30-glue-code/idiomatic-job-worker-cleanup.md` from the pattern catalog before editing.

Inspect every generated `@JobWorker` method. Apply each matching rule:

| Recipe artifact | Cleanup |
|---|---|
| A method name ends in `Migrated` or starts with `executeJob` | Rename the method to the worker's job type or the original delegate's intent. Preserve the explicit `@JobWorker(type = "...")` value. If the job type came from the method name, set it explicitly before renaming. |
| The method reads one or more variables through `ActivatedJob` | Replace `job.getVariable(...)` or `job.getVariablesAsMap()` with typed `@Variable` parameters. Use `@VariablesAsType` for a cohesive variable object. Keep `ActivatedJob` only when the method uses its metadata or the job key (`job.getKey()`). |
| The method has `throws Exception` after variable cleanup | Remove the declaration when the method no longer throws a checked exception. Preserve a specific checked exception when the worker still requires it. |
| The method returns one output through a mutable map | Return `Map.of(...)` when the output has non-null values and callers do not mutate the map. Keep a mutable map when the worker needs mutation or supports nullable values. |
| A `@JobWorker` annotation contains `autoComplete = true` | Remove the attribute because `true` is the default. Keep it only when the project documents the explicit setting as part of its configuration contract. |
| An input can be absent | Mark the matching input `@Variable(optional = true)` and use a nullable or optional-compatible Java type. Do not mark required inputs optional. |
| The source was a Camunda 7 delegate or external task worker | Preserve a short migration Javadoc. Add one when the generated method has no provenance note and the source origin is known. |

Do not change a job type, variable name, output name, exception behavior, or worker completion mode
during this cleanup. If the worker needs `ActivatedJob` for completion, failure, BPMN error, retry, or
metadata, keep that parameter and clean only the unused recipe artifacts.

### Before and after

The following recipe output uses the job object for variable access, retains a generated method name,
and builds a redundant result map:

```java
@JobWorker(type = "sampleJavaDelegate", autoComplete = true)
public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
  Map<String, Object> resultMap = new HashMap<>();
  Object x = job.getVariable("x");
  System.out.println("SampleJavaDelegate " + x);
  resultMap.put("y", "hello world");
  return resultMap;
}
```

The cleanup produces an idiomatic worker without changing the job type or variables:

```java
/**
 * Migrated from the Camunda 7 SampleJavaDelegate.
 */
@JobWorker(type = "sampleJavaDelegate")
public Map<String, Object> sampleJavaDelegate(@Variable Object x) {
  System.out.println("SampleJavaDelegate " + x);
  return Map.of("y", "hello world");
}
```

When an input is optional, retain that semantic explicitly:

```java
@JobWorker(type = "sampleJavaDelegate")
public void sampleJavaDelegate(@Variable(optional = true) String comment) {
  // worker logic
}
```

---

## 1. Dependencies and Configuration

Catalog: `10-general/dependencies.md`. It owns the GA version resolution from Maven Central metadata,
the starter choice by Spring Boot version, the `httpclient5` override, the SLF4J binding, the
`@PostConstruct` to `@EventListener(CamundaPostDeploymentEvent.class)` move. Read it.
The `@EnableProcessApplication` replacement is documented in
`20-client-code/10-process-engine/handle-resources.md`.
Never restate a version number from memory.

These items are not in the catalog:

- If the project already pins a released GA Camunda 8 version in the selected target minor, keep it
  unless the user explicitly opts into a patch upgrade.
- Check Spring Boot compatibility from the selected starter or BOM POM on Maven Central. Do not
  assume a pairing works because both versions are "latest".
- Ensure Spring Boot dependency management is set through a parent or BOM before adding a Camunda
  starter.
- Keep the dependency footprint. Never add a dependency the C7 app did not need, for example
  `spring-boot-starter-web` when it exposed no REST endpoints. This includes a dependency added
  transitively via a starter choice.
- Remove dependencies with groupId `org.camunda.bpm` or a groupId that starts with `org.camunda.bpm.`. Remove `camunda-bom` and the embedded-engine deps (H2, JDBC starter).
- If tests exist, add `io.camunda:camunda-process-test-spring` (test scope).
- Add the Camunda public repository only when the selected artifact or version is not on Maven
  Central:
  - Maven: `<repository><id>camunda-public</id><url>https://artifacts.camunda.com/artifactory/public/</url></repository>`
  - Gradle: `maven { url "https://artifacts.camunda.com/artifactory/public/" }`
- Replace `camunda.*` keys with `camunda.client.*` in application.properties or .yaml.

---

## 2. Client Code (ProcessEngine to CamundaClient)

Catalog: `20-client-code/10-process-engine/`. One file per mapping: `starting-process-instances`,
`business-key-and-tags`, `correlate-messages`, `broadcast-signals`, `cancel-process-instance`,
`handle-user-tasks`, `handle-process-variables`, `handle-files-and-documents`, `query-history`,
`evaluate-decisions`, `batch-operations`, `raise-incidents`, `handle-resources`,
`search-process-definitions`, `adjusting-the-java-class`. Fetch the ones the inventory needs.

These items are not in the catalog:

- Replace direct service injection (RuntimeService, TaskService, HistoryService, DecisionService,
  ManagementService) with CamundaClient.
- When a C7 API has a C8 counterpart, use the matching CamundaClient API instead of new process
  variables carrying what the API can already return.
- Flag the business-key semantic difference in `MIGRATION_REPORT.md` when the migrated process
  mutates the key.
- Preserve startup behavior exactly: what starts, when it starts, and how many instances.

### Mandatory open items for migrated queries

A C7 `RuntimeService`, `HistoryService`, `TaskService`, `RepositoryService`, or `DecisionService`
query becomes a C8 search request (`newProcessInstanceSearchRequest`, `newElementInstanceSearchRequest`,
`newVariableSearchRequest`, `newUserTaskSearchRequest`, `newIncidentSearchRequest`,
`newUserTaskVariableSearchRequest`, `newDecisionInstanceSearchRequest`,
`newProcessDefinitionSearchRequest`). A C8 search request reads secondary storage, so its result is
eventually consistent. See
`20-client-code/10-process-engine/query-history.md`.

For every migrated query, the skill records an open item in the `MIGRATION_REPORT.md` open-items
section. This is mandatory and never depends on the running model. When a trigger below matches,
record its wording. Replace `<call site>` with the class and the method.

| Trigger | Open item to record |
|---|---|
| A C7 query becomes a C8 search request | `<call site>` now reads secondary storage through the C8 search API. The result is eventually consistent, so an instance changed moments earlier can be missing. Confirm the surrounding logic tolerates an eventually-consistent result. |
| The result drives a business decision, such as a count, a guard, or a branch | `<call site>` makes a business decision from an eventually-consistent search result. Confirm the decision still holds when the result lags. |
| The C7 code read its own recent write inside a worker (read-after-write) | `<call site>` relied on a C7 transaction boundary for read-after-write. The C8 search is asynchronous. Confirm the logic does not depend on immediate visibility. |
| The C7 project relied on `historyTimeToLive` for data availability or cleanup | `<call site>` relied on `historyTimeToLive`. Camunda 8 controls retention on the cluster, not per query. Confirm the cluster retention matches the old expectation. |

Set each open item to status `open`. Resolve it only on an explicit user decision, and record that
decision in `MIGRATION_REPORT.md`.

### Query counts and pagination

Catalog: `20-client-code/10-process-engine/count-query-results.md`.

- If the code needs the complete query count, then replace `list().size()`,
  `list().stream().count()`, and `count()` with `page().totalItems()`.
- If the original result type is `int` or `Integer`, then append `.intValue()` to
  `page().totalItems()`.
- Trace search results assigned to variables before checking later `.size()` or `.stream().count()`
  uses.
- Never use `items().size()` or `items().stream().count()` for a complete query count.
- If the search can exceed cluster result limits, then review `page().hasMoreTotalItems()`.

---

## 3. JavaDelegate to Job Worker (OpenRewrite covers this)

Catalog: `30-glue-code/10-java-spring-delegate/` (`adjusting-the-java-class`,
`handling-process-variables`, `handling-a-bpmn-error`, `handling-a-failure`, `handling-an-incident`)
and `30-glue-code/outbound-http-rest-connector.md`.

These items are not in the catalog:

- Keep worker behavior unchanged. A migrated worker keeps the same inputs and outputs. Never add a new
  feature to an existing worker during migration. New logic belongs in a new, separate worker.

---

## 4. External Task Workers (OpenRewrite covers this)

Catalog: `30-glue-code/20-java-spring-external-task-worker/`, with the same five files as item 3.

---

## 5. Listeners (NOT covered by OpenRewrite)

Catalog: `30-glue-code/30-java-spring-listeners/listeners.md`. It owns the ExecutionListener,
TaskListener, and global user task listener mappings with their version requirements.

The catalog covers listener mappings, including the multi-instance collection limitation.

---

## 6. Test Code (NOT fully covered by OpenRewrite)

Catalog: `40-test-assertions/10-assertions/` (`10-complete-test-case`, `20-process-instance`,
`30-process-variable`, `40-user-task`, `50-message`, `60-job`).

These items are not in the catalog:

- Add per-worker overrides when mocked workers need exceptions.
- When a large suite on 8.9+ uses one runtime configuration, use CPT shared-runtime mode.

---

## 7. JUEL Expressions (NOT covered by OpenRewrite)

- Pure data expressions become FEEL (the converter automates this model-side in Part B).
- Conditional events are native since 8.9.
- A method-invoking expression (on a bean or a plain variable) is the named category **FEEL method-invocation**, handled below.

### Named category: Script expressions (Groovy, JavaScript, ...)

A script-language expression (sequence-flow condition with `language="groovy"`, script task, `camunda:script` in a listener) cannot run in C8, because FEEL cannot execute scripts. Treat it as ONE category per script language. Default remediation, with the same decision weighting as FEEL method-invocation: move the script logic into a preceding service task whose `@JobWorker` computes the outcome into a plain variable, then replace the expression with a FEEL reference. A script task itself becomes a service task with a worker holding the script logic.

### Named category: FEEL method-invocation

Every expression that invokes a Java method (e.g. `${order.getTotal()}`, `${pricingService.quote(customer)}`) fails for one root cause. FEEL cannot call Java methods, whether the receiver is a Spring bean or a plain variable (e.g. `${objectVar.getAddress().getStreet()}`, `${execution.getVariable("a").size()}`). Treat all occurrences as ONE countable category — **FEEL method-invocation** — regardless of where they appear: sequence-flow/gateway condition expressions, multi-instance `collection` or completion conditions, callActivity `calledElement`, timer expressions, input/output parameters, or job/user-task attributes (assignee, dueDate, priority, ...).

Count occurrences for sizing, but decide remediation ONCE per category (or per coherent sub-group sharing one receiver expression), never per row.

**Decision process. Present all options to the user and let them choose:**

1. **Precompute via job worker** (default): compare the bean's fully qualified class name with the
   original Java source baseline, recorded as fully qualified class names. Create a new thin
   `*Worker` adapter component when the class is present in that baseline. Never add `@JobWorker` to
   an existing domain or service class from the C7 source, including a Spring `@Component` or
   `@Service`. Keep the domain logic in the existing bean and delegate to it from the adapter. Use
   this shape for a Spring bean method:

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

   Treat the baseline comparison as authoritative. A class remains an invalid worker target even
   when its name ends with `Worker`. Only a class absent from the baseline can be the new adapter.

   Add a preceding service task whose `@JobWorker` calls the method (or runs the equivalent logic) and stores the result in a plain process variable. Then replace the expression with a FEEL reference to that variable (e.g. `=total`). For multi-instance `collection` this is the required shape, because the collection must exist as a variable before the multi-instance body starts.
2. **Compute via execution listener** (most elegant when no extra visible shape in the diagram is desired): attach a `zeebe:executionListener` (8.6+) backed by a `@JobWorker` that computes the value into a variable, e.g. on the `end` event of the preceding element or the `start` event of the element carrying the expression. Caveats: the listener must run BEFORE the expression is evaluated. For multi-instance `collection` it must sit on a preceding element, never the MI body itself (the collection is read at activation). Listeners are jobs too, so a failure creates an incident on the element. The precompute step becomes invisible in the diagram, so document it.
3. **Refactor into DMN** (when the expression encodes a business rule/decision, typical for gateway conditions): move the logic into a DMN table in a preceding business rule task and read its output variable.
4. **JUEL job worker** (exceptional, only when the expression must stay dynamic): keep the JUEL string in a task header and evaluate it inside a generic worker. ⚠️ This is dynamic expression evaluation: only ever evaluate trusted, model-controlled expressions (never user input), and constrain the evaluation context (e.g. a bean allow-list) to avoid code injection.

This category is out of scope for auto-generation. Detect, count, and name it. The human decides the approach per category.

---

## 8. Generated Task Form Dependencies (NOT covered by OpenRewrite)

When the model inventory finds `camunda:formData`, `camunda:formField`, or `camunda:formProperty`, inspect the code that supplied or consumed their runtime behavior:

- `FormFieldValidator` implementations and named validator beans or classes need a new backend or application validation design. form-js validation is not server-side enforcement.
- `FormService`, `TaskFormData`, `StartFormData`, `FormField`, and `FormProperty` consumers may depend on C7 metadata that no longer exists at runtime.
- `submitTaskForm`/`submitStartForm` and REST form-submission clients may depend on field ids, aliases, type conversion, business-key extraction, or validation exceptions.
- Redesign code that reads custom form-field properties, even when the metadata is copied to the C8 form component.
- Reconcile code that expects C7 `Date` or full-range Java `long` values with the C8 form output.

Do not delete or rewrite these consumers from form structure alone. Cross-check each against the user-approved decisions from `form-migration.md`, then implement only the agreed worker, listener, API, input/output mapping, or application validation.

---

## Detection Hints for Assessment

Use these to classify files during assessment:

| Pattern | Type |
|---------|------|
| `implements JavaDelegate` | JavaDelegate |
| `@ExternalTaskSubscription` or `ExternalTaskHandler` | External task worker |
| `implements ExecutionListener` or `implements TaskListener` | Listener |
| `ProcessEngine`, `RuntimeService`, `TaskService` autowired | Client code |
| `HistoryService` | Client code (maps to search endpoints) |
| `DecisionService` | Client code (maps to newEvaluateDecisionCommand) |
| `IdentityService`, `FormService` | Client code (flag for manual design) |
| `FormFieldValidator` or `camunda:constraint name="validator"` | Generated-form backend validation (manual design) |
| `TaskFormData`, `StartFormData`, `FormField`, `FormProperty` | Generated-form metadata consumer |
| `submitTaskForm`, `submitStartForm`, `/submit-form`, `/form-variables` | Generated-form submission client |
| `businessKey` usage | Flag: maps to Business ID (8.9+) or tags (8.8). See pattern catalog. Keep a mutable key as a process variable |
| `FileValue` / `Variables.fileValue(...)` | Flag: maps to Document API (see pattern catalog) |
| Groovy/JavaScript in `conditionExpression`, script tasks, `camunda:script` | Script expression (maps to preceding job worker) |
| `camunda:connector` / http-connector, HTTP client code in delegates | Flag: maps to out-of-the-box REST connector (see pattern catalog) |
| Batch operations (`...Async`, ManagementService batches) | Client code |
| `ZeebeClient` / Spring Zeebe SDK | Legacy C8 client (migrate to CamundaClient) |
| `@Test` + Camunda 7 test rules | Test code |
| `application.properties`/`.yaml` with `camunda.*` keys | Config |
| `ProcessEnginePlugin`, BPMN parse listeners | Flag: global behavior |

## Special Blockers

Flag these explicitly:

- A listener or delegate attached to a multi-instance body that computes the collection variable. Sequencing does not exist in C8, so this requires a model change with a preceding service task. High complexity.
- Custom batch handlers (`ManagementService#createBatch` with custom jobs): no generic C8 equivalent.
- Generated-form custom validators, business-key fields, writable/readable form properties, expression-backed form properties, and date-pattern/type assumptions: these require the decisions and code cross-check in `form-migration.md`.
