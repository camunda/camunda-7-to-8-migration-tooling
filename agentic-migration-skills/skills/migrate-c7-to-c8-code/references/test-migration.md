# Camunda 7 Test Inventory

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD), and an option is marked (MAY).

## Scope rule

A test is eligible for migration only when it runs a BPMN process or DMN decision on a Camunda 7 engine.
The test must also use a framework or approach that existed for Camunda 7.
A dependency alone never makes a test eligible for migration.
For example, `camunda-platform-7-mockito` provides engine-backed helpers and `DelegateExecutionFake` for plain unit tests.

The skill inventories every test method, including tests that are out of scope.
The skill assigns one test kind to each test method.
When methods in one class differ, assign the test kind per method.

CMMN tests and tests that use unsupported Camunda engine internals do not meet this scope rule.
`ClockUtil` used to control timers is a supported test utility.
The skill does not assign `manual redesign` based on `ClockUtil` alone.
The skill inventories CMMN tests and tests that use unsupported engine internals as `manual redesign` with `Report only` handling.

## Test kinds

| Test kind | Detect by | Handling |
|---|---|---|
| process test | `ProcessEngineRule` (JUnit 4), `ProcessEngineExtension` (JUnit 5, including `org.camunda.bpm.extension:camunda-bpm-junit5`), `ProcessEngineTestCase` (JUnit 3), `@org.camunda.bpm.engine.test.Deployment`, `BpmnAwareTests` or `ProcessEngineTests` from camunda-bpm-assert, `AbstractProcessEngineRuleTest`, `StandaloneInMemoryTestConfiguration`, or an embedded engine used by a `@SpringBootTest` | Migrate |
| decision test | `DmnEngineRule`, `DmnEngine`, `DmnEngineConfiguration`, or `DecisionService` used by a test | Migrate |
| scenario test | `org.camunda.bpm.scenario.*` from camunda-platform-scenario | Migrate (lower priority) |
| remote-engine test | A test runs a BPMN process or DMN decision on a running Camunda 7 engine through Engine REST (`/engine-rest`), `org.camunda.bpm.client.*`, or a Camunda 7 Testcontainers image | Migrate (lower priority) |
| manual migration | JGiven (`io.holunda.testing:camunda-bpm-jgiven`) or Cucumber scenarios use Camunda 7 APIs to run an engine-backed BPMN process or DMN decision. The Cucumber classification includes applicable hooks, not only steps. An in-scope test uses Arquillian, camunda-bpm-needle (CDI), or the Camunda 7 Quarkus extension. A test runs an engine-backed process from a BPMN model built with the Camunda 7 fluent model API. A Kotlin or Groovy test uses Camunda 7 test APIs to run an engine-backed BPMN process or DMN decision. | Report only |
| manual redesign | CMMN APIs or models, such as `CmmnAwareTests` or `CaseService`, or unsupported engine internals, such as `ProcessEnginePlugin`, BPMN parse listeners, custom history levels, or `ProcessEngineConfigurationImpl` internals. `ClockUtil` timer control does not trigger this signal by itself. | Report only |
| out of scope | Tests that run no engine are out of scope. This includes Kotlin or Groovy tests that use Camunda 7 test APIs but run no process or decision. It also includes delegate, listener, external task worker, and service tests that run no engine. Examples include `DelegateExecutionFake`, `mock(DelegateExecution.class)`, and Mockito `RuntimeService` mocks. WireMock stubs of Engine REST and plain Java tests are also out of scope. Remote health or metadata probes that run no process or decision are also out of scope, unless the shared-engine exception in Scope confirmation applies. | Not part of test migration |
| out of scope (Camunda 8) | Zeebe Process Test (`io.camunda.zeebe.process.test.*`) or CPT (`io.camunda.process.test.*`) | Not part of test migration |

## Decision-test migration

When the target is Camunda 8.9 or later, the skill migrates every test with test kind `decision test`.
The skill keeps a decision test distinct from a process test.
A process test that checks a business rule task remains a process test.
Add `assertThatDecision(DecisionSelectors.byId("dish", processInstanceKey))` to a process test without changing its test kind. (MAY)
Decision mocks use the mock subtask's `mockDmnDecision` rules.

### Harness and evaluation mapping

| Camunda 7 | Camunda 8.9 with CPT | Notes |
|---|---|---|
| `@Rule DmnEngineRule` or `DmnEngine` built with `DmnEngineConfiguration` | `@CamundaProcessTest` with an injected `CamundaClient` | Camunda 7 evaluates DMN in process. CPT needs a Camunda 8 runtime. |
| A Spring test that injects `DecisionService` | `@SpringBootTest` with `@CamundaSpringProcessTest` and an injected `CamundaClient` | Keep the Spring context and use the CPT Spring artifact that matches the production starter. |
| `dmnEngine.parseDecision("dish", stream)`, `parseDecisions(stream)`, or `@Deployment(resources = "dish.dmn")` | `@TestDeployment(resources = "converted-c8-dish.dmn")` | Deploy the converted DMN copy. A DRD deploys as one resource. |
| `dmnEngine.evaluateDecisionTable(decision, vars)`, `evaluateDecision(decision, vars)`, `DecisionService.evaluateDecisionByKey("dish").variables(vars).evaluate()`, or `evaluateDecisionTableByKey("dish", vars)` | `client.newEvaluateDecisionCommand().decisionId("dish").variables(vars).send().join()` | The Camunda 8 command evaluates required decisions automatically. |
| `Variables.putValue("a", 1).putValue("b", "x")` | A `Map<String, Object>` | The skill keeps the same logical inputs. Camunda 8 serializes map values as JSON. For a Camunda 7 `Date` or typed value, the skill checks that the converted DMN reads its JSON representation as intended. The skill does not assume the Java type survives serialization. |
| `result.getSingleResult().getSingleEntry()` or `result.getSingleEntry()` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(value)` | Use for one output column and one result. |
| `result.getSingleResult().getEntry("a")` or `getEntryMap()` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(Map.of("a", value, "b", value))` | `hasOutput` compares all outputs. Parse `response.getDecisionOutput()` to check only selected outputs. |
| `result.collectEntries("x")` with hit policy `COLLECT` | The skill parses `response.getDecisionOutput()` as a list of scalar values for one output column, or a list of maps keyed by output name for multiple output columns. The skill selects values by output name and compares rows without relying on their order. | Camunda 8 returns `COLLECT` results in arbitrary order. |
| `result.collectEntries("x")` with hit policy `RULE ORDER` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(List.of(...))` | The order is defined by the hit policy. |
| `result.isEmpty()` or `getSingleResult()` returns `null` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).hasNoMatchedRules()` | |
| Matched-rule checks through `HistoricDecisionInstance` | `hasMatchedRules(int...)` or `hasNotMatchedRules(int...)` | `hasMatchedRules` passes when the expected rule numbers are a subset of the matched rules. |
| An expected `DmnEngineException`, including one wrapped by `DecisionService` in `ProcessEngineException` | The skill checks `response.getFailureMessage()` and `response.getFailedDecisionId()` | Camunda 8 returns a failed response instead of throwing. `isEvaluated()` fails for this response. |

The skill reads the hit policy and output columns from the converted DMN copy before choosing an assertion.
The skill uses the output names from that copy when it checks a map.
The skill preserves exact checks as exact and order-insensitive checks as order-insensitive.
If the converted decision uses an output shape not listed here, then the skill parses `response.getDecisionOutput()` and checks its actual JSON shape.
The skill does not guess an output shape from the Camunda 7 Java result type.
The skill never uses `hasOutput(List)` for a `COLLECT` decision.

### Nullable inputs and failures

The skill keeps every input key whose Camunda 7 value is `null`.
The skill builds nullable decision variables with a mutable map, such as `HashMap`.
The skill never uses `Map.of` for a decision variable map that contains `null`.
The skill distinguishes an explicit `null` input from an omitted input.
The skill uses a null-tolerant map or list for an expected output that contains `null`.
The skill never uses `Map.of` or `List.of` to build an expected output that contains `null`.
`Map.of` rejects null values. `List.of` rejects null elements.
If the target decision treats those inputs differently from Camunda 7, then the skill records the difference for review instead of dropping the input or adding a default.

When Camunda 7 throws an exception for a decision evaluation, the skill checks the returned Camunda 8 response.
The skill checks `getFailureMessage()` and `getFailedDecisionId()` for a failed Camunda 8 evaluation.
The skill does not expect a Camunda 8 evaluation failure to throw.

When a migrated decision test fails, the skill checks the converted DMN findings report.
The skill links the failure to a relevant finding in `MIGRATION_REPORT.md`.
The skill changes the DMN copy only through the normal model-editing rules.
The skill does not change the Diagram Converter.

### Build changes

| Dependency use | Camunda 7 artifact | Camunda 8 test artifact |
|---|---|---|
| Standalone DMN tests | Remove test-scoped `org.camunda.bpm.dmn:camunda-engine-dmn` and `org.camunda.bpm.dmn:camunda-engine-feel-*` artifacts when tests are their only users. | Add `io.camunda:camunda-process-test-java` in test scope. |
| Spring decision tests | Remove test-scoped `org.camunda.bpm.dmn` engine and FEEL artifacts when tests are their only users. | Use the CPT Spring artifact that matches the production Spring Boot starter. Use `camunda-process-test-spring` with Spring Boot 4 or `camunda-process-test-spring-boot-3` with the Spring Boot 3 starter. |
| Process engine used only by tests | Remove the test-scoped Camunda 7 engine when tests are its only users. | Use the CPT artifact for the selected test harness. |

The skill inventories each dependency before removal.
The skill keeps an artifact when production code still uses it.
The skill records a required redesign when a production dependency has no Camunda 8 equivalent.

Standalone Camunda 7 DMN tests run with an in-process engine.
The migrated CPT tests need a Camunda 8 runtime.
CPT uses Docker by default and supports a configured remote runtime.
When the skill asks Question 8, the skill follows its DMN runtime notice in `interview-questions.md`.

### Limitations

| Source behavior | Handling |
|---|---|
| Camunda 7 DMN engine plugins or custom function providers | Manual redesign |
| Camunda 7 decision table with hit policy `PRIORITY` | Manual redesign. Camunda 8.9 does not support `PRIORITY`. |
| Camunda 7 decision table with hit policy `OUTPUT ORDER` | Manual redesign. Camunda 8.9 does not support `OUTPUT ORDER`. |
| Decision-history assertions beyond matched rules and outputs | Report only |

## Scope confirmation

| Signal | Confirmation required |
|---|---|
| A test uses CMMN APIs or models, or unsupported Camunda engine internals | Keep the test as `manual redesign` and use `Report only` handling, even when it does not run a BPMN process or DMN decision |
| A test uses `ClockUtil` to control a timer | Treat `ClockUtil` as a supported test utility. Do not assign `manual redesign` based on `ClockUtil` alone. |
| `@Test`, `@ParameterizedTest`, `@RepeatedTest`, or a JUnit 3 test method | The method runs a BPMN process or DMN decision on a Camunda 7 engine |
| `@Deployment` | The method runs a process or decision. The annotation alone is not enough |
| `@SpringBootTest` | The embedded engine starts a process, completes a task, correlates a message, or handles an endpoint that does one |
| A Cucumber `Scenario` or `Scenario Outline` data row | Its step definitions or applicable hooks run a BPMN process or DMN decision on a Camunda 7 engine |
| A remote-engine test reads a shared engine URL from an environment variable and does not run a process or decision | Keep it as `remote-engine test` and use `Report only` handling. Record `shared environment` as the reason. |
| A remote test makes only health or metadata calls and does not run a process or decision, without the shared-engine exception | The skill classifies the test as out of scope. |
| A Camunda 7 dependency or a test class name | Not sufficient without an engine-backed process or decision |

When one test matches multiple test kinds, the skill assigns the first matching kind in this order:

| Order | Matching signal | Test kind |
|---|---|---|
| 1 | The test uses a Camunda 8 test API | out of scope (Camunda 8) |
| 2 | The test uses CMMN or unsupported engine internals. `ClockUtil` timer control does not trigger this signal by itself. | manual redesign |
| 3 | JGiven or Cucumber scenarios use Camunda 7 APIs to run an engine-backed BPMN process or DMN decision. The Cucumber classification includes applicable hooks, not only steps. An in-scope test uses Arquillian, camunda-bpm-needle, or the Camunda 7 Quarkus extension. The test runs an engine-backed process from a BPMN model built with the Camunda 7 fluent model API. A Kotlin or Groovy test uses Camunda 7 test APIs to run an engine-backed BPMN process or DMN decision. | manual migration |
| 4 | The test uses camunda-platform-scenario | scenario test |
| 5 | The test reads a shared Camunda 7 engine URL from an environment variable and runs no process or decision | remote-engine test |
| 6 | The test runs a BPMN process or DMN decision against a running Camunda 7 engine remotely | remote-engine test |
| 7 | The test directly evaluates a DMN decision | decision test |
| 8 | The test runs a BPMN process | process test |
| 9 | The test runs no engine-backed process or decision | out of scope |

## Modifiers

| Modifier | Detect by | Used by |
|---|---|---|
| mocks | `org.camunda.bpm.engine.test.mock.Mocks`, `MockExpressionManager`, `org.camunda.community.mockito.*`, `org.camunda.bpm.extension.mockito.*`, holunda `c7-mockito`, or Mockito mocks registered as Spring beans called by the process | Mock migration subtask |
| coverage | `org.camunda.community.process_test_coverage.*`, `org.camunda.bpm.extension.process_test_coverage.*`, or holunda `c7-process-test-coverage` | Baseline and parity subtask |
| time | `ClockUtil`, `ManagementService.executeJob(...)`, or job queries for timers | Engine test support subtask |
| Spring | `@SpringBootTest`, `SpringRunner`, `SpringExtension`, or Spring XML or Java contexts that wire the engine | Spring subtask |

Record each modifier in the test's `Signals` cell.
Keep modifiers separate from the test kind.

## Test sources and methods

The skill scans every configured test source directory in each module.
This includes `src/test/java`, `src/test/kotlin`, and `src/test/groovy` when present.
The skill also scans each additional test source set declared by the build, such as `src/it/java` or Gradle `integrationTest`.
The skill follows each Cucumber runner or build configuration to locate executed `.feature` files in test resources.
The skill inventories each Cucumber `Scenario` as one test.
The skill inventories each data row in a Cucumber `Scenario Outline` `Examples` table as a separate test.
The skill reads methods annotated with `@Given`, `@When`, `@Then`, `@And`, or `@But` as step definitions.
The skill reads constructor-registered lambda steps, such as `io.cucumber.java8.En`.
The skill does not inventory step-definition methods, lambda registrations, a Cucumber runner class, or hook methods as separate tests.
The skill follows both forms when it checks for Camunda 7 process or decision calls.
The skill reads applicable Cucumber hooks.
The skill uses them to check whether scenarios run BPMN processes or DMN decisions on a Camunda 7 engine.
The skill reads shared test bases, abstract test classes, test configuration classes, and `camunda.cfg.xml` under test resources.
When a shared base or configuration supplies an engine signal, apply it to each affected test method.
The skill includes methods annotated with `@Test`, `@ParameterizedTest`, or `@RepeatedTest`.
The skill also includes JUnit 3 `public void test...()` methods.
The skill includes Spock feature methods in Groovy classes that extend `spock.lang.Specification`.
The skill includes these methods even when they have no `@Test` annotation.

The skill marks Kotlin/Groovy tests as `manual migration` only when they run an engine-backed BPMN process or DMN decision.
The engine must be Camunda 7.
The skill records their source language in the `Notes` cell.

## Test models

Every BPMN, DMN, or CMMN model that a test deploys or parses appears in the Model Inventory.
Include models under `src/test/resources` in the Model Inventory.
Link each test ID to every model it deploys or parses in the `Models` cell.
Trace model resources through test setup and shared helpers.
Record the resolved path for each model resource.

| Model source | Model paths and notes to record | Handling |
|---|---|---|
| Explicit `@Deployment(resources = ...)` | Every declared resource path | Use the test kind's handling |
| Implicit method-level `@Deployment` | The first matching `<package path>/<TestClass>.<method>.<suffix>` resource | Use the test kind's handling |
| Implicit class-level `@Deployment` | The first matching `<package path>/<DeclaringClass>.<suffix>` resource, including a superclass that declares the annotation | Use the test kind's handling |
| Programmatic deployment | Every resource added through `repositoryService.createDeployment().addClasspathResource(...)` | Use the test kind's handling |
| Spring Boot auto-deployment | Models deployed by `@EnableProcessApplication` with `META-INF/processes.xml`, or by the starter's auto-deployment of `src/main/resources` | Use the test kind's handling |
| Standalone DMN parsing | A test or shared helper calls `DmnEngine.parseDecision(...)` on a resource. The skill records its path and links the DMN model to each affected test. | Use the test kind's handling |
| CMMN model deployed by a test | Record the CMMN path and note manual redesign | Report only |
| BPMN model built with the Camunda fluent model API | Record the model as programmatically built and note manual migration | Report only |

For implicit deployment, the skill tries suffixes in this order:

| Order | Suffix |
|---|---|
| 1 | `bpmn20.xml` |
| 2 | `bpmn` |
| 3 | `cmmn11.xml` |
| 4 | `cmmn10.xml` |
| 5 | `cmmn` |
| 6 | `dmn11.xml` |
| 7 | `dmn` |

## Test IDs and report

The skill uses `<module path>:<fully qualified class name>#<method>` as the stable Test ID for method-based tests.
The skill uses this method-based Test ID for each Spock feature method.
The skill uses `<module path>:<feature path>#<scenario name>@L<line>` as the stable Test ID for a Cucumber `Scenario`.
The skill resolves the feature path relative to its module.
The skill uses the `Scenario` line number in its Test ID.
For each Cucumber `Scenario Outline` data row, the skill uses the outline name and `Examples` row's line number in that format.
The line number distinguishes scenarios with duplicate names and separate `Examples` rows.
Resolve the module path relative to the project root.
Use `.` for the project root module.
For example, `.:com.example.JobAnnouncementProcessTest#testPublishOnlyOnWeb`.

Add a `Test Inventory` table to `MIGRATION_REPORT.md` with these columns in this order:

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|

Add a `Test kind counts` table to `MIGRATION_REPORT.md` with the columns `Test kind` and `Count`.
Include a row for every test kind, including zero counts.
Present the test-kind counts in the Step 2 Summary.
State how many tests are eligible for CPT migration.
List every test with handling `Report only` by Test ID and give its reason.
Record the reason for each `Report only` test in `Notes`.
When no test is eligible for CPT migration, state that the count is zero.
Do not ask an additional question about test migration during Step 2.
Do not migrate tests during Step 2.

## Handling overrides

| Condition | Handling | Reason or note |
|---|---|---|
| A remote-engine test reads a shared engine URL from an environment variable and does not start the engine | Report only | `shared environment` |

## Camunda 8.8 target

| Default handling | Camunda 8.8 handling | Reason |
|---|---|---|
| Migrate | Report only | `test migration needs Camunda 8.9 or later` |
| Migrate (lower priority) | Report only | `test migration needs Camunda 8.9 or later` |
| Report only | Keep `Report only` | For an in-scope test, the skill preserves the existing reason and adds `test migration needs Camunda 8.9 or later`. For `manual redesign`, the skill preserves the existing reason. |
| Not part of test migration | Keep `Not part of test migration` | Keep the existing reason |

---

## Migrate Engine-Backed Process Tests

Every instruction in this section is mandatory. Mark a preference with `(SHOULD)` and an option
with `(MAY)`.

### Scope

This section covers each `process test` that uses Camunda 7 engine test support without Spring.
Migrate every test in this scope. Spring tests, mock migration, DMN tests, and scenario tests belong
to separate migrations.

Use JUnit 5. Convert JUnit 3 and JUnit 4 process tests to JUnit 5. Keep test class names and method
names. (SHOULD)

| Camunda 8 target | Response |
|---|---|
| 8.9 or later | Use target-aligned CPT and follow this section. |
| 8.8 | Stop before changing test dependencies. Record a blocked item in `MIGRATION_REPORT.md` and ask the user to approve a target-compatible test setup. |

CPT assertions wait for asynchronous process behavior. The default assertion timeout is 10 seconds.
The default Java runtime uses Testcontainers and needs a Docker-compatible runtime.

### Inventory and deployment

1. Record every in-scope test class and method in the parity ledger. Record its engine support,
   deployed model paths, assertions, and job, timer, message, or signal operations.
2. Resolve every source deployment to the model files that it actually loads. Map each file to a
   converted copy named `converted-c8-*`.
3. Replace every implicit Camunda 7 `@Deployment` with an explicit CPT `@TestDeployment`. Use the
   converted-copy path resolved by the Test Inventory.
4. Keep the class and method annotation scope. A method-level `@TestDeployment` takes precedence
   over a class-level annotation.
5. Deploy converted copies only. Never deploy an original model.
6. Preserve the behavior of every passing Camunda 7 test.
7. Record a mapping gap in `MIGRATION_REPORT.md` when no listed mapping applies. Do not invent an
   API or report the affected test as migrated.

### Harness and API mappings

The mapping table covers engine-backed tests without Spring. Use the code-conversion pattern
catalog as the source of truth for exact API mappings. Record any disagreement with the catalog in
`MIGRATION_REPORT.md` and track catalog corrections in #3209.

| Camunda 7 | CPT 8.9 or later | Note |
|---|---|---|
| `@Rule ProcessEngineRule`, `@ClassRule`, or `ProcessEngineRule("custom.cfg.xml")` | `@CamundaProcessTest` on the class, with `CamundaClient client` and `CamundaProcessTestContext processTestContext` fields | Configure the CPT runtime in `camunda-container-runtime.properties`. |
| `@ExtendWith(ProcessEngineExtension.class)` or `@RegisterExtension ProcessEngineExtension` | `@CamundaProcessTest` on the class, with the same fields | |
| `extends ProcessEngineTestCase` (JUnit 3) | JUnit 5 class with `@CamundaProcessTest` | Add `@Test` to each `testXxx()` method. |
| `extends AbstractProcessEngineRuleTest` or `new StandaloneInMemoryTestConfiguration().rule()` | `@CamundaProcessTest` | These Camunda 7 helpers start a standalone engine with `MockExpressionManager` and no Spring context. |
| Test-only `camunda.cfg.xml` | Remove it when only the test engine uses it | Record plugins, custom history, and other behavior-changing settings. |
| Class- or method-level `@Deployment(resources = {...})` | Class- or method-level `@TestDeployment(resources = {...})` | Name converted copies. Method-level annotations take precedence. |
| Implicit `@Deployment` | Explicit `@TestDeployment(resources = "<resolved converted-copy path>")` | Use the path resolved by the Test Inventory. |
| `runtimeService().startProcessInstanceByKey(key, vars)` | `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Returns `ProcessInstanceEvent`. Follow the catalog's business-key pattern when needed. |
| `assertThat(pi).isWaitingAt("A")` | `assertThat(pi).hasActiveElements("A")` | |
| `isWaitingAtExactly("A")` | `hasActiveElementsExactly("A")` | |
| `isNotWaitingAt("A")` | `hasNoActiveElements("A")` | Do not use `hasNotActivatedElements`. |
| `hasPassed("A")` | `hasCompletedElements("A")` | |
| `hasPassedInOrder("A", "B")` | `hasCompletedElementsInOrder("A", "B")` | |
| `hasNotPassed("A")` | No exact counterpart | `hasNotActivatedElements` is stricter because it also fails when the element is active. Decide per test and record the decision. |
| `isEnded()` | `isCompleted()` | Use `isTerminated()` when the instance was cancelled. |
| `isNotEnded()` or `isActive()` | `isActive()` | |
| `isStarted()` | `isCreated()` | |
| `hasVariables("x")` | `hasVariableNames("x")` | |
| `variables().containsEntry("x", value)` | `hasVariable("x", value)` | Typed or serialized Java values become JSON. See the catalog's process-variable pattern. |
| `isWaitingFor("message")` | `isWaitingForMessage("message")` | |
| `task()`, `task("A")`, or `findId("Task name")` | `UserTaskSelectors.byElementId("A")` or `UserTaskSelectors.byTaskName("Task name")` | |
| `assertThat(task()).isAssignedTo("user")`, `.hasName(...)`, `.hasCandidateGroup(...)`, or `.hasDueDate(...)` | `assertThatUserTask(selector).hasAssignee("user")`, `.hasName(...)`, `.hasCandidateGroup(...)`, or `.hasDueDate(...)` | |
| `complete(task(), withVariables(...))` or `taskService.complete(id, vars)` | `processTestContext.completeUserTask("A", vars)` or `completeUserTask(selector, vars)` | A string argument is the BPMN element ID. |
| `claim(task(), "user")` | `client.newAssignUserTaskCommand(userTaskKey).assignee("user").send().join()` | Get the task key with a user-task search. Record a reason before dropping the step. |
| `complete(externalTask(), vars)` or `fetchAndLock(topic, ...)` followed by `complete` | `processTestContext.completeJob(jobType, vars)` | Use the converted topic as the job type. Use `throwBpmnErrorFromJob` for `handleBpmnError`. |
| `execute(job())` for an asynchronous continuation | Remove the manual job step | Camunda 8 continues asynchronously. Use a waiting assertion. |
| `execute(job())` or `managementService.executeJob(id)` for a timer | `processTestContext.increaseTime(Duration)` | Assert the timer catch event first. For a boundary timer, assert the attached task because CPT does not expose the timer as an active element. |
| `ClockUtil.setCurrentTime(date)` or `ClockUtil.reset()` | `processTestContext.setTime(instant)` | CPT resets the clock after each test. |
| `runtimeService.correlateMessage(name, businessKey, vars)` | `client.newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(vars).send().join()` | Get `key` from the converted model's message subscription, not the business key. |
| `runtimeService.signalEventReceived(name)` | `client.newBroadcastSignalCommand().signalName(name).send().join()` | |
| `historyService` or `runtimeService` queries used as assertions | CPT assertions | CPT assertions wait for the expected state. Search requests are eventually consistent. |
| An expected exception from process start or task completion because a delegate failed | `assertThat(pi).hasActiveIncidents()` | See the semantic differences below. |
| Process-test-coverage rule or extension | Remove | CPT reports process coverage. The parity subtask compares coverage. |
| `org.junit.Assert`, `@Before`, `@After`, `@Ignore`, or `@Test(expected = ...)` | JUnit 5 `Assertions` or AssertJ, `@BeforeEach`, `@AfterEach`, `@Disabled`, or `assertThrows` | |

### Semantic differences

| Camunda 7 behavior | Camunda 8 behavior | Required test change |
|---|---|---|
| The in-memory engine runs synchronously in the test thread until a wait state. | The runtime runs asynchronously. | Use waiting CPT assertions. Put non-CPT checks, such as Mockito `verify`, behind a timeout or Awaitility. |
| The job executor is off, and tests step through jobs with `execute(job())`. | Camunda 8 advances asynchronously. Workers or test job handlers complete service-task jobs. | Remove manual async-continuation steps. Record which job types run real workers and which use test handlers. |
| A failing synchronous delegate throws into the test. | A failing worker creates an incident after its retries. | Assert the incident instead of the exception. Record this semantic change. |
| A process-instance ID is a string. | A process-instance key is a `long`. | Update helpers and variables that store process-instance IDs. |
| `@Deployment` teardown deletes its deployment after each test. | CPT deletes runtime data and resets the clock after each test. | Keep test isolation. Fix tests that depend on state from another test. |

### Dependency changes

1. Remove `camunda-bpm-assert`, `camunda-bpm-junit5`, test-scoped `camunda-engine`, and Camunda 7
   coverage artifacts after migrating their uses.
2. Remove H2 when the embedded test engine is its only user.
3. Add `io.camunda:camunda-process-test-java` in test scope. Align its version with the target
   Camunda version and the catalog's dependency pattern.
4. Use the CPT Spring artifact instead when the module uses the Camunda Spring Boot Starter. That
   migration is outside this section.
5. Add `junit-vintage-engine` when non-process JUnit 4 tests remain in the module.
6. Align AssertJ with the version required by CPT.

### Recipe-assisted migration

The OpenRewrite pass adds the CPT dependency and renames assertions. Review every migrated test
against its Camunda 7 source. A successful compile does not prove behavioral parity.

Until `ReplaceAssertionsRecipe` fixes #3213, inspect and repair these cases:

| Recipe output or source | Required check |
|---|---|
| `hasNotActivatedElements(...)` produced from `isNotWaitingAt(...)` | Replace it with `hasNoActiveElements(...)`. |
| `variables().containsEntry(name, value)` | Require `isCreated().hasVariable(name, value)`. Keep unrelated AssertJ `containsEntry(...)` calls unchanged. |
| `variables().containsKey(name)` or `containsKeys(names...)` | Require `hasVariableNames(name)` or `hasVariableNames(names...)`. |
| Any other assertion chained after `variables()` | Keep the Camunda 7 call with a TODO. Do not replace it with `isCreated()`. |
| `hasVariables()` with no names | Keep the Camunda 7 call with a TODO. Do not replace it with `hasVariableNames()`, which passes without names. |

Until `ReplaceAssertionsRecipe` fixes #3214, change assertion imports before the recipe or convert
the assertions by hand afterward:

| Camunda 7 code | Workaround before the recipe |
|---|---|
| `import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;` | `import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;` |
| `import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;` | `import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;` |
| `BpmnAwareTests.assertThat(...)` or `ProcessEngineTests.assertThat(...)` | Use unqualified `assertThat(...)` with the static import from `ProcessEngineTests`. |

Apply the same import workaround to shared assertions from `CmmnAwareTests`. Keep CMMN-specific
assertions report-only because CPT has no counterpart.

### Limitations

Camunda 7 fluent BPMN models built in test code stay report-only. CMMN assertions stay report-only.
