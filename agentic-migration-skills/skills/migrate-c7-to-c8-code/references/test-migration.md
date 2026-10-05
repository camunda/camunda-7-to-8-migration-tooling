# Camunda 7 Test Inventory

## Scope rule

A test is in scope only when it runs a BPMN process or DMN decision on a Camunda 7 engine.
The test must also use a framework or approach that existed for Camunda 7.
A dependency alone never makes a test in scope.
For example, `camunda-platform-7-mockito` provides engine-backed helpers and `DelegateExecutionFake` for plain unit tests.

The skill inventories every test method, including tests that are out of scope.
The skill assigns one test kind to each test method.
When methods in one class differ, assign the test kind per method.

## Test kinds

| Test kind | Detect by | Handling |
|---|---|---|
| process test | `ProcessEngineRule` (JUnit 4), `ProcessEngineExtension` (JUnit 5, including `org.camunda.bpm.extension:camunda-bpm-junit5`), `ProcessEngineTestCase` (JUnit 3), `@org.camunda.bpm.engine.test.Deployment`, `BpmnAwareTests` or `ProcessEngineTests` from camunda-bpm-assert, `AbstractProcessEngineRuleTest`, `StandaloneInMemoryTestConfiguration`, or an embedded engine used by a `@SpringBootTest` | Migrate |
| decision test | `DmnEngineRule`, `DmnEngine`, `DmnEngineConfiguration`, or `DecisionService` used by a test | Migrate |
| scenario test | `org.camunda.bpm.scenario.*` from camunda-platform-scenario | Migrate (lower priority) |
| remote-engine test | A test calls a running Camunda 7 engine through Engine REST (`/engine-rest`), `org.camunda.bpm.client.*`, or a Camunda 7 Testcontainers image | Migrate (lower priority) |
| manual migration | BDD frameworks that drive the engine through JGiven (`io.holunda.testing:camunda-bpm-jgiven`) or Cucumber steps that call Camunda 7 APIs. Also includes Arquillian, camunda-bpm-needle (CDI), and Quarkus tests with the Camunda 7 Quarkus extension. It includes Kotlin and Groovy tests that use Camunda 7 test APIs. | Report only |
| manual redesign | CMMN APIs or models, such as `CmmnAwareTests` or `CaseService`, or engine internals, such as `ProcessEnginePlugin`, BPMN parse listeners, custom history levels, or `ProcessEngineConfigurationImpl` internals | Report only |
| out of scope | Unit tests that run no engine, including delegate, listener, external task worker, or service tests with `DelegateExecutionFake`, `mock(DelegateExecution.class)`, or a Mockito `RuntimeService`. Also include WireMock stubs of Engine REST and plain Java tests. | Not part of test migration |
| out of scope (Camunda 8) | Zeebe Process Test (`io.camunda.zeebe.process.test.*`) or CPT (`io.camunda.process.test.*`) | Not part of test migration |

## Scope confirmation

| Signal | Confirmation required |
|---|---|
| `@Test`, `@ParameterizedTest`, `@RepeatedTest`, or a JUnit 3 test method | The method runs a BPMN process or DMN decision on a Camunda 7 engine |
| `@Deployment` | The method runs a process or decision. The annotation alone is not enough |
| `@SpringBootTest` | The embedded engine starts a process, completes a task, correlates a message, or handles an endpoint that does one |
| A Camunda 7 dependency or a test class name | Not sufficient without an engine-backed process or decision |

When a Kotlin or Groovy test uses Camunda 7 test APIs, record its source language in `Notes`.

When one method matches multiple test kinds, assign the first matching kind in this order:

| Order | Matching signal | Test kind |
|---|---|---|
| 1 | The method uses a Camunda 8 test API | out of scope (Camunda 8) |
| 2 | The method tests CMMN or engine internals | manual redesign |
| 3 | The method uses a BDD framework, Arquillian, camunda-bpm-needle, the Camunda 7 Quarkus extension, or Kotlin/Groovy with Camunda 7 test APIs | manual migration |
| 4 | The method uses camunda-platform-scenario | scenario test |
| 5 | The method calls a running Camunda 7 engine remotely | remote-engine test |
| 6 | The method directly evaluates a DMN decision | decision test |
| 7 | The method runs a BPMN process | process test |
| 8 | The method runs no engine-backed process or decision | out of scope |

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
The skill reads shared test bases, abstract test classes, test configuration classes, and `camunda.cfg.xml` under test resources.
When a shared base or configuration supplies an engine signal, apply it to each affected test method.
The skill includes methods annotated with `@Test`, `@ParameterizedTest`, or `@RepeatedTest`.
The skill also includes JUnit 3 `public void test...()` methods.

If a Kotlin or Groovy test source uses Camunda 7 test APIs, then report it for manual migration.
Record the source language in the `Notes` cell.

## Test models

Every model deployed by a test appears in the Model Inventory, including CMMN and models under `src/test/resources`.
Link each test ID to every model it deploys in the `Models` cell.
Record the path resolved for every implicit deployment.

| Deployment form | Model paths and notes to record | Handling |
|---|---|
| Explicit `@Deployment(resources = ...)` | Every declared resource path | Use the test kind's handling |
| Implicit method-level `@Deployment` | The first matching `<package path>/<TestClass>.<method>.<suffix>` resource | Use the test kind's handling |
| Implicit class-level `@Deployment` | The first matching `<package path>/<DeclaringClass>.<suffix>` resource, including a superclass that declares the annotation | Use the test kind's handling |
| Programmatic deployment | Every resource added through `repositoryService.createDeployment().addClasspathResource(...)` | Use the test kind's handling |
| Spring Boot auto-deployment | Models deployed by `@EnableProcessApplication` with `META-INF/processes.xml`, or by the starter's auto-deployment of `src/main/resources` | Use the test kind's handling |
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

Use `<module path>:<fully qualified class name>#<method>` as the stable Test ID.
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
| Report only | Keep `Report only` | Keep the existing reason |
| Not part of test migration | Keep `Not part of test migration` | Keep the existing reason |
