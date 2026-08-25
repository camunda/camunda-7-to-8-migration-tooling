# Code Transform Checklist

This checklist defines all code transformation items. Approach A runs OpenRewrite first (covering items 3, 4, and partially 2), then uses this checklist for the rest. Approach B works the full checklist manually.

Confirm each item before the next (commit policy: ask user before committing).

---

## 1. Dependencies and Configuration

- Resolve the latest released GA Camunda version from Maven Central artifact metadata: query `https://repo.maven.apache.org/maven2/io/camunda/<artifact-id>/maven-metadata.xml`. From versions, choose the highest version matching the target Camunda minor (8.8.x, 8.9.x, etc.) and exclude SNAPSHOT, alpha, beta, and rc versions. If no GA version exists for the target, ask before using a pre-release.
- Pick the starter by Spring Boot version: 3.x uses `io.camunda:camunda-spring-boot-3-starter`; 4.x uses `io.camunda:camunda-spring-boot-starter`.
- Add the Camunda public repository only if the selected artifact/version is not available on Maven Central:
  - Maven: `<repository><id>camunda-public</id><url>https://artifacts.camunda.com/artifactory/public/</url></repository>`
  - Gradle: `maven { url "https://artifacts.camunda.com/artifactory/public/" }`
- Remove `org.camunda.bpm.*`, `camunda-bom`, and embedded-engine deps (H2, JDBC starter).
- Add the starter; add `io.camunda:camunda-process-test-spring` (test scope) if tests exist.
- For Spring Boot 3.5.x with Camunda 8.9.13, check resolved `org.apache.httpcomponents.client5:httpclient5` version. If Spring Boot BOM selects 5.5.2, override to 5.6.1 or later.
- If removing Camunda 7 webapp/rest starters also removes your only SLF4J binding, add `org.springframework.boot:spring-boot-starter-logging`.
- Ensure Spring Boot dependency management is set (parent or BOM). If `@PostConstruct` is only used to start process instances, migrate that flow to `@EventListener(CamundaPostDeploymentEvent.class)` first.
- Replace `@EnableProcessApplication` with `@Deployment`. If the C7 app relied on implicit classpath auto-deployment, still add explicit `@Deployment(resources = ...)`.
- Replace `camunda.*` keys with `camunda.client.*` in application.properties/.yaml.

---

## 2. Client Code (ProcessEngine to CamundaClient)

- Replace ProcessEngine/service autowiring (RuntimeService, TaskService, HistoryService, DecisionService, ManagementService) with CamundaClient.
- Map: start instances (incl. businessId/tags), message correlation, signal broadcast, cancel, user tasks, variables, HistoryService to search requests, DecisionService to newEvaluateDecisionCommand, batch ...Async to batch operations (8.8+).
- If migrated code starts instances from @PostConstruct while using @Deployment, move startup logic to `@EventListener(CamundaPostDeploymentEvent.class)`.

---

## 3. JavaDelegate to Job Worker (OpenRewrite covers this)

- Remove `implements JavaDelegate`; convert `execute(DelegateExecution)` to a `@JobWorker` method.
- Variable access becomes method params / @Variable.
- BpmnError becomes `CamundaError.bpmnError(...)`.
- Remove TypedValue usage.

---

## 4. External Task Workers (OpenRewrite covers this)

- `@ExternalTaskSubscription` becomes `@JobWorker`.
- Update variable access and failure/incident handling.

---

## 5. Listeners (NOT covered by OpenRewrite)

- ExecutionListener becomes execution listener job workers (`zeebe:executionListener` + `@JobWorker`).
- TaskListener becomes user task listener job workers (job result with corrections/deny).
- Globally registered listeners (engine plugins, parse listeners) on user tasks become global user task listeners (8.9+).
- Flag multi-instance body listeners that prepare collections (requires a model change).

---

## 6. Test Code (NOT fully covered by OpenRewrite)

- `@Rule` Camunda test rules become `@CamundaSpringProcessTest`.
- Update assertions: `isWaitingAt("id")` becomes `hasActiveElements("id")`.
- Update message correlation, timers, and user task completion using processTestContext (completeUserTask, completeJob, mockJobWorker, increaseTime).
- Disable real workers where mocked: `camunda.client.worker.defaults.enabled=false` with per-worker overrides.
- On 8.9+, use CPT shared-runtime mode for large suites.

---

## 7. JUEL Expressions (NOT covered by OpenRewrite)

- Pure data expressions become FEEL (the converter automates this model-side in Part B).
- Conditional events are native since 8.9.
- Bean-invoking expressions are the named category **FEEL method-invocation**, handled below.

### Named category: FEEL method-invocation

Every expression that invokes a Java bean method (e.g. `${order.getTotal()}`, `${pricingService.quote(customer)}`) fails for the same root cause: FEEL cannot call Java methods. Treat all occurrences as ONE countable category — **FEEL method-invocation** — regardless of where they appear: sequence-flow/gateway condition expressions, multi-instance `collection` or completion conditions, callActivity `calledElement`, timer expressions, input/output parameters, or job/user-task attributes (assignee, dueDate, priority, ...).

Count occurrences for sizing, but make the remediation decision ONCE per category (or per coherent sub-group sharing one bean) — not per row.

**Decision process:**

1. **Precompute via job worker** (default): add a preceding service task whose `@JobWorker` calls the bean method and stores the result in a plain process variable; replace the expression with a FEEL reference to that variable (e.g. `=total`). For multi-instance `collection`, this is the required shape (the collection must exist as a variable before the multi-instance body starts).
2. **Refactor into DMN** (when the expression encodes a business rule/decision, typical for gateway conditions): move the logic into a DMN table in a preceding business rule task and read its output variable.
3. **JUEL job worker** (exceptional, only when the expression must stay dynamic): keep the JUEL string in a task header and evaluate it inside a generic worker. ⚠️ This is dynamic expression evaluation — only ever evaluate trusted, model-controlled expressions (never user input), and constrain the evaluation context (e.g. bean allow-list) to avoid code injection.

This category is out of scope for auto-generation: detect, count, and name it — the human decides the approach per category.

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
| `businessKey` usage | Flag: maps to Business ID (8.9+) or tags (8.8) |
| Batch operations (`...Async`, ManagementService batches) | Client code |
| `ZeebeClient` / Spring Zeebe SDK | Legacy C8 client (migrate to CamundaClient) |
| `@Test` + Camunda 7 test rules | Test code |
| `application.properties`/`.yaml` with `camunda.*` keys | Config |
| `ProcessEnginePlugin`, BPMN parse listeners | Flag: global behavior |

## Special Blockers

Flag these explicitly:
- Listeners or delegates attached to multi-instance bodies that compute the collection variable (sequencing does not exist in C8; requires model change with preceding service task). High complexity.
- Custom batch handlers (`ManagementService#createBatch` with custom jobs) - no generic C8 equivalent.
