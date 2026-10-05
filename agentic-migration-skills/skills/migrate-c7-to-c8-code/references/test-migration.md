# Test Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked
(SHOULD) and an option is marked (MAY).

This reference uses C7 for Camunda 7, C8 for Camunda 8, and CPT for Camunda Process Test.

## Test Inventory

The skill detects C7 tests during Step 2 for every migration scope, including Assessment only and code approach C.
During assessment, the skill edits no project file other than `MIGRATION_REPORT.md`.
The skill scans each module's build-declared test source sets.
The skill scans abstract test classes, shared test bases, test configuration classes, and `camunda.cfg.xml` in test resources.
The skill scans `src/test/java` and additional source sets such as `src/it/java` or Gradle `integrationTest`.

| Runs a BPMN process or DMN decision on a Camunda 7 engine | Uses a framework or approach that existed for Camunda 7 | Scope decision |
|---|---|---|
| Yes | Yes | When both prerequisites are met, the skill includes the test in scope. |
| No | Any | If a test does not run a BPMN process or DMN decision on a Camunda 7 engine, then the skill excludes the test from scope. |
| Yes | No | If a test does not use a framework or approach that existed for Camunda 7, then the skill excludes the test from scope. |
A dependency alone never makes a test eligible for migration.
For example, `camunda-platform-7-mockito` provides engine-backed helpers and `DelegateExecutionFake` for plain unit tests.

The skill inventories every test method declared or inherited by each concrete test class in the scanned test source sets.
It records each test's test kind and handling, including tests marked out of scope.
Apply the table from top to bottom. The first matching row assigns one test kind and handling.
The skill classifies tests by executed engine behavior, not assertion type.
The skill classifies an embedded Engine REST call from a `@SpringBootTest` as a remote-engine test, not a process test.
A real C7 process or decision test remains in scope when it asserts only endpoint responses or downstream side effects.
The skill records assertion gaps in the Test Inventory's Notes column for migration review.
The skill verifies that a direct service call resolves to a real C7 engine in the test or its
shared configuration.
Test rules, extensions, dependencies, and API references alone do not prove that a test executed a BPMN process or DMN decision.
Calls to mocks, fakes, or stubs do not count as engine execution.
The skill requires a completed task's `processInstanceId` to identify an executed BPMN process before
`TaskService.complete(...)` is a process-test signal.
`TaskService.newTask()` without a process instance is not a process-test signal.
The `camunda-platform-7-mockito` dependency can provide engine-backed helpers or `DelegateExecutionFake`.
When methods in one class differ, the skill classifies each method separately.

CMMN tests and tests that use unsupported Camunda engine internals do not meet this scope rule.
`ClockUtil` used to control timers is a supported test utility.
The skill does not assign `manual redesign` based on `ClockUtil` alone.
The skill inventories CMMN tests and tests that use unsupported engine internals as `manual redesign` with `Report only` handling.

## Test kinds

| Priority | Test kind | Detect by | Handling |
|---|---|---|---|
| 1 | out of scope (Camunda 8) | Uses Zeebe Process Test (`io.camunda.zeebe.process.test.*`) or CPT (`io.camunda.process.test.*`) without running a C7 engine. | Not part of test migration |
| 2 | manual redesign | A C7 engine test covers CMMN APIs or models, or unsupported engine internals such as `ProcessEnginePlugin`, BPMN parse listeners, custom history levels, or `ProcessEngineConfigurationImpl` internals. `ClockUtil` timer control does not trigger this signal by itself. | Report only |
| 3 | manual migration | JGiven (`io.holunda.testing:camunda-bpm-jgiven`) tests require manual migration. Cucumber scenarios use Camunda 7 APIs to run an engine-backed BPMN process or DMN decision. The Cucumber classification includes applicable hooks, not only steps. An in-scope test uses Arquillian, camunda-bpm-needle (CDI), or the Camunda 7 Quarkus extension. A test runs an engine-backed process from a BPMN model built with the Camunda 7 fluent model API. A Kotlin or Groovy test uses Camunda 7 test APIs to run an engine-backed BPMN process or DMN decision. | Report only |
| 4 | scenario test | Runs `org.camunda.bpm.scenario.*` against C7. | Migrate (lower priority) |
| 5 | remote-engine test | A test runs a BPMN process or DMN decision on a running Camunda 7 engine through Engine REST at `/engine-rest`, `org.camunda.bpm.client.*`, or Testcontainers for C7. | Report only |
| 6 | decision test | Evaluates a DMN decision on C7 through `DmnEngineRule`, `DmnEngine`, `DmnEngineConfiguration`, or `DecisionService`. | Migrate |
| 7 | process test | Runs a BPMN process on C7 through `ProcessEngineRule`, `ProcessEngineExtension` including `org.camunda.bpm.extension:camunda-bpm-junit5`, `ProcessEngineTestCase`, `BpmnAwareTests`, `ProcessEngineTests`, `AbstractProcessEngineRuleTest`, or `StandaloneInMemoryTestConfiguration`. It may call a real C7 engine's `RuntimeService` to start a process (for example, `startProcessInstanceByKey(...)`), `TaskService` to complete a task with a non-null `processInstanceId`, or `RuntimeService` to correlate a message. It may call a Spring Boot endpoint that starts a process, completes a process-backed task, or correlates a message on a real C7 engine. | Migrate to CPT |
| 8 | out of scope | Does not execute a real C7 BPMN process or DMN decision. This includes Kotlin or Groovy tests that use Camunda 7 test APIs but run no process or decision, standalone tasks created with `TaskService.newTask()` without a `processInstanceId`, plain Java tests, delegate or worker unit tests, `DelegateExecutionFake`, mocked `DelegateExecution`, mocked `RuntimeService`, Spring test slices with mocked C7 APIs, or WireMock Engine REST stubs. The skill classifies remote health or metadata probes that run no process or decision as out of scope. When the shared-engine exception in Scope confirmation applies, the skill classifies the probe as a remote-engine test instead. | Not part of test migration |

`@Deployment` is model-resolution evidence, not a test-kind signal by itself.
While the remote-engine migration procedure is undefined, the skill keeps remote-engine test rows at Report only.

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
| `Variables.putValue("a", 1).putValue("b", "x")` | A `Map<String, Object>` | The skill keeps the same logical inputs. Camunda 8 serializes map values as JSON. Where a Camunda 7 value is a `Date` or typed value, the skill checks that the converted DMN reads its JSON representation as intended. The skill does not assume the Java type survives serialization. |
| `result.getSingleResult().getSingleEntry()` or `result.getSingleEntry()` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(value)` | Use for one output column and one result. |
| `result.getSingleResult().getEntry("a")` or `getEntryMap()` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(Map.of("a", value, "b", value))` | `hasOutput` compares all outputs. Parse `response.getDecisionOutput()` to check only selected outputs. |
| `result.collectEntries("x")` with hit policy `COLLECT` | The skill parses `response.getDecisionOutput()` as a list of scalar values for one output column, or a list of maps keyed by output name for multiple output columns. The skill selects values by output name and compares rows without relying on their order. | Camunda 8 returns `COLLECT` results in arbitrary order. |
| `result.collectEntries("x")` with hit policy `RULE ORDER` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(List.of(...))` | The order is defined by the hit policy. |
| `result.isEmpty()` or `getSingleResult()` returns `null` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).hasNoMatchedRules()` | |
| Matched-rule checks through `HistoricDecisionInstance` | `hasMatchedRules(int...)` or `hasNotMatchedRules(int...)` | When the expected rule numbers are a subset of the matched rules, `hasMatchedRules` passes. |
| An expected `DmnEngineException`, including one wrapped by `DecisionService` in `ProcessEngineException` | The skill checks `response.getFailureMessage()` and `response.getFailedDecisionId()` | Camunda 8 returns a failed response instead of throwing. `isEvaluated()` fails for this response. |

The skill reads the hit policy and output columns from the converted DMN copy before choosing an assertion.
When the skill checks a map, it uses the output names from the converted DMN copy.
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
| Standalone DMN tests | When tests are their only users, the skill removes test-scoped `org.camunda.bpm.dmn:camunda-engine-dmn` and `org.camunda.bpm.dmn:camunda-engine-feel-*` artifacts. | Add `io.camunda:camunda-process-test-java` in test scope. |
| Spring decision tests | When tests are their only users, the skill removes test-scoped `org.camunda.bpm.dmn` engine and FEEL artifacts. | Use the CPT Spring artifact that matches the production Spring Boot starter. Use `camunda-process-test-spring` with Spring Boot 4 or `camunda-process-test-spring-boot-3` with the Spring Boot 3 starter. |
| Process engine used only by tests | When tests are its only users, the skill removes the test-scoped Camunda 7 engine. | Use the CPT artifact for the selected test harness. |

The skill inventories each dependency before removal.
When production code uses an artifact, the skill keeps it.
When a production dependency has no Camunda 8 equivalent, the skill records a required redesign.

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
| A test uses CMMN APIs or models, or unsupported Camunda engine internals | The skill marks the test as `manual redesign` and uses `Report only` handling. The skill applies this classification to tests that do not run a BPMN process or DMN decision. |
| A test uses `ClockUtil` to control a timer | Treat `ClockUtil` as a supported test utility. Do not assign `manual redesign` based on `ClockUtil` alone. |
| `@Test`, `@ParameterizedTest`, `@RepeatedTest`, or a JUnit 3 test method | The method runs a BPMN process or DMN decision on a Camunda 7 engine |
| `@Deployment` | The method runs a process or decision. The annotation alone is not enough |
| `@SpringBootTest` | The embedded engine starts a process, completes a task, correlates a message, or handles an endpoint that does one |
| A Cucumber `Scenario` or `Scenario Outline` data row | Its step definitions or applicable hooks run a BPMN process or DMN decision on a Camunda 7 engine |
| A remote-engine test reads a shared engine URL from an environment variable and does not run a process or decision | Keep it as `remote-engine test` and use `Report only` handling. Record `shared environment` as the reason. |
| A remote test makes only health or metadata calls and does not run a process or decision, without the shared-engine exception | The skill classifies the test as out of scope. |
| A Camunda 7 dependency or a test class name | Not sufficient without an engine-backed process or decision |

## Modifiers

The skill records modifiers only for process tests and decision tests.

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
Where a module has `src/test/java`, `src/test/kotlin`, or `src/test/groovy`, the skill scans each present directory.
The skill also scans each additional test source set declared by the build, such as `src/it/java` or Gradle `integrationTest`.
The skill follows each Cucumber runner or build configuration to locate executed `.feature` files in test resources.
The skill inventories each Cucumber `Scenario` as one test.
The skill inventories each data row in a Cucumber `Scenario Outline` `Examples` table as a separate test.
The skill reads methods annotated with `@Given`, `@When`, `@Then`, `@And`, or `@But` as step definitions.
The skill reads constructor-registered lambda steps, such as `io.cucumber.java8.En`.
The skill does not inventory step-definition methods, lambda registrations, a Cucumber runner class, or hook methods as separate tests.
When the skill checks for Camunda 7 process or decision calls, it inspects both step-definition methods and constructor-registered lambda steps.
The skill reads applicable Cucumber hooks.
The skill uses them to check whether scenarios run BPMN processes or DMN decisions on a Camunda 7 engine.
The skill reads shared test bases, abstract test classes, test configuration classes, and `camunda.cfg.xml` under test resources.
When a shared base or configuration supplies an engine signal, apply it to each affected test method.
The skill includes methods annotated with `@Test`, `@ParameterizedTest`, or `@RepeatedTest`.
The skill also includes JUnit 3 `public void test...()` methods.
The skill includes Spock feature methods in Groovy classes that extend `spock.lang.Specification`.
The skill includes these methods without a `@Test` annotation.

Where Kotlin or Groovy tests run an engine-backed BPMN process or DMN decision on C7, the skill marks them as `manual migration`.
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
| BPMN model built with the Camunda fluent model API, such as `Bpmn.createExecutableProcess()` | Record the model as programmatically built and note the manual migration reason in `Notes` | Report only |

When the skill resolves an implicit deployment, it tries suffixes in this order:

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

The skill uses `<module path>:<fully qualified concrete test class name>#<method>` as the stable Test ID for method-based tests.
For each inherited method, the skill creates one row for every concrete test class that executes it.
The Test ID uses the concrete class and method name.
The File column names the source file that declares the method.
The skill does not create a row for an abstract class by itself.
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

When the target version is Camunda 8.8, the skill detects every test.

| Default handling | Camunda 8.8 handling | Reason |
|---|---|---|
| Migrate | Report only | `test migration needs Camunda 8.9 or later` |
| Migrate to CPT | Report only | `test migration needs Camunda 8.9 or later` |
| Migrate (lower priority) | Report only | `test migration needs Camunda 8.9 or later` |
| Report only | Keep `Report only` | Where a test is in scope, the skill preserves the existing reason and adds `test migration needs Camunda 8.9 or later`. Where the test kind is `manual redesign`, the skill preserves the existing reason. |
| Not part of test migration | Keep `Not part of test migration` | Keep the existing reason |

---

## Migrate Engine-Backed Process Tests

### Scope

This section covers each `process test` that uses Camunda 7 engine test support without Spring.
Migrate every test in this scope. Spring tests, decision tests, and scenario tests follow their own
sections in this reference. Mock migration belongs to a separate migration.

Use JUnit 5. Convert JUnit 3 and JUnit 4 process tests to JUnit 5. Keep test class names and method
names. (SHOULD)

| Camunda 8 target | Response |
|---|---|
| 8.9 or later | Use target-aligned CPT and follow this section. |
| 8.8 | Apply the Camunda 8.8 target table above. Keep `Report only` handling with the reason `test migration needs Camunda 8.9 or later`. Do not change test dependencies. |

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
5. Deploy only converted copies and accepted forms from the Test Inventory. Never deploy an
   original model or a form outside the inventory.
6. Preserve the behavior of every passing Camunda 7 test.
7. If no listed mapping applies, then record a mapping gap in `MIGRATION_REPORT.md`. Do not invent
   an API or report the affected test as migrated.

### Harness and API mappings

The mapping table covers engine-backed tests without Spring. Use the code-conversion pattern
catalog as the source of truth for exact API mappings. Record any disagreement with the catalog in
`MIGRATION_REPORT.md`.

| Camunda 7 | CPT 8.9 or later | Note |
|---|---|---|
| `@Rule ProcessEngineRule`, `@ClassRule`, or `ProcessEngineRule("custom.cfg.xml")` | `@CamundaProcessTest` on the class, with `CamundaClient client` and `CamundaProcessTestContext processTestContext` fields | Configure the CPT runtime in `camunda-container-runtime.properties`. |
| `@ExtendWith(ProcessEngineExtension.class)` or `@RegisterExtension ProcessEngineExtension` | `@CamundaProcessTest` on the class, with the same fields | |
| `extends ProcessEngineTestCase` (JUnit 3) | JUnit 5 class with `@CamundaProcessTest` | Add `@Test` to each `testXxx()` method. Annotate an overridden `setUp()` with `@BeforeEach` and an overridden `tearDown()` with `@AfterEach`. Remove the `super.setUp()` and `super.tearDown()` calls. |
| `extends AbstractProcessEngineRuleTest` or `new StandaloneInMemoryTestConfiguration().rule()` | `@CamundaProcessTest` | These Camunda 7 helpers start a standalone engine with `MockExpressionManager` and no Spring context. |
| Test-only `camunda.cfg.xml` | Where only the test engine uses it, remove it. | Record plugins, custom history, and other behavior-changing settings. |
| Class- or method-level `@Deployment(resources = {...})` | Class- or method-level `@TestDeployment(resources = {...})` | Name converted copies. Method-level annotations take precedence. |
| Implicit `@Deployment` | Explicit `@TestDeployment(resources = "<resolved converted-copy path>")` | Use the path resolved by the Test Inventory. |
| `runtimeService().startProcessInstanceByKey(key, vars)` | `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Returns `ProcessInstanceEvent`. Where the test sets a business key, follow the catalog's business-key pattern. |
| `assertThat(pi).isWaitingAt("A")` | `assertThat(pi).hasActiveElements("A")` | |
| `isWaitingAtExactly("A")` | `hasActiveElementsExactly("A")` | |
| `isNotWaitingAt("A")` | `hasNoActiveElements("A")` | Do not use `hasNotActivatedElements`. |
| `hasPassed("A")` | `hasCompletedElements("A")` | |
| `hasPassedInOrder("A", "B")` | `hasCompletedElementsInOrder("A", "B")` | |
| `hasNotPassed("A")` | No exact counterpart | `hasNotActivatedElements` is stricter because it also fails for an active element. Decide per test and record the decision. |
| `isEnded()` | `isCompleted()` | If the test cancels the instance, then use `isTerminated()`. |
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
| `execute(job())` or `managementService.executeJob(id)` for a timer | `processTestContext.increaseTime(Duration)` | Assert the timer catch event first. When the timer is a boundary timer, assert the attached task because CPT does not expose the timer as an active element. |
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
| The in-memory engine runs synchronously in the test thread up to the next wait state. | The runtime runs asynchronously. | Use waiting CPT assertions. Put non-CPT checks, such as Mockito `verify`, behind a timeout or Awaitility. |
| The job executor is off, and tests step through jobs with `execute(job())`. | Camunda 8 advances asynchronously. Workers or test job handlers complete service-task jobs. | Remove manual async-continuation steps. Record which job types run real workers and which use test handlers. |
| A failing synchronous delegate throws into the test. | A failing worker creates an incident after its retries. | Assert the incident instead of the exception. Record this semantic change. |
| A process-instance ID is a string. | A process-instance key is a `long`. | Update helpers and variables that store process-instance IDs. |
| `@Deployment` teardown deletes its deployment after each test. | CPT deletes runtime data and resets the clock after each test. | Keep test isolation. Fix tests that depend on state from another test. |

### Dependency changes

1. Remove `camunda-bpm-assert`, `camunda-bpm-junit5`, test-scoped `camunda-engine`, and Camunda 7
   coverage artifacts after migrating their uses.
2. Where the embedded test engine is the only H2 user, remove H2.
3. Add `io.camunda:camunda-process-test-java` in test scope. Align its version with the target
   Camunda version and the catalog's dependency pattern.
4. Where the module uses the Camunda Spring Boot Starter, use the CPT Spring artifact instead. That
   migration is outside this section.
5. Where non-process JUnit 4 tests remain in the module, add `junit-vintage-engine`.
6. Align AssertJ with the version required by CPT.

### Recipe-assisted migration

The OpenRewrite pass adds the CPT dependency and renames assertions. Review every migrated test
against its Camunda 7 source. A successful compile does not prove behavioral parity.

While #3213 is open, inspect and repair these `ReplaceAssertionsRecipe` cases:

| Recipe output or source | Required check |
|---|---|
| `hasNotActivatedElements(...)` produced from `isNotWaitingAt(...)` | Replace it with `hasNoActiveElements(...)`. |
| `variables().containsEntry(name, value)` | Require `isCreated().hasVariable(name, value)`. Keep unrelated AssertJ `containsEntry(...)` calls unchanged. |
| `variables().containsKey(name)` or `containsKeys(names...)` | Require `hasVariableNames(name)` or `hasVariableNames(names...)`. |
| Any other assertion chained after `variables()` | Keep the Camunda 7 call with a TODO. Do not replace it with `isCreated()`. |
| `hasVariables()` with no names | Keep the Camunda 7 call with a TODO. Do not replace it with `hasVariableNames()`, which passes without names. |

While #3214 is open, change assertion imports before `ReplaceAssertionsRecipe` runs or convert the
assertions by hand afterward:

| Camunda 7 code | Workaround before the recipe |
|---|---|
| `import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;` | `import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;` |
| `import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;` | `import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;` |
| `BpmnAwareTests.assertThat(...)` or `ProcessEngineTests.assertThat(...)` | Use unqualified `assertThat(...)` with the static import from `ProcessEngineTests`. |

Apply the same import workaround to shared assertions from `CmmnAwareTests`. Keep CMMN-specific
assertions report-only because CPT has no counterpart.

### Limitations

Camunda 7 fluent BPMN models built in test code stay report-only. CMMN assertions stay report-only.

## Spring Test Migration

## Scope

The skill applies Spring test migration only to process test rows with the `Spring` modifier and handling `Migrate to CPT`.

| Spring evidence | Test Inventory treatment |
|---|---|
| `@SpringBootTest` with a process or decision test | Record the `Spring` modifier. |
| `@RunWith(SpringRunner.class)`, `@RunWith(SpringJUnit4ClassRunner.class)`, or `@ExtendWith(SpringExtension.class)` with a process or decision test | Record the `Spring` modifier. |
| `@ContextConfiguration` loads Spring XML or Java configuration with `SpringProcessEngineConfiguration` or `ProcessEngineFactoryBean` | Record the `Spring` modifier. |
| `@Autowired @Rule ProcessEngineRule`, `@Autowired RuntimeService`, or `BpmnAwareTests.init(processEngine)` | Record the `Spring` modifier. |
| `AbstractProcessEngineRuleTest` or `StandaloneInMemoryTestConfiguration` without a Spring context | Record the test kind without the `Spring` modifier. |
| `@WebMvcTest`, `@DataJpaTest`, or another Spring test slice without execution against a real C7 engine | Out of scope. |
| A shared engine in a WAR or `processes.xml` application-server deployment | Report only. Record the manual migration reason in Notes. |

The skill does not classify every `@SpringBootTest` as a process test. The skill uses the Test
Inventory kind and modifier.

A Spring test slice that uses only mocked C7 APIs is out of scope.

## Harness and dependencies

The skill keeps the Spring Boot version and production starter selected in Step 3. The skill selects
the matching CPT dependency from `code-conversion/patterns/10-general/dependencies.md`.

| Target application | CPT dependency in test scope | Test annotation |
|---|---|---|
| Spring Boot 4 with `camunda-spring-boot-starter` | `camunda-process-test-spring` | `@SpringBootTest` and `@CamundaSpringProcessTest` |
| Spring Boot 3 with `camunda-spring-boot-3-starter` | `camunda-process-test-spring-boot-3` | `@SpringBootTest` and `@CamundaSpringProcessTest` |
| Spring without Spring Boot | `camunda-process-test-java` | `@CamundaProcessTest` |

The CPT Spring dependencies include the CPT Java API. The skill does not add
`camunda-process-test-java` with either Spring dependency.

The skill uses the dependency catalog for artifact versions. The skill does not choose a different
starter for tests than the production starter.
The skill removes each C7 test dependency that no remaining test or production code uses after
migration.
When only migrated tests use them, the skill removes `camunda-bpm-spring-boot-starter-test`,
`camunda-bpm-junit5`, and `camunda-bpm-assert`.
If any test outside the migrated set or production code still uses a dependency, then the skill keeps it.

The skill migrates each Spring Boot test to JUnit 5. The skill keeps `@SpringBootTest` and adds
`@CamundaSpringProcessTest`. The skill injects `CamundaClient` and `CamundaProcessTestContext` with
`@Autowired`.

| C7 Spring test | C8 CPT test |
|---|---|
| `@RunWith(SpringRunner.class) @SpringBootTest` | `@SpringBootTest @CamundaSpringProcessTest` |
| `@Autowired RuntimeService`, `TaskService`, `HistoryService`, or `ProcessEngine` | `@Autowired CamundaClient` and `CamundaProcessTestContext` |
| `@Autowired @Rule ProcessEngineRule` or `BpmnAwareTests.init(processEngine)` | The skill removes the engine rule and initialization |
| `camunda.bpm.*` test-engine properties | The skill removes them. Where a migrated test needs `camunda.process-test.*` properties, the skill adds them. |
| H2 used only by the embedded engine | The skill removes H2. The skill keeps a data source used by the application |
| The C7 test transaction reverts engine and application state | The skill keeps `@Transactional` only for application database state |

The skill uses `@MockitoBean` with Spring Boot 4. The skill uses a supported Mockito test
annotation with the selected Spring Boot 3 version. When Step 3 changes Spring Boot 3 to 4, the
skill migrates `@MockBean` annotations to `@MockitoBean`.

Where practical, the skill keeps each test's class and method names. (SHOULD)

## Deployment

The skill uses the converted copies in the application's `@Deployment` annotation. (SHOULD)
This also exercises Step 4 check 13.

When a test needs a resource set that differs from the application's deployment, the skill adds
`@TestDeployment`. This rule applies to CPT 8.9 or later. The skill uses converted copies in every
`@TestDeployment`. A method-level annotation takes precedence over a class-level annotation.

## Workers and mocks

The skill keeps each C7 test's mock boundary. The Test Parity record owns approved test and mock
changes.

When a C7 test mocks a service called by a delegate, the skill mocks the same service in the CPT
test. The skill runs the real C8 worker.

When a C7 test mocks a delegate bean, the skill disables the matching C8 worker. The skill mocks
its job type through `CamundaProcessTestContext`.

The skill sets `camunda.client.worker.override.<job-type-or-worker-name>.enabled=false` to disable
the real worker. The skill uses the job type with `processTestContext.mockJobWorker("<job-type>")`.

The skill registers the mocked job worker before the test starts a process. The skill gives it the
same completion variables, BPMN error, or failure outcome as the C7 mock.

If the C7 test ran a delegate for real, then the skill keeps the C8 worker real. The skill asks
the user before it changes this boundary. The skill records each approved boundary change in the
Test Parity record.

```java
processTestContext.mockJobWorker("ship-order").thenComplete();
```

## Endpoint-driven tests

The skill keeps a test's `MockMvc`, `TestRestTemplate`, or `WebTestClient` call to the application
endpoint.

The skill preserves the endpoint operation that the test exercises.
The skill maps the original C7 operation to the equivalent `CamundaClient` operation.

| C7 endpoint operation | C8 endpoint operation |
|---|---|
| Starts a BPMN process | Starts the same process through `CamundaClient`. |
| Completes a process-backed task | Completes the same task through `CamundaClient`. |
| Correlates a message | Correlates the same message through `CamundaClient`. |

When the endpoint starts or advances a process, C8 workers can run asynchronously after the endpoint
returns. The skill uses waiting CPT assertions for process state or worker effects that the test
observes. The skill wraps asynchronous Mockito `verify` calls with a timeout.

## Startup hooks and transaction state

CPT runs `@PostConstruct` methods and `CommandLineRunner` callbacks once per Spring context. CPT
deletes runtime data after each test.

When an application hook starts a process, deploys resources, or sends a message, the skill adds a
minimal `TestProcessApplication`. The test app uses a package outside the production application's
component-scan root. The test app sets `scanBasePackages` to the required controllers, services, and workers.
The test app adds `@Deployment` with the converted copies.

The skill keeps the production startup callback out of the minimal test application's scan.
The skill replays each startup-hook action after CPT starts the test runtime.

| C7 hook action | CPT test action |
|---|---|
| Deploys resources | The test app adds the converted copies to `@Deployment`. |
| Starts a process | The test calls the startup method in `@BeforeEach` with `CamundaClient`. |
| Sends a message | The test sends the equivalent message in `@BeforeEach`. |

The skill keeps `@Transactional` for the application's database only. The skill does not expect it
to restore C8 process state.

## Test Parity record

When the user approves a test or mock boundary change, the skill records it before changing the
boundary in `MIGRATION_REPORT.md` at the project root.
When `MIGRATION_REPORT.md` does not exist, the skill creates it.
When `MIGRATION_REPORT.md` exists, the skill adds the Test Parity record and preserves its existing content.
Record one row per approved change with these columns:

| Test ID | C7 boundary | Approved C8 boundary | Approver | Reason |
|---|---|---|---|---|

## Spring without Spring Boot

The skill uses `@CamundaProcessTest` for a Spring application that does not use Spring Boot. The
skill starts its workers with the injected `CamundaClient` through the application's bootstrap code.

When the skill migrates a non-Boot Spring test that uses JUnit 4, it replaces its runner with
`@ExtendWith(SpringExtension.class)`.
The skill replaces JUnit 4 test and lifecycle annotations and assertions with JUnit 5 equivalents.
It updates their imports.

| JUnit 4 Spring test | JUnit 5 CPT test |
|---|---|
| `@RunWith(SpringJUnit4ClassRunner.class)` or `@RunWith(SpringRunner.class)` | `@ExtendWith(SpringExtension.class)` without `@RunWith`. |

When the migrated test needs Spring-managed beans, the skill keeps `@ContextConfiguration` and adds
`@ExtendWith(SpringExtension.class)`.

If the application has no usable worker bootstrap, then the skill sets the test's handling to
`Report only`. The skill records the manual migration reason in the Notes column of
`MIGRATION_REPORT.md`.
The skill states which bootstrap is missing and why it cannot start the workers.
The skill does not invent a new worker bootstrap.

## Camunda Platform Scenario Test Migration

### Migration gate

The Step 2 Test Inventory controls whether the skill migrates each scenario test. The gate covers
test dependencies, test edits, and timer handling in every instruction below.

| Selected code approach | Step 2 Test Inventory `Handling` | Action |
|---|---|---|
| Approach A or B | `Migrate (lower priority)` | Apply the preparation, dependency, wait-state, mapping, and time steps below. |
| Approach A or B | Any value other than `Migrate (lower priority)`, including missing | Follow any recorded disposition. Do not add CPT dependencies or modify tests or their build configuration. |
| Approach C | Any value, including missing | Assess only. Do not add CPT dependencies or modify tests or their build configuration. |

### Scope and target

When an in-scope test method uses `Scenario.run(...)` or `Scenario.use(...)` with a
`ProcessScenario`, the test inventory classifies it as a `scenario test`.
The artifacts `org.camunda.bpm.extension.scenario:camunda-platform-scenario-runner` and
`org.camunda.bpm.extension:camunda-bpm-assert-scenario` are detection signals. Their JARs contain
overlapping classes in `org.camunda.bpm.scenario`. The skill keeps only one of these artifacts on
each test classpath. When remaining tests require both artifacts, the skill separates their
test classpaths. The skill inspects the test calls before classifying the method.

When the Step 2 Test Inventory marks a scenario test `Migrate (lower priority)`, the skill targets
a Java test with Camunda Process Test (CPT) and `io.camunda:camunda-process-test-java`. The target
is Camunda 8.9 or later.
The skill does not create CPT instruction-based JSON tests.

When the target is Camunda 8.8, the skill sets scenario-test handling to `Report only`. The skill
records the test as `manual` in the parity ledger with the exact reason `test migration needs
Camunda 8.9 or later`.

The scenario runner's Cucumber module, logging, and history fast-forward reports stay out of scope.

### Prepare the test

When the skill changes a class's setup, it first inspects every test method and each method's shared
Scenario runner, `ProcessScenario` mock, C7 engine rule, and deployment dependencies.

| Retained method condition | Class setup action |
|---|---|
| No retained method needs C7 Scenario setup. | Convert the shared setup to CPT or remove it. |
| A retained manual method needs C7 Scenario setup. | While a retained manual method needs C7 Scenario setup, the skill moves migrated methods to a separate CPT class or retains the Scenario runner, `ProcessScenario` mock, C7 engine rule, and deployments. |

When the skill plans a target-build change, it checks test-source roots, test filters, and resource
processing in every Maven module or Gradle source set.
Where a project uses Maven, the skill checks each module's
`testSourceDirectory`, compiler include patterns, `resources`, and `testResources` declarations.
Where a project uses Maven, the skill checks resource filters, includes, excludes, and `targetPath`
settings. Where a project uses Gradle, the skill checks each test source set and its test-task
include and exclude patterns. Where a project uses Gradle, the skill checks source-set resource
directories and matching resource-processing tasks such as `processResources` and
`processTestResources`. Where a project uses Gradle, the skill checks their filters and output
paths. The skill checks plugins or tasks that copy or generate resources.

| Source-set condition | Migration action |
|---|---|
| Multiple C7 modules compile the same physical test source. | Migrate that source only once to the CPT suite. Keep each module-qualified C7 test ID in the inventory. Map duplicate module executions to one CPT test ID. |
| A target module has no test sources outside the shared set and no unique resources, resource-processing behavior, main outputs, generated outputs, or build responsibilities. | Remove the module. |
| A target module has test sources outside the shared set or unique resources, resource-processing behavior, main outputs, generated outputs, or build responsibilities. | Preserve every unique test, resource, resource-processing rule, output, and build responsibility in a reconfigured module that excludes shared sources or in the primary module. If the skill cannot preserve all unique content, then the skill retains the target module. |

When a module applies different filters, target paths, or generation tasks, a shared resource root
can produce unique output.

| Camunda 7 engine-test setup | Camunda Process Test 8.9 or later | Notes |
|---|---|---|
| `@Rule ProcessEngineRule processEngineRule = new ProcessEngineRule()` | Add `@CamundaProcessTest` to the class. Remove the `ProcessEngineRule` field. | CPT supplies the engine-test runtime. |
| `@Deployment(resources = "source.bpmn")` | `@TestDeployment(resources = "converted-c8-source.bpmn")` | Deploy the converted C8 copy. |
| Test code needs a `CamundaClient` | Inject `CamundaProcessTestContext processTestContext`. Call `processTestContext.createClient()`. | |

1. The skill reads the converted copy before mapping test behavior. It uses that copy to find element
   IDs, external job types, message names and correlation keys, and timer definitions.
2. When the test completes a user task, the skill checks that the converted copy declares
   `<zeebe:userTask />`. Without the marker, Camunda uses a job-worker implementation, so the User
   Task API has no user-task instance to complete.
3. The skill moves JUnit 3 and JUnit 4 scenario tests to JUnit 5.
4. When no retained method needs the `ProcessScenario` mock or Scenario runner setup, the skill
   removes both.
5. When no remaining test uses a Scenario artifact, the skill removes the artifact.
6. The skill adds `io.camunda:camunda-process-test-java` in test scope.
7. The skill keeps Mockito initialization and cleanup for retained annotations that rely on
   `MockitoAnnotations.openMocks(this)`.

The Scenario runner completed external tasks itself.
When the Camunda 7 test ran a Java delegate for real, the skill keeps the migrated worker real and
does not add a mock.

### Wait-state behavior

The skill uses a CPT conditional behavior for each user-task, message, signal, event-gateway, or
conditional-event stub. Each condition
waits for the corresponding process state. The action resolves that state so CPT can detect it
again.

Where the corresponding CPT API accepts a process-instance selector, the skill scopes a condition or
action to the Scenario instance. The user-task condition and completion action use the
process-instance key from the Scenario start result.

The message action targets a message name and evaluated correlation key, not the Scenario start
result's process-instance key. The signal action broadcasts by signal name and can also advance
another process instance waiting for that signal.

When the process path is linear, the skill may use sequential CPT calls instead of conditional
behaviors. (MAY) The skill advances time explicitly for timer stubs.

```java
long processInstanceKey = processInstance.getProcessInstanceKey();
processTestContext
    .when(
        () ->
            assertThatProcessInstance(byKey(processInstanceKey))
                .hasActiveElements("Review"))
    .as("Review")
    .then(
        () ->
            processTestContext.completeUserTask(
                byElementId("Review", processInstanceKey), variables));
```

The skill preserves every existing stub. The skill does not add behavior for an unstubbed wait
state. When a process reaches an unstubbed wait state, CPT leaves it waiting. When the process test
times out, its final assertion fails.

### Scenario-to-CPT mapping

When the skill records a scenario test as `manual`, it stores the specific reason in that test's
parity-ledger entry.

| Camunda Platform Scenario | Camunda Process Test 8.9 or later | Notes |
|---|---|---|
| `@Mock ProcessScenario process` and its Scenario stubs | The skill converts each migrated Scenario stub with the matching CPT behavior below. When no retained method needs C7 Scenario setup, the skill removes the mock and Scenario runner setup. | Map each verification to the CPT assertion rows below. |
| `MockitoAnnotations.openMocks(this)` and matching cleanup | When no retained Mockito annotations require initialization, the skill removes initialization and matching cleanup. | When retained `@Mock`, `@Spy`, `@Captor`, or `@InjectMocks` fields rely on it, the skill keeps initialization and cleanup. |
| JUnit 4 `@Before`, `@After`, and `@Test` | JUnit 5 `@BeforeEach`, `@AfterEach`, and `@Test` | |
| `waitsAtUserTask("X")` returning `task.complete(variables)` | `when(() -> assertThatProcessInstance(byKey(processInstanceKey)).hasActiveElements("X")).as("X").then(() -> processTestContext.completeUserTask(byElementId("X", processInstanceKey), variables))` | The action completes the task tested by the condition. |
| `thenReturn(a, b)` for repeated actions on a conditional behavior | Chain `.then(a).then(b)` on the corresponding CPT conditional behavior. | CPT repeats the last action after earlier actions run. The skill does not apply this chain to worker mocks. |
| `task.handleBpmnError(...)` or `task.handleEscalation(...)` on a user task | Record `manual` in the parity ledger with the unsupported operation as the reason | CPT has no direct user-task BPMN error or escalation action. |
| `waitsAtServiceTask`, `waitsAtSendTask`, `waitsAtBusinessRuleTask`, `waitsAtMessageIntermediateThrowEvent`, or `waitsAtMessageEndEvent` completing an external task | `processTestContext.mockJobWorker(type).thenComplete(variables)` | Read `type` from the converted copy. |
| The same external-task stubs handling a BPMN error | `processTestContext.mockJobWorker(type).thenThrowBpmnError(code, variables)` | Read `type` from the converted copy. |
| Repeated external-task actions on a linear path | Call `completeJob(...)` or `throwBpmnErrorFromJob(...)` once per activation in the tested order. | The skill does not register a worker mock for these repeated actions. The skill does not chain `thenComplete(...)` or `thenThrowBpmnError(...)` because their builder methods return `JobWorkerMock`. |
| Repeated external-task actions on a non-linear path | Configure `mockJobWorker(type).withHandler(...)` to select the response for each activated job. | |
| `waitsAtTimerIntermediateEvent("T")` with an empty action | Assert `hasActiveElements("T")`, then call `processTestContext.increaseTime(duration)` | Read the duration from the converted timer definition. |
| `action.defer(period, action)` | Increase time in bounded steps, then run the deferred action | Follow the time rule below. |
| `waitsAtMessageIntermediateCatchEvent` or `waitsAtReceiveTask` with `receive(variables)` | Correlate the message with `client.newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(variables).send().join()` | Read the message name and correlation-key FEEL expression from the converted copy's `zeebe:subscription`. Evaluate the expression against the test variables. Pass the result to `correlationKey(...)`. Never pass the expression text, such as `=orderId`. Use the resulting key in `isWaitingForMessage(name, key)`. |
| `waitsAtSignalIntermediateCatchEvent` with `receive()` | Broadcast `client.newBroadcastSignalCommand().signalName(name).send().join()` | Read `name` from the converted copy. |
| `waitsAtEventBasedGateway("G")` receiving event `"E"` | Use the corresponding message, signal, or timer action for `"E"` | Read the event type and subscription from the converted copy. |
| `waitsAtConditionalIntermediateEvent("C")` | Call `processTestContext.updateVariables(byKey(processInstanceKey), variables)` | Converted conditional events need Camunda 8.9 or later. |
| `runsCallActivity("C")` returning `Scenario.use(child)` | Deploy the converted child process and register its behaviors with `byProcessId(childProcessId)` | When the Camunda 7 test mocks the child process, the skill preserves that mocked boundary. |
| `withMockedProcess("child")` and `waitsAtMockedCallActivity("C")` | Call `processTestContext.mockChildProcess("child", variables)` | This preserves the existing mocked-child boundary. |
| `Scenario.run(process).startByKey(key, variables).execute()` | The skill creates an instance with `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(variables).send().join()` and retains the returned `ProcessInstanceEvent` | When the source test sets a business key, the skill applies the confirmed business-key mapping. |
| `startByMessage(name, variables)` | The skill correlates a message start with `.messageName(name).withoutCorrelationKey().variables(variables).send().join()` and retains the returned `CorrelateMessageResponse` | |
| `.fromBefore("A")` | Call `.startBeforeElement("A")` on the create command | |
| `.fromAfter("A")` with no clear next element | Record `manual` in the parity ledger with the reason that CPT has no direct counterpart and the next element is ambiguous | When the next element is unambiguous, the skill starts before it. |
| `startBy(customProcessStarter)` | Record `manual` in the parity ledger with the reason that CPT has no direct mapping for the custom starter | A custom `ProcessStarter` needs a manual migration. |
| `Scenario.instance(process)` after `startByKey` | The skill asserts against the `ProcessInstanceEvent` returned by the create-instance command | |
| `Scenario.instance(process)` after `startByMessage` | The skill selects the instance with `assertThatProcessInstance(byKey(correlationResponse.getProcessInstanceKey()))` | The correlate command returns a `CorrelateMessageResponse`, not a `ProcessInstanceEvent`. |
| `verify(process).hasCompleted("E")` | Assert `hasCompletedElements("E")` | |
| `verify(process, times(n)).hasCompleted("E")` | Assert `hasCompletedElement("E", n)`. | The skill preserves the exact completed-element count. |
| `verify(process).hasFinished("E")` | The skill asserts `hasCompletedElements("E")`, `hasTerminatedElements("E")`, or both | The skill asserts each outcome present on the path. `hasFinished` includes completed and canceled elements. |
| `verify(process, times(n)).hasFinished("E")` | When the completed-versus-terminated split is known, the skill asserts the completed count with `hasCompletedElement("E", completedCount)`, the terminated count with `hasTerminatedElement("E", terminatedCount)`, or both. | When all visits share an outcome, the skill uses one assertion. When the path has known mixed counts, the skill uses both assertions. When the split is unknown, the skill records `manual` in the parity ledger with the unknown completed-versus-terminated split as the reason and does not assert exact counts or their sum. |
| `verify(process).hasCanceled("E")` | Assert `hasTerminatedElements("E")` | |
| `verify(process).hasStarted("E")` | Assert the reached state with `hasActiveElements`, `hasCompletedElements`, or `hasTerminatedElements` | |
| `verify(process, never()).hasStarted("E")` | When the skill completes a waiting assertion, the skill asserts `hasNotActivatedElements("E")` | This assertion does not wait. |

### Time rule

The Scenario runner moves the clock to each due timer, one timer at a time. CPT's
`increaseTime(duration)` moves the clock once for the full duration. The skill preserves the
intermediate timer effects by increasing time in steps no longer than the shortest timer period on
the active path. When the active path contains a boundary timer, the skill asserts that the attached
activity is active. When the active path contains a timer catch event, the skill asserts that the
timer event is active. When the skill completes each time-increase step, the skill asserts the
expected timer effect.

When a Scenario stub uses `defer(period, action)` and the total time increase reaches `period`, the
skill runs the deferred action.

One valid schedule uses five 12-hour increments for a daily timer and `defer("P2DT12H", action)`.

## References

- [CPT Spring test setup and lifecycle](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
- [CPT mock job workers](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-job-workers)
- [Camunda Spring Boot Starter worker configuration](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#disable-a-job-worker)
- `code-conversion/patterns/10-general/dependencies.md`
 
## Remote-engine test migration
 
Every instruction is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD), and an option is marked (MAY).

Camunda Process Test (CPT) provides the Camunda 8 test runtime, commands, and assertions.
A remote-engine test drives a running Camunda 7 engine through Engine REST or the external-task client.
A shared-engine test calls an engine that the test does not start.
The engine is neither local nor a test-owned container.

## Scope and classification

Classify each Camunda 7 test before changing it.
Apply the rows from top to bottom. Stop at the first matching row.
The shared-engine row overrides every client-shape row below it.

| Camunda 7 test shape | Classification | Skill action |
|---|---|---|
| Load, performance, or end-to-end UI test against Camunda 7 | Out of scope | Do not migrate it in this test slice. |
| Unit test of an external-task handler that starts no engine | Out of scope | Migrate it as ordinary code. |
| WireMock or another Engine REST stub | Out of scope | Do not migrate it as a remote-engine test. |
| Test calls an engine that it does not start, and the engine is neither local nor a test-owned container | Report only | Record `manual` in the parity ledger with the shared-environment reason below. |
| Engine REST calls through RestAssured, RestTemplate, TestRestTemplate, WebClient, HTTP clients, or generated OpenAPI clients | In scope | Replace Engine REST calls with the matching CPT command or assertion. |
| Java clients that call Engine REST through a Camunda 7 service API, including `camunda-platform-7-rest-client-spring-boot` | In scope | Replace the client calls with Camunda 8 commands and assertions. |
| `org.camunda.bpm.client.ExternalTaskClient` or `@ExternalTaskSubscription` from `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | In scope | Migrate the worker and keep its process behavior in the CPT test. |
| Testcontainers image `camunda/camunda-bpm-platform` or Docker Compose setup started by the test | In scope | Remove the Camunda 7 runtime setup and use the CPT-managed runtime. |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` calling the embedded engine's `/engine-rest` | In scope | Map the REST calls. Use the Spring test harness from the Spring migration. |

For report-only tests, record this exact reason in `MIGRATION_REPORT.md`:

> CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime.

## Runtime and build changes

Use the CPT-managed Testcontainers runtime for each migrated automated test.
Remove the Camunda 7 container setup, Engine REST base URL, and credentials from migrated test configuration.
Remove Camunda 7 REST-client test dependencies when no remaining test uses them.
Remove Testcontainers from test dependencies when no remaining test uses it.
Keep Testcontainers when another test still uses it.
Add `io.camunda:camunda-process-test-java` for non-Spring tests.
Select the Spring Process Test artifact using the target project's Spring Boot version.
Add the selected Spring artifact in test scope.
Annotate each migrated JUnit test that uses plain Java with `@CamundaProcessTest` to register `CamundaProcessTestExtension`.
Annotate each migrated Spring CPT test with `@CamundaSpringProcessTest` to start the Spring Process Test harness.
The artifact dependency alone does not start the CPT runtime or inject the CPT client and context fields.

| Target Spring Boot version | CPT test artifact |
|---|---|
| Spring Boot 3.5.x | `io.camunda:camunda-process-test-spring-boot-3` |
| Spring Boot 4.x | `io.camunda:camunda-process-test-spring` |

| User request | CPT test type | Runtime action |
|---|---|---|
| No explicit request for remote mode | Spring or plain Java test | Use the CPT-managed Testcontainers runtime. Never configure remote mode. |
| Explicit request for remote mode and a dedicated local Camunda 8 runtime | Spring test | Set Spring property `camunda.process-test.runtime-mode` to `remote` in `application.properties` or `application.yml` (MAY). |
| Explicit request for remote mode and a dedicated local Camunda 8 runtime | Plain Java test | Add `src/test/resources/camunda-container-runtime.properties` with `runtimeMode=remote` (MAY). |

The remote runtime requires management API port `9600` and `zeebe.clock.controlled: true`.
CPT deletes runtime data between tests, so never point remote mode at a shared or production runtime.

## Worker behavior

Run migrated job workers for real when the Camunda 7 test ran a real external-task worker.
In a Spring Boot test, let the Spring harness start the `@JobWorker` beans.
Without Spring, open the migrated worker in `@BeforeEach` with the injected `CamundaClient`.

When the Camunda 7 test itself called `/external-task/fetchAndLock` and completed the task, no real worker ran.
Use `processTestContext.completeJob(type, variables)` or `processTestContext.mockJobWorker(type)` for that boundary.

## Waiting, timers, and variables

Replace Awaitility or `Thread.sleep` polling on engine state with CPT assertions.
Keep Awaitility only for state outside Camunda.
CPT assertions wait up to 10 seconds by default.
For a longer wait, use `CamundaAssert.setAssertionTimeout(Duration)` or `camunda.process-test.assertion.timeout`.

Assert that a timer element is active before using `processTestContext.increaseTime(duration)`.
Identify the job type before translating a Camunda 7 `POST /job/{id}/execute` call.

Replace Camunda 7 typed variable values with plain JSON values.
Update Java assertions when the JSON value changes the Java type, such as `Integer` to `Long`.

## Engine REST mapping

Prefer `CamundaClient` commands and CPT assertions over raw HTTP. (SHOULD)
Keep raw HTTP only when the test checks the Orchestration Cluster REST API contract.

| Camunda 7 Engine REST call | CPT 8.9 replacement | Notes |
|---|---|---|
| `POST /deployment/create` | `@TestDeployment(resources = "converted-c8-<name>.bpmn")` or the application's `@Deployment` | Deploy the converted copy. |
| `POST /process-definition/key/{key}/start` | `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Pass plain JSON variables. |
| `POST /message` | `client.newCorrelateMessageCommand()` or `client.newPublishMessageCommand()` | Read the name and key from the converted copy's `zeebe:subscription`. |
| `POST /signal` | `client.newBroadcastSignalCommand().signalName(name).send().join()` | Keep the converted signal name. |
| `GET /task?processInstanceId=...` then `POST /task/{id}/complete` | `processTestContext.completeUserTask(elementId, vars)` or `client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()` | Pass completion variables. Use the C8 user-task key when calling the client directly. |
| `POST /task/{id}/claim` or `/task/{id}/assignee` | `client.newAssignUserTaskCommand(userTaskKey).assignee(user).send().join()` | Preserve the assignee. |
| `POST /external-task/fetchAndLock` then `POST /external-task/{id}/complete` | `processTestContext.completeJob(type, vars)` | Use `mockJobWorker(type)` when the test needs a mock worker boundary. |
| `POST /external-task/{id}/bpmnError` | `processTestContext.throwBpmnErrorFromJob(type, code, vars)` | Preserve the BPMN error code and variables. |
| `GET /history/process-instance/{id}` with state `COMPLETED` | `assertThat(processInstance).isCompleted()` | Use the CPT process-instance assertion. |
| `GET /history/activity-instance?processInstanceId=...` | `hasCompletedElements(...)` or `hasCompletedElementsInOrder(...)` | Preserve required activity order. |
| `GET /process-instance/{id}/variables` or `GET /history/variable-instance` | `hasVariable(name, value)` or `hasVariables(map)` | Compare plain JSON values. |
| `GET /incident?processInstanceId=...` | `hasActiveIncidents()` or `hasNoActiveIncidents()` | Assert the expected incident state. |
| `POST /job/{id}/execute` for a timer job | `processTestContext.increaseTime(duration)` | Assert that the timer element is active first. |
| `POST /job/{id}/execute` for a non-timer job | No time-advancement mapping | Identify the job type and why the test executes it. Use the matching CPT worker command when it controls a worker boundary. Assert the resulting process path for an engine-managed continuation. Do not advance time. |

Use the [Camunda 7 to Camunda 8 API mapping](https://camunda.github.io/camunda-7-to-8-migration-tooling/) for calls not listed here.

## Baseline and parity reporting

Run the baseline test against its Camunda 7 engine before migration.
If the test cannot reach or start that engine, record the baseline as `not run` in `MIGRATION_REPORT.md`.
Do not claim parity from an expected result when the baseline did not run.

| Test classification | Parity ledger verdict |
|---|---|
| In-scope test whose baseline did not run | `not run` |
| Shared-engine test, whether its baseline ran or not | `manual` |

Include the exact shared-environment reason above.
Do not report a shared-engine test as an automated CPT pass.

See the [CPT 8.9 configuration](https://docs.camunda.io/docs/8.9/apis-tools/testing/configuration/), [assertions](https://docs.camunda.io/docs/8.9/apis-tools/testing/assertions/), and [utilities](https://docs.camunda.io/docs/8.9/apis-tools/testing/utilities/) documentation.
