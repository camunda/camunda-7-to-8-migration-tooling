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

A test is eligible for migration only when it runs a BPMN process or DMN decision on a Camunda 7 engine.
The test must also use a framework or approach that existed for Camunda 7.
A dependency alone never makes a test eligible for migration.
For example, `camunda-platform-7-mockito` provides engine-backed helpers and `DelegateExecutionFake` for plain unit tests.

The skill inventories every test method declared or inherited by each concrete test class in the scanned test source sets.
It records each test's test kind and handling, including tests marked out of scope.
Apply the table from top to bottom. The first matching row assigns one test kind and handling.
The skill classifies tests by executed engine behavior, not assertion type.
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
| 1 | out of scope (Camunda 8) | Uses Zeebe or CPT APIs without running a C7 engine. | Not part of test migration |
| 2 | manual redesign | A C7 engine test covers CMMN APIs or models, or unsupported engine internals such as `ProcessEnginePlugin`, BPMN parse listeners, custom history levels, or `ProcessEngineConfigurationImpl` internals. `ClockUtil` timer control does not trigger this signal by itself. | Report only |
| 3 | manual migration | JGiven (`io.holunda.testing:camunda-bpm-jgiven`) tests require manual migration. Cucumber scenarios use Camunda 7 APIs to run an engine-backed BPMN process or DMN decision. The Cucumber classification includes applicable hooks, not only steps. An in-scope test uses Arquillian, camunda-bpm-needle (CDI), or the Camunda 7 Quarkus extension. A test runs an engine-backed process from a BPMN model built with the Camunda 7 fluent model API. A Kotlin or Groovy test uses Camunda 7 test APIs to run an engine-backed BPMN process or DMN decision. | Report only |
| 4 | scenario test | Runs `org.camunda.bpm.scenario.*` against C7. | Migrate (lower priority) |
| 5 | remote-engine test | A test runs a BPMN process or DMN decision on a running Camunda 7 engine through Engine REST at `/engine-rest`, `org.camunda.bpm.client.*`, or Testcontainers for C7. | Report only |
| 6 | decision test | Evaluates a DMN decision on C7 through `DmnEngineRule`, `DmnEngine`, `DmnEngineConfiguration`, or `DecisionService`. | Migrate |
| 7 | process test | Runs a BPMN process on C7 through `ProcessEngineRule`, `ProcessEngineExtension` including `org.camunda.bpm.extension:camunda-bpm-junit5`, `ProcessEngineTestCase`, `BpmnAwareTests`, `ProcessEngineTests`, `AbstractProcessEngineRuleTest`, or `StandaloneInMemoryTestConfiguration`. It may call a real C7 engine's `RuntimeService` to start a process (for example, `startProcessInstanceByKey(...)`), `TaskService` to complete a task with a non-null `processInstanceId`, or `RuntimeService` to correlate a message. It may call a Spring Boot endpoint that starts a process, completes a process-backed task, or correlates a message on a real C7 engine. | Migrate to CPT only with the `Spring` modifier; otherwise Report only |
| 8 | out of scope | Does not execute a real C7 BPMN process or DMN decision. This includes Kotlin or Groovy tests that use Camunda 7 test APIs but run no process or decision, standalone tasks created with `TaskService.newTask()` without a `processInstanceId`, plain Java tests, delegate or worker unit tests, `DelegateExecutionFake`, mocked `DelegateExecution`, mocked `RuntimeService`, Spring test slices with mocked C7 APIs, or WireMock Engine REST stubs. Remote health or metadata probes that run no process or decision are also out of scope, unless the shared-engine exception in Scope confirmation applies. | Not part of test migration |

`@Deployment` is model-resolution evidence, not a test-kind signal by itself.
The skill keeps remote-engine test rows at Report only until their migration procedures are defined.
The skill keeps process test rows without the `Spring` modifier at Report only until their engine-test migration procedure is defined.

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
| BPMN model built with the Camunda fluent model API, such as `Bpmn.createExecutableProcess()` | Record the model as programmatically built and note the manual migration reason in `Notes` | Report only |

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
| Report only | Keep `Report only` | For an in-scope test, the skill preserves the existing reason and adds `test migration needs Camunda 8.9 or later`. For `manual redesign`, the skill preserves the existing reason. |
| Not part of test migration | Keep `Not part of test migration` | Keep the existing reason |

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
This includes `camunda-bpm-spring-boot-starter-test`, `camunda-bpm-junit5`, and
`camunda-bpm-assert` when only migrated tests use them.
If any test outside the migrated set or production code still uses a dependency, the skill keeps it.

The skill migrates each Spring Boot test to JUnit 5. The skill keeps `@SpringBootTest` and adds
`@CamundaSpringProcessTest`. The skill injects `CamundaClient` and `CamundaProcessTestContext` with
`@Autowired`.

| C7 Spring test | C8 CPT test |
|---|---|
| `@RunWith(SpringRunner.class) @SpringBootTest` | `@SpringBootTest @CamundaSpringProcessTest` |
| `@Autowired RuntimeService`, `TaskService`, `HistoryService`, or `ProcessEngine` | `@Autowired CamundaClient` and `CamundaProcessTestContext` |
| `@Autowired @Rule ProcessEngineRule` or `BpmnAwareTests.init(processEngine)` | The skill removes the engine rule and initialization |
| `camunda.bpm.*` test-engine properties | The skill removes them. The skill adds `camunda.process-test.*` properties only when needed |
| H2 used only by the embedded engine | The skill removes H2. The skill keeps a data source used by the application |
| The C7 test transaction reverts engine and application state | The skill keeps `@Transactional` only for application database state |

The skill uses `@MockitoBean` with Spring Boot 4. The skill uses a supported Mockito test
annotation with the selected Spring Boot 3 version. When Step 3 changes Spring Boot 3 to 4, the
skill migrates `@MockBean` annotations to `@MockitoBean`.

The skill keeps each test's class and method names when practical. (SHOULD)

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
Create `MIGRATION_REPORT.md` when it does not exist.
Add the Test Parity record when the file exists, and preserve its existing content.
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

If the application has no usable worker bootstrap, the skill sets the test's handling to
`Report only`. The skill records the manual migration reason in the Notes column of
`MIGRATION_REPORT.md`.
The skill states which bootstrap is missing and why it cannot start the workers.
The skill does not invent a new worker bootstrap.

## Camunda Platform Scenario Test Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

### Scope and target

The test inventory classifies an in-scope test method as a `scenario test` when it uses
`Scenario.run(...)` or `Scenario.use(...)` with a `ProcessScenario`.
The artifacts `org.camunda.bpm.extension.scenario:camunda-platform-scenario-runner` and
`org.camunda.bpm.extension:camunda-bpm-assert-scenario` are detection signals. Their JARs contain
overlapping classes in `org.camunda.bpm.scenario`. The skill keeps only one of these artifacts on
each test classpath. When remaining tests require both artifacts, the skill separates their
test classpaths. The skill inspects the test calls before classifying the method.

The skill migrates scenario tests to Java tests with Camunda Process Test (CPT) and
`io.camunda:camunda-process-test-java`. The target is Camunda 8.9 or later. The skill does not
create CPT instruction-based JSON tests.

When the target is Camunda 8.8, the skill sets scenario-test handling to `Report only`. It records
the exact reason `test migration needs Camunda 8.9 or later`. The parity ledger records the test as
`manual`.

The scenario runner's Cucumber module, logging, and history fast-forward reports stay out of scope.

### Prepare the test

The skill converts the shared engine-test setup before it applies the scenario-specific mappings.

| Camunda 7 engine-test setup | Camunda Process Test 8.9 or later | Notes |
|---|---|---|
| `@Rule ProcessEngineRule processEngineRule = new ProcessEngineRule()` | Add `@CamundaProcessTest` to the class. Remove the `ProcessEngineRule` field. | CPT supplies the engine-test runtime. |
| `@Deployment(resources = "source.bpmn")` | `@TestDeployment(resources = "converted-c8-source.bpmn")` | Deploy the converted C8 copy. |
| Test code needs a `CamundaClient` | Inject `CamundaProcessTestContext processTestContext`. Call `processTestContext.createClient()` when needed. | |

1. The skill reads the converted copy before mapping test behavior. It uses that copy to find element
   IDs, external job types, message names and correlation keys, and timer definitions.
2. When the test completes a user task, the skill checks that the converted copy declares
   `<zeebe:userTask />`. Without the marker, Camunda uses a job-worker implementation, so the User
   Task API has no user-task instance to complete.
3. The skill moves JUnit 3 and JUnit 4 scenario tests to JUnit 5.
4. The skill removes the `ProcessScenario` mock and Scenario runner setup.
5. The skill removes each Scenario artifact only when no remaining test uses it.
6. The skill adds `io.camunda:camunda-process-test-java` in test scope.
7. The skill keeps Mockito initialization and cleanup for retained annotations that rely on
   `MockitoAnnotations.openMocks(this)`.

The Scenario runner completed external tasks itself. The skill uses `mockJobWorker(type)` for those
tasks.
The skill does not add a mock for a Java delegate that the Camunda 7 test ran for real. The skill
keeps the migrated worker real unless the Camunda 7 test mocked that delegate.

### Wait-state behavior

The skill uses a CPT conditional behavior for each user-task, message, signal, event-gateway, or
conditional-event stub. Each condition waits for the corresponding process state. The action
resolves that state so CPT can detect it again.

The skill uses sequential CPT calls when the process path is linear. (MAY) The skill uses
`mockJobWorker(type)` for external-task stubs. The skill advances time explicitly for timer stubs.

```java
processTestContext
    .when(() -> assertThatProcessInstance(byProcessId(processId)).hasActiveElements("Review"))
    .as("Review")
    .then(() -> processTestContext.completeUserTask("Review", variables));
```

The skill preserves every existing stub. The skill does not add behavior for an unstubbed wait
state. When a process reaches an unstubbed wait state, CPT leaves it waiting. The final process
assertion fails after its timeout.

### Scenario-to-CPT mapping

| Camunda Platform Scenario | Camunda Process Test 8.9 or later | Notes |
|---|---|---|
| `@Mock ProcessScenario process` and its Scenario stubs | Remove the mock and Scenario runner setup. Convert each existing Scenario stub with the matching CPT behavior below. | Map each verification to the CPT assertion rows below. |
| `MockitoAnnotations.openMocks(this)` and matching cleanup | Remove only when no retained Mockito annotations require it | Keep initialization and cleanup for `@Mock`, `@Spy`, `@Captor`, or `@InjectMocks` fields that rely on it. |
| JUnit 4 `@Before`, `@After`, and `@Test` | JUnit 5 `@BeforeEach`, `@AfterEach`, and `@Test` | |
| `waitsAtUserTask("X")` returning `task.complete(variables)` | `when(() -> assertThatProcessInstance(byProcessId(pid)).hasActiveElements("X")).as("X").then(() -> processTestContext.completeUserTask("X", variables))` | The action completes the task tested by the condition. |
| `thenReturn(a, b)` for actions on repeated visits | Chain `.then(a).then(b)` | CPT repeats the last action after earlier actions run. |
| `task.handleBpmnError(...)` or `task.handleEscalation(...)` on a user task | Record `manual` in the parity ledger | CPT has no direct user-task BPMN error or escalation action. |
| `waitsAtServiceTask`, `waitsAtSendTask`, `waitsAtBusinessRuleTask`, `waitsAtMessageIntermediateThrowEvent`, or `waitsAtMessageEndEvent` completing an external task | `processTestContext.mockJobWorker(type).thenComplete(variables)` | Read `type` from the converted copy. |
| The same external-task stubs handling a BPMN error | `processTestContext.mockJobWorker(type).thenThrowBpmnError(code, variables)` | Read `type` from the converted copy. |
| `waitsAtTimerIntermediateEvent("T")` with an empty action | Assert `hasActiveElements("T")`, then call `processTestContext.increaseTime(duration)` | Read the duration from the converted timer definition. |
| `action.defer(period, action)` | Increase time in bounded steps, then run the deferred action | Follow the time rule below. |
| `waitsAtMessageIntermediateCatchEvent` or `waitsAtReceiveTask` with `receive(variables)` | Correlate the message with `client.newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(variables).send().join()` | Read the message name and correlation-key FEEL expression from the converted copy's `zeebe:subscription`. Evaluate the expression against the test variables. Pass the result to `correlationKey(...)`. Never pass the expression text, such as `=orderId`. Use the resulting key in `isWaitingForMessage(name, key)`. |
| `waitsAtSignalIntermediateCatchEvent` with `receive()` | Broadcast `client.newBroadcastSignalCommand().signalName(name).send().join()` | Read `name` from the converted copy. |
| `waitsAtEventBasedGateway("G")` receiving event `"E"` | Use the corresponding message, signal, or timer action for `"E"` | Read the event type and subscription from the converted copy. |
| `waitsAtConditionalIntermediateEvent("C")` | Call `processTestContext.updateVariables(byKey(processInstanceKey), variables)` | Converted conditional events need Camunda 8.9 or later. |
| `runsCallActivity("C")` returning `Scenario.use(child)` | Deploy the converted child process and register its behaviors with `byProcessId(childProcessId)` | Keep the child process behavior real unless the Camunda 7 test mocked it. |
| `withMockedProcess("child")` and `waitsAtMockedCallActivity("C")` | Call `processTestContext.mockChildProcess("child", variables)` | This preserves the existing mocked-child boundary. |
| `Scenario.run(process).startByKey(key, variables).execute()` | The skill creates an instance with `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(variables).send().join()` and retains the returned `ProcessInstanceEvent` | Apply the confirmed business-key mapping when the source test sets a business key. |
| `startByMessage(name, variables)` | The skill correlates a message start with `.messageName(name).withoutCorrelationKey().variables(variables).send().join()` and retains the returned `CorrelateMessageResponse` | |
| `.fromBefore("A")` | Call `.startBeforeElement("A")` on the create command | |
| `.fromAfter("A")` with no clear next element | Record `manual` in the parity ledger | CPT has no direct `fromAfter` counterpart. Start before the next element only when it is unambiguous. |
| `startBy(customProcessStarter)` | Record `manual` in the parity ledger | A custom `ProcessStarter` needs a manual migration. |
| `Scenario.instance(process)` after `startByKey` | The skill asserts against the `ProcessInstanceEvent` returned by the create-instance command | |
| `Scenario.instance(process)` after `startByMessage` | The skill selects the instance with `assertThatProcessInstance(byKey(correlationResponse.getProcessInstanceKey()))` | The correlate command returns a `CorrelateMessageResponse`, not a `ProcessInstanceEvent`. |
| `verify(process).hasCompleted("E")` | Assert `hasCompletedElements("E")` | |
| `verify(process).hasFinished("E")` | The skill asserts `hasCompletedElements("E")`, `hasTerminatedElements("E")`, or both | The skill asserts each outcome present on the path. `hasFinished` includes completed and canceled elements. |
| `verify(process, times(n)).hasFinished("E")` | The skill asserts exact counts with `hasCompletedElement("E", completedCount)` and `hasTerminatedElement("E", terminatedCount)` | The skill uses one assertion when all visits share an outcome. The skill uses both assertions when the path has known mixed counts. The skill records `manual` when the split is unknown. The skill verifies that the completed and terminated counts sum to `n`. |
| `verify(process).hasCanceled("E")` | Assert `hasTerminatedElements("E")` | |
| `verify(process).hasStarted("E")` | Assert the reached state with `hasActiveElements`, `hasCompletedElements`, or `hasTerminatedElements` | |
| `verify(process, never()).hasStarted("E")` | Assert `hasNotActivatedElements("E")` after a waiting assertion | This assertion does not wait. |

### Time rule

The Scenario runner moves the clock to each due timer, one timer at a time. CPT's
`increaseTime(duration)` moves the clock once for the full duration. The skill preserves the
intermediate timer effects by increasing time in steps no longer than the shortest timer period on
the active path. For a boundary timer, the skill asserts that the attached activity is active.
For a timer catch event, the skill asserts that the timer event is active. After every step, the
skill asserts the expected timer effect.

The skill runs a deferred action when the total time increase reaches its `defer(period, action)`
period. It does not run the action before that period.

For a daily timer and `defer("P2DT12H", action)`, the skill increases time by one day twice and
asserts each daily effect. It then increases time by twelve hours and runs the deferred action.

## References

- [CPT Spring test setup and lifecycle](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
- [CPT mock job workers](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-job-workers)
- [Camunda Spring Boot Starter worker configuration](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#disable-a-job-worker)
- `code-conversion/patterns/10-general/dependencies.md`
