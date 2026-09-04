# Code Transform Checklist

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

This checklist defines every code transformation item. Approach A runs OpenRewrite first (it covers items 3, 4, and partially item 2), then uses this checklist for the rest. Approach B works the full checklist by hand.

Confirm each item before the next. Ask the user before each commit.

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

1. **Precompute via job worker** (default): add a preceding service task whose `@JobWorker` calls the method (or runs the equivalent logic) and stores the result in a plain process variable. Then replace the expression with a FEEL reference to that variable (e.g. `=total`). For multi-instance `collection` this is the required shape, because the collection must exist as a variable before the multi-instance body starts.
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
