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

The skill inventories every test method in the scanned test source sets.
It records each test's test kind and handling, including tests marked out of scope.
Apply the table from top to bottom. The first matching row assigns one test kind and handling.
A test method or its setup must execute and assert behavior against a real C7 process or decision engine.
Class-level annotations, dependency presence, and API references alone do not prove engine execution.
Calls to mocks, fakes, or stubs do not count as engine execution.
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
| 2 | out of scope | Does not assert process or decision behavior from a real C7 engine. This includes plain Java tests, delegate or worker unit tests, `DelegateExecutionFake`, mocked `DelegateExecution`, mocked `RuntimeService`, Spring test slices with mocked C7 APIs, or WireMock Engine REST stubs. | Not part of test migration |
| 3 | manual redesign | A C7 engine test covers CMMN, `ProcessEnginePlugin`, BPMN parse listeners, custom history levels, or `ProcessEngineConfigurationImpl` internals. | Report only |
| 4 | manual migration | A test drives a C7 engine through BDD or Cucumber layers, Arquillian, `camunda-bpm-needle`, the C7 Quarkus extension, or Kotlin or Groovy tests that use C7 test APIs. | Report only |
| 5 | scenario test | Runs `org.camunda.bpm.scenario.*` against C7. | Migrate to CPT after process and decision tests |
| 6 | remote-engine test | Asserts real C7 engine behavior through Engine REST at `/engine-rest`, `org.camunda.bpm.client.*`, or Testcontainers for C7. | Migrate to CPT after process and decision tests |
| 7 | decision test | Evaluates a DMN decision on C7 through `DmnEngineRule`, `DmnEngine`, `DmnEngineConfiguration`, or `DecisionService`. | Migrate to CPT |
| 8 | process test | Asserts BPMN process execution on C7 through `ProcessEngineRule`, `ProcessEngineExtension` including `org.camunda.bpm.extension:camunda-bpm-junit5`, `ProcessEngineTestCase`, `BpmnAwareTests`, `ProcessEngineTests`, `AbstractProcessEngineRuleTest`, `StandaloneInMemoryTestConfiguration`, or a Spring Boot test that starts a process, completes a task, correlates a message, or calls an endpoint that does. | Migrate to CPT |

`@Deployment` is model-resolution evidence, not a test-kind signal by itself.

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

## Spring Test Migration

## Scope

The skill applies Spring test migration only to process or decision test rows with the `Spring` modifier and handling `Migrate to CPT`.

| Spring evidence | Test Inventory treatment |
|---|---|
| `@SpringBootTest` with a process or decision test | Record the `Spring` modifier. |
| `@RunWith(SpringRunner.class)`, `@RunWith(SpringJUnit4ClassRunner.class)`, or `@ExtendWith(SpringExtension.class)` with a process or decision test | Record the `Spring` modifier. |
| `@ContextConfiguration` loads Spring XML or Java configuration with `SpringProcessEngineConfiguration` or `ProcessEngineFactoryBean` | Record the `Spring` modifier. |
| `@Autowired @Rule ProcessEngineRule`, `@Autowired RuntimeService`, or `BpmnAwareTests.init(processEngine)` | Record the `Spring` modifier. |
| `AbstractProcessEngineRuleTest` or `StandaloneInMemoryTestConfiguration` without a Spring context | Record the test kind without the `Spring` modifier. |
| `@WebMvcTest`, `@DataJpaTest`, or another Spring test slice without asserted execution against a real C7 engine | Out of scope. |
| A shared engine in a WAR or `processes.xml` application-server deployment | Manual migration. |

The skill does not classify every `@SpringBootTest` as a process test. The skill uses the Test
Inventory kind and modifier.

The engine-test migration covers tests without a Spring context, even when they use
`camunda-bpm-spring-boot-starter-test`.

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

The skill uses the dependency catalog for artifact versions and dependency removal. The skill does
not choose a different starter for tests than the production starter.

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
endpoint. The endpoint starts the process through `CamundaClient`.

C8 workers run asynchronously after the endpoint returns. The skill uses waiting CPT assertions
after the endpoint response. The skill wraps asynchronous Mockito `verify` calls with a timeout.

## Startup hooks and transaction state

CPT runs `@PostConstruct` methods and `CommandLineRunner` callbacks once per Spring context. CPT
deletes runtime data after each test.

When an application hook starts a process, deploys resources, or sends a message, the skill adds a
minimal `TestProcessApplication`. The test app uses a package separate from the production
application. The test app sets `scanBasePackages` to the required controllers, services, and workers.
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

When the test needs Spring-managed beans, the skill keeps `@ContextConfiguration` and adds
`@ExtendWith(SpringExtension.class)` alongside `@CamundaProcessTest`.

If the application has no usable worker bootstrap, then the skill reports the test as **manual
migration** in `MIGRATION_REPORT.md`. The skill states which bootstrap is missing and why it cannot
start the workers. The skill does not invent a new worker bootstrap.

## References

- [CPT Spring test setup and lifecycle](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
- [CPT mock job workers](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-job-workers)
- [Camunda Spring Boot Starter worker configuration](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#disable-a-job-worker)
- `code-conversion/patterns/10-general/dependencies.md`
