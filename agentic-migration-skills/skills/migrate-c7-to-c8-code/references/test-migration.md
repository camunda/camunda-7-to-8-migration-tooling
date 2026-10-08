# Test Migration

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked
(SHOULD) and an option is marked (MAY).

This reference uses C7 for Camunda 7, C8 for Camunda 8, and CPT for Camunda Process Test.

## Test Inventory

The skill detects C7 tests during Step 2 for every migration scope, including Assessment only and code approach C.

A **shared-engine test** reads a shared Camunda 7 engine URL from any configuration source and calls
that engine. The test does not start the engine, and the engine is neither local nor a test-owned
container.

| Runs a BPMN process or DMN decision on a Camunda 7 engine | Uses a framework or approach that existed for Camunda 7 | Is a shared-engine test | Scope decision |
|---|---|---|---|
| Yes | Yes | Any | When both prerequisites are met, the skill includes the test in scope. |
| No | Any | Yes | The skill includes the test in scope as `remote-engine test` with `Report only` handling. |
| No | Any | No | The skill excludes the test from scope. |
| Yes | No | Any | If a test does not use a framework or approach that existed for Camunda 7, then the skill excludes the test from scope. |

A dependency alone never makes a test eligible for migration.
When production code or an unmigrated test uses a dependency, the skill keeps it.
For example, `camunda-platform-7-mockito` provides engine-backed helpers and `DelegateExecutionFake` for plain unit tests.
The skill inventories CMMN tests and tests that use unsupported engine internals as `manual redesign` with `Report only` handling, although they do not meet this scope rule.

The skill inventories every test method declared or inherited by each concrete test class in the scanned test source sets.
It records each test's test kind and handling, including tests marked out of scope.
The skill classifies tests by executed engine behavior, not assertion type.
When a real C7 process or decision test asserts only endpoint responses or downstream side effects, the skill keeps the test in scope.
The skill classifies an embedded Engine REST call from a `@SpringBootTest` as a remote-engine test, not a process test.
The skill records assertion gaps in the Test Inventory's Notes column for migration review.
The skill verifies that a direct service call resolves to a real C7 engine in the test or its
shared configuration.
Test rules, extensions, dependencies, and API references alone do not prove that a test executed a BPMN process or DMN decision.
Calls to mocks, fakes, or stubs do not count as engine execution.
The skill requires a completed task's `processInstanceId` to identify an executed BPMN process before
`TaskService.complete(...)` is a process-test signal.
`TaskService.newTask()` without a process instance is not a process-test signal.
When methods in one class differ, the skill classifies each method separately.

## Candidate discovery

When the skill scans a configured test source set, it looks for these test inputs:

| Candidate source | Discovery signal |
|---|---|
| JUnit 3 | `ProcessEngineTestCase` and a `test*` method |
| Cucumber | A configured Cucumber runner or build configuration and `.feature` files |
| Spock | A Groovy test class that extends `spock.lang.Specification` |
| Spring endpoint | A test that calls an application endpoint backed by a C7 engine |
| Direct decision service | A test that calls `DecisionService` |

When the skill identifies a candidate, it records a Test Inventory row before it assigns a test kind.
When the skill assigns a test kind, it uses the Test kinds table and evidence of executed engine behavior.
When a discovered test has `Report only` or out-of-scope handling, the skill keeps its Test Inventory row.

## Test kinds

Apply the table from top to bottom. The first matching row assigns one test kind and handling.

| Priority | Test kind | Detect by | Handling |
|---|---|---|---|
| 1 | out of scope (Camunda 8) | Uses Zeebe Process Test (`io.camunda.zeebe.process.test.*`) or CPT (`io.camunda.process.test.*`). | Not part of test migration |
| 2 | manual redesign | A C7 engine test covers CMMN APIs or models, or unsupported engine internals such as `ProcessEnginePlugin`, BPMN parse listeners, custom history levels, or `ProcessEngineConfigurationImpl` internals. A CMMN or engine-internals test that runs no BPMN process or DMN decision also gets this classification. `ClockUtil` is a supported test utility. `ClockUtil` timer control does not trigger this signal by itself. | Report only |
| 3 | manual migration | A JGiven (`io.holunda.testing:camunda-bpm-jgiven`) test executes a real C7 process or decision and requires manual migration. Cucumber scenarios use Camunda 7 APIs to run an engine-backed BPMN process or DMN decision. The Cucumber classification includes applicable hooks, not only steps. An in-scope test uses Arquillian, camunda-bpm-needle (CDI), or the Camunda 7 Quarkus extension. A test runs an engine-backed process from a BPMN model built with the Camunda 7 fluent model API. A Kotlin or Groovy test uses Camunda 7 test APIs to run an engine-backed BPMN process or DMN decision. | Report only |
| 4 | scenario test | Runs `org.camunda.bpm.scenario.*` against C7. | Migrate (lower priority) |
| 5 | remote-engine test | A test runs a BPMN process or DMN decision on a C7 engine through Engine REST (`/engine-rest`), a service-API REST client such as `camunda-platform-7-rest-client-spring-boot`, `org.camunda.bpm.client.*` or `@ExternalTaskSubscription`. The test may start the C7 engine with a Testcontainers image or Docker Compose. A shared-engine test is a remote-engine test whether or not it runs a process or decision. [Handling overrides](#handling-overrides) sets its handling. | Migrate (lower priority) |
| 6 | decision test | Evaluates a DMN decision on C7 through `DmnEngineRule`, `DmnEngine`, `DmnEngineConfiguration`, or `DecisionService`. | Migrate |
| 7 | process test | Runs a BPMN process on C7 through `ProcessEngineRule`, `ProcessEngineExtension` including `org.camunda.bpm.extension:camunda-bpm-junit5`, `ProcessEngineTestCase`, `BpmnAwareTests`, `ProcessEngineTests`, `AbstractProcessEngineRuleTest`, or `StandaloneInMemoryTestConfiguration`. It may call a real C7 engine's `RuntimeService` to start a process (for example, `startProcessInstanceByKey(...)`), `TaskService` to complete a task with a non-null `processInstanceId`, or `RuntimeService` to correlate a message. It may call a Spring Boot endpoint that starts a process, completes a process-backed task, or correlates a message on a real C7 engine. | Migrate to CPT |
| 8 | out of scope | Does not execute a real C7 BPMN process or DMN decision. This includes Kotlin or Groovy tests that use Camunda 7 test APIs but run no process or decision, standalone tasks created with `TaskService.newTask()` without a `processInstanceId`, plain Java tests, delegate or worker unit tests, `DelegateExecutionFake`, mocked `DelegateExecution`, mocked `RuntimeService`, Spring test slices with mocked C7 APIs, or tests that use WireMock or another Engine REST stub. The skill classifies every other test that runs no process or decision as out of scope, including a test that only deploys a model. | Not part of test migration |

When a JGiven test does not execute a real C7 process or decision, the skill assigns the out-of-scope test kind.
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

When the skill asks Question 8, the skill includes the DMN runtime notice from `interview-questions.md`.

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
| `@Test`, `@ParameterizedTest`, `@RepeatedTest`, or a JUnit 3 test method | The method runs a BPMN process or DMN decision on a Camunda 7 engine |
| `@SpringBootTest` | The embedded engine starts a process, completes a task, correlates a message, or handles an endpoint that does one |
| A Cucumber `Scenario` or `Scenario Outline` data row | Its step definitions or applicable hooks run a BPMN process or DMN decision on a Camunda 7 engine |
| `@Deployment`, a Camunda 7 dependency, or a test class name | Not sufficient without an engine-backed process or decision |

## Modifiers

The skill records every applicable modifier for every in-scope test row, including scenario, remote-engine, and manual-migration tests.

| Modifier | Detect by | Used by |
|---|---|---|
| mocks | Mockito mocks of `org.camunda.bpm.scenario.ProcessScenario` in scenario tests, `org.camunda.bpm.engine.test.mock.Mocks`, `MockExpressionManager`, `org.camunda.community.mockito.*`, `org.camunda.bpm.extension.mockito.*`, holunda `c7-mockito`, or Mockito mocks registered as Spring beans called by the process | Mock migration subtask |
| coverage | `org.camunda.community.process_test_coverage.*`, `org.camunda.bpm.extension.process_test_coverage.*`, or holunda `c7-process-test-coverage` | Baseline and parity subtask |
| time | `ClockUtil`, `ManagementService.executeJob(...)`, job queries for timers, or `task.defer(period, action)` in Scenario stubs | Engine test support subtask |
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

The skill records the source language of a Kotlin or Groovy test in the `Notes` cell.

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

When `MIGRATION_REPORT.md` does not exist, the skill creates it.
Add a `Test Inventory` table under a `## Test Inventory` heading in `MIGRATION_REPORT.md`, with these
columns in this order:

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|

Keep the Test Inventory table as the only table in its section. Start every table row with `|`. The validator reads every table row
between the `Test Inventory` heading and the next heading as an inventory row.
Add a `Test kind counts` table under its own heading in `MIGRATION_REPORT.md` with the columns
`Test kind` and `Count`.
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
| A shared-engine test | Report only | Record the exact shared-engine reason below. Where the target is Camunda 8.8, append `test migration needs Camunda 8.9 or later` to that reason. |

When a test uses a shared engine, the skill records this exact reason in `MIGRATION_REPORT.md`:

> CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime.

The skill records a shared-engine test as `manual` in the parity ledger.

## Camunda 8.8 target

When the target version is Camunda 8.8, the skill detects every test.

| Default handling | Camunda 8.8 handling | Reason |
|---|---|---|
| Migrate | Report only | `test migration needs Camunda 8.9 or later` |
| Migrate to CPT | Report only | `test migration needs Camunda 8.9 or later` |
| Migrate (lower priority) | Report only | `test migration needs Camunda 8.9 or later` |
| Report only | Keep `Report only` | Where a test is in scope, the skill preserves the existing reason and adds `test migration needs Camunda 8.9 or later`. Where the test kind is `manual redesign`, the skill preserves the existing reason. |
| Not part of test migration | Keep `Not part of test migration` | Keep the existing reason |

## Test Execution Choice

A migratable test is a Test Inventory test with handling `Migrate`, `Migrate to CPT`, or
`Migrate (lower priority)`.

Ask Question 8 from `references/interview-questions.md` after the Step 2 Test Inventory is complete.
The conditions for asking it are in that file.

When the user selects Assessment only and the inventory includes a migratable test, explain both
Question 8 options in `MIGRATION_REPORT.md`. Do not ask Question 8 or record a test run mode.

The skill runs `docker info` before the options and states whether it succeeds.
If no test command was found for a module, then the skill does not invent one.
If `docker info` fails, then the skill still offers both options.

| User choice | Baseline step | Step 3 and Step 4 | Test verification |
|---|---|---|---|
| **Run tests** | Run each Camunda 7 test command before Step 3 changes any file. Record the baseline. | Migrate the tests, run each CPT test command, and apply the test safeguards. | `verified`, or `blocked` with the reason |
| **Migrate tests only** | Preserve the C7 baseline as described below. Do not run a test command. | Migrate the tests as in the Run tests path. Compile test sources. Do not run C7 suites, CPT suites, or Step 4 process scenarios. | `not verified (Migrate tests only)` |

When the user selects **Migrate tests only**, preserve the C7 baseline before Step 3:

| Project root | Baseline action |
|---|---|
| Git repository with a clean working tree | Record the Step 2 commit. |
| Git repository with uncommitted changes, or not a Git repository | Copy the full project root, including hidden files, to a sibling directory. Record the snapshot path in `MIGRATION_REPORT.md`. |

If the skill cannot preserve the baseline, then ask the user before Step 3.

When the user selects **Migrate tests only**, apply these rules:

- Compile each module's test sources with `mvn test-compile` or the Gradle `testClasses` task. A
  main-source-only compile does not count.
- Where a non-test check needs packaging, package with `-DskipTests` (Maven) or `-x test` (Gradle).
  Record why in `MIGRATION_REPORT.md`.
- Record each module `tests` check and each process `process_path` check with the `block` action and
  the exact reason `declined by user (Question 8)`.
- The validation gate reports `NOT READY`. The project-readiness verdict is `needs review`, as
  defined in `references/project-readiness.md`.

Record the Question 8 answer in the `MIGRATION_REPORT.md` decision log. Also record it as
`test_run_mode` in `.camunda-migration/validation/step2-inventory.json`, as described in
`references/validation-evidence.md`.
Where the Test Inventory has a migratable test, the validator requires `test_run_mode`.

## Step 3 order

When `test_run_mode` is `run`, use these phases in this order:

| Phase | Action |
|---|---|
| C7 baseline | Run each suite containing a migratable test or a `Report only` test selected for migration before Step 3 changes any file. |
| Models | Convert the model copies, including test models. |
| Tests | Migrate the in-scope tests. Review recipe changes before accepting them. |
| Freeze | Record hashes for test source files and test resources. |
| Production code | Migrate production code. This phase ends after every frozen test passes. |

When the selected mode is `migrate_only`, do not run a test command. Follow the declined-test path
in `validation-evidence.md`.

Keep the Test Inventory in `MIGRATION_REPORT.md`, and keep it unchanged after Step 2. Record the
machine-readable test mode and suite commands in `.camunda-migration/validation/step2-inventory.json`
as `validation-evidence.md` defines. Record CPT mappings in `test-mapping.json`.
Where Question 8 does not apply, the skill omits `test_run_mode`.

## Camunda 7 baseline

Run each suite that contains a migratable test with the `c7_baseline` check in
`validation-evidence.md`, before Step 3 changes any file. A failed or skipped C7 test remains visible
in the ledger. A C7 test that did not pass is not required to pass parity.

If a suite cannot run, then record it as blocked and ask the user whether to continue without that
baseline. Record the approval in `test-mapping.json`. Do not invent test results. Without a C7
baseline, report parity as `not verified`. The validation gate remains `NOT READY`.

## Test parity ledger

The validator owns `.camunda-migration/validation/test-mapping.json`. Never write `c7_result` by hand.
The skill records each test mapping and each approval in the ledger shape that
`validation-evidence.md` defines.

Use these ledger statuses:

| Status | Meaning |
|---|---|
| `migrated` | The C7 test maps to one or more CPT tests. |
| `retired` | The user approved removal of the C7 test. Record a reason and approver. |
| `manual` | The Test Inventory marks the test `Report only`. The test is not verified. |
| `added` | The migration added a CPT test without a C7 source test. |

The validator requires the same repeat, parity, freeze, and review evidence for migrated `Report only` tests.
The validator requires a C7 baseline for every suite that contains a migrated or retired `Report only` test.

When a `Report only` test passed in the C7 baseline, its `manual` status does not satisfy parity.
Migrate it or record an approved retirement before claiming `READY`.

Set `retirement.reason` and `retirement.approved_by` on each retired test. The validator rejects a
retired test without both values.

Set `c8_ids` on each added test. Set `suite` to the name of the module test suite that runs it. The
validator requires `test_repeat` only for that suite and requires each added CPT test to pass in both
runs.
Keep `c8_ids` distinct within each ledger row. Never assign one CPT ID to multiple migrated or
added test rows.

## Freeze migrated tests

When test migration completes, run the `test_freeze` check in `validation-evidence.md`.

While the skill migrates production code, it does not edit a frozen test file. Ask the user before a test file must
change. Record each approved change with its path, reason, old hash, new hash, and approver:

```json
{
  "file": "examples/web/src/test/java/com/example/OrderCptTest.java",
  "reason": "The user approved a required assertion update.",
  "old_hash": "sha256:<old hash>",
  "new_hash": "sha256:<new hash>",
  "approved_by": "operator"
}
```

When the change adds a file, use `null` for the old hash. When the change removes a file, use
`null` for the new hash. The validator rejects each changed hash without a matching approval.

## CPT repeat and parity checks

Run the `test_repeat` check for each migrated suite.
Record one `assertion_strength` review per migrated test class.
Record one `mock_boundary` review per migrated C7 test.
When every suite's repeat runs and all reviews pass, record the project-level `test_parity` check.
When the CPT suites have run, record the project-level `coverage_parity` check.
`validation-evidence.md` defines each command.

For each `assertion_strength` review, compare each C7 assertion with its CPT assertion. Keep equal or stronger assertions. If a CPT test cannot retain an assertion, then record a reason.

The `mock_boundary` review applies the [Mock boundary](#mock-boundary) rules to the mocks in the
[Parity ledger](#parity-ledger).

## Verification Plan for a Deferred Test Run

When the user selects **Migrate tests only**, add a **Verify the test migration** section to
`MIGRATION_REPORT.md`. Use the actual baseline commit or snapshot path, and the test commands from
the Test Inventory, project documentation, and CI inventory:

1. Run the Camunda 7 suite from the baseline. Where the baseline is a commit, create a separate
   worktree with `git worktree add ../c7-baseline <baseline-commit>` and run the module test
   command there. Where the baseline is a snapshot, run the command in the snapshot directory.
2. Start Docker or configure a remote CPT runtime. Run the migrated suite, for example `mvn test`.
3. Change only `test_run_mode` from `migrate_only` to `run` in the Step 2 inventory. Keep the Test
   Inventory and `test_suites` unchanged. Do not run `init`, because it clears earlier checks.
4. Record each check that the validator `report` action lists as missing. The validator cannot run
   a `c7_baseline` check after Step 3 changes files. Record each `c7_baseline` check with the
   `block` action. Name the baseline run from step 1 in the reason.
5. Run the validator `report` action again. The gate stays `NOT READY` without a C7 baseline that
   the validator captured.

The verification plan is not test evidence.

When the user later asks the skill to verify a **Migrate tests only** run, follow this plan. (MAY)
Never rebuild the C7 baseline from migrated code.

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
| 8.8 | Apply the [Camunda 8.8 target](#camunda-88-target) table. Do not change test dependencies. |

Most CPT assertions wait for an expected state for up to 10 seconds by default.
`hasNotActivatedElements(...)` evaluates the current process state immediately and does not wait.
The skill uses `hasNoActiveElements(...)`, `isNotWaitingForMessage(...)`, and
`hasNotActivatedElements(...)` to inspect the current state.
The skill establishes the observation point with a positive waiting or terminal assertion before it
uses a current-state absence check.
If the skill does not establish that point first, then the negative assertion can pass before the
process reaches it.
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
| `execute(job())` or `managementService.executeJob(id)` for a timer | `processTestContext.increaseTime(Duration)` | When the active path contains a timer catch event, the skill asserts that the timer catch event is active before increasing time. When the active path contains a boundary timer, the skill asserts that the attached activity is active instead. CPT does not expose a boundary timer as an active element. |
| `ClockUtil.setCurrentTime(date)` or `ClockUtil.reset()` | `processTestContext.setTime(instant)` | CPT resets the clock after each test. |
| `runtimeService.correlateMessage(name, businessKey, vars)` | `client.newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(vars).send().join()` | Get `key` from the converted model's message subscription, not the business key. |
| `runtimeService.signalEventReceived(name)` | `client.newBroadcastSignalCommand().signalName(name).send().join()` | |
| `historyService` or `runtimeService` queries used as assertions | CPT assertions | The skill uses a waiting CPT assertion for asynchronous behavior. It establishes the observation point before it checks absence. Search requests are eventually consistent. |
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
| Spring Boot 4.x with `camunda-spring-boot-starter` | `io.camunda:camunda-process-test-spring` | `@SpringBootTest` and `@CamundaSpringProcessTest` |
| Spring Boot 3.5.x with `camunda-spring-boot-3-starter` | `io.camunda:camunda-process-test-spring-boot-3` | `@SpringBootTest` and `@CamundaSpringProcessTest` |
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

Follow [Mock boundary](#mock-boundary) and [C7 mock API mapping](#c7-mock-api-mapping).

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

See the [Parity ledger](#parity-ledger) for the authoritative record and approval contract.
Do not maintain a separate approval table in `MIGRATION_REPORT.md`.

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

When the target is Camunda 8.8, the skill applies the [Camunda 8.8 target](#camunda-88-target) table
and records the test as `manual` in the parity ledger.

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
| `verify(process, never()).hasStarted("E")` | The skill asserts `hasNotActivatedElements("E")` only after a positive waiting assertion establishes the observation point. | This assertion evaluates immediately and does not wait. |

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

## Mock boundary

When a Test Inventory row records the `mocks` modifier and its `Handling` value instructs
migration, the skill applies these mappings. A `mocks` modifier alone does not qualify a
`Report only` test for migration.

The skill treats the APIs of `camunda-platform-7-mockito` (`org.camunda.community.mockito`),
`io.holunda.c7:c7-mockito`, and `camunda-bpm-mockito` (`org.camunda.bpm.extension.mockito`) as
the same C7 mock API.

The skill identifies what each C7 mock replaced before it chooses a CPT mock:

| C7 mock replaced | CPT replacement | Boundary rule |
|---|---|---|
| A whole delegate, so no project code ran for that task | `processTestContext.mockJobWorker(type)` | Read `type` from the task's `zeebe:taskDefinition/@type` in the converted copy. |
| A whole execution listener, so no project code ran for that listener | `processTestContext.mockJobWorker(type)` | Read `type` from the converted copy's `zeebe:executionListener/@type`. Do not use the attached task's `zeebe:taskDefinition/@type`. |
| A whole user-task listener, so no project code ran for that listener | Call `processTestContext.completeJobOfUserTaskListener(JobSelectors.byJobType(type), result -> {})` once for every matching listener-job activation that the C7 test handles. | Read `type` from the matching `zeebe:taskListener/@type` in the converted copy. Do not use a `zeebe:taskDefinition/@type`. |
| An expression target or a collaborator called by a real delegate or worker | Run the real worker and inject the same Mockito mock into its collaborator. | Do not mock the worker. |
| A called process | `processTestContext.mockChildProcess(processId, output)` | Preserve the called process ID and output variables. |
| A C7 process-flow test already mocks a business-rule task | `processTestContext.mockDmnDecision(decisionId, output)` | Preserve the decision ID and the result shape established by the C7 business-rule mapping. This existing C7 decision mock is a same-boundary migration and needs no additional approval. |
| A C7 process-flow test does not mock a business-rule task | No CPT decision mock by default | Ask the user before adding a CPT decision mock. Record an approved addition in `mock_changes`. |
| No component; project code ran for the task | No CPT mock | Keep the corresponding C8 worker real. Ask the user before changing this boundary. |

Never derive a job type from a C7 bean name. If the converted copy has no matching job type, then do
not invent one. If the converted copy omits a C7 listener, then the skill records that mock in `mocks.c7`.
The skill leaves `mocks.c8` without a corresponding mock.
If the real worker cannot run, then the skill asks the user before it adds a mock.
The skill registers CPT job mocks before the test starts a process.
A whole-component mock must not start the real C8 worker for the mocked component.

## C7 mock API mapping

| C7 test code | CPT test code | Required behavior |
|---|---|---|
| `Mocks.register("bean", mock)` for a `camunda:expression` target or collaborator | Keep the real mapped worker and inject the same Mockito mock into the expression service. | Treat the expression service as a collaborator. Do not call `mockJobWorker(type)`. |
| `Mocks.register("delegate", mock)` for a whole `camunda:delegateExpression` | `mockJobWorker(type)` | Use the converted task's job type. |
| `doAnswer(...)` on a whole delegate with fixed outputs | `.thenComplete(outputs)` and `getActivatedJobs()` | Preserve every output variable. Read the input variables from the activated job. Keep the invocation verification. |
| `doAnswer(...)` on a whole delegate with input-dependent outputs | `.withHandler(handler)` | Read the activation variables and complete the job with the matching outputs. |
| `CamundaMockito.registerMockInstance(...)` | Apply the same-boundary table | Classify the registered object. Do not infer its boundary from the helper name. |
| `@MockBean` or `@MockitoBean` for a process-used delegate or listener | Apply the matching whole-component row in the [mock boundary](#mock-boundary) table | Preserve the whole-component mock boundary. |
| `@MockBean` or `@MockitoBean` for a service called by a delegate | `@MockitoBean` or the version-compatible Spring mock for the same service | Keep the real worker enabled. |
| `registerJavaDelegateMock("delegate")` | `mockJobWorker(type).thenComplete()` | The whole delegate was mocked. |
| `.onExecutionSetVariables(vars)` or `.onExecutionSetVariable(key, value)` | `.thenComplete(vars)` | Preserve every output variable. |
| `.onExecutionSetVariables(vars1, vars2)` for repeated calls | `.withHandler(handler)` that completes each activation with its matching result | Preserve the order and value of each result. |
| `.onExecutionThrowBpmnError(code, message)` | `.thenThrowBpmnError(code, message, Map.of())` or `.thenThrowBpmnError(code)` | When the test checks a BPMN error code or message, the skill preserves it. |
| `.onExecutionThrowException(exception)` | `.withHandler(...)` that fails the job with zero retries | Assert the resulting active incident instead of expecting a synchronous exception. |
| `autoMock("process.bpmn")` | When the C7 helper mocks a delegate expression, the skill uses `mockJobWorker(type)` for its converted service-task or execution-listener type. The skill applies mock registrations in source order. The last registration for a bean sets the effective boundary, so `autoMock` can replace an earlier concrete registration. The skill does not infer mocks from `camunda:class` or `camunda:expression`. When the C7 test mocks either boundary separately, the skill maps it. For each retained user-task listener, the skill calls `completeJobOfUserTaskListener(...)` once for every matching activation. | Read each `type` from its own extension declaration in the converted copy. |
| `registerExecutionListenerMock("listener")` | `mockJobWorker(type)` for the listener's job type | Read `type` from the converted copy's `zeebe:executionListener/@type`. Do not use the attached task's `zeebe:taskDefinition/@type`. |
| `registerTaskListenerMock("listener")` | Where the converted copy retains a listener job, the skill calls `completeJobOfUserTaskListener(JobSelectors.byJobType(type), result -> {})` once for every matching listener-job activation. | Read `type` from the matching `zeebe:taskListener/@type`. Record a dropped C7 listener in `mocks.c7` and leave `mocks.c8` without a corresponding mock. |
| `registerCallActivityMock("child").onExecutionSetVariables(vars)` | `mockChildProcess("child", vars)` | When outputs depend on parent variables, the skill uses the function overload. |
| A call-activity mock using `onExecutionWaitForMessage`, `onExecutionWaitForTimerWithDuration`, `onExecutionSendMessage`, `onExecutionRunIntoError`, or `onExecutionDo` | No direct counterpart | Deploy the real converted child or ask the user to approve a test-only child model. |
| `verifyJavaDelegateMock("name")` or `verifyExecutionListenerMock("name")` with `executed()`, `executed(times(n))`, or `executedNever()` | `assertThat(mock.getInvocations())` with `isEqualTo(1)`, `isEqualTo(n)`, or `isZero()` | Read the count only after a waiting CPT assertion on the related element. |
| `verifyTaskListenerMock("name").executed()` | Increment an `AtomicInteger` in the `completeJobOfUserTaskListener` result callback. Assert the count is `1`. | Read the count only after a waiting CPT assertion on the related task or process. |
| `verifyTaskListenerMock("name").executed(times(n))` | Increment an `AtomicInteger` in each matching listener-job result callback. Assert the count is `n`. | Call `completeJobOfUserTaskListener(...)` once for every matching listener-job activation. Read the count only after a waiting CPT assertion on the related task or process. |
| `verifyTaskListenerMock("name").executedNever()` | Do not complete a matching listener job. | Assert that the same CPT checkpoint succeeds without a matching blocking listener job. If no waiting assertion proves the absence, then ask the user before claiming parity. |
| `ArgumentCaptor<DelegateExecution>` on a delegate mock | `mock.getActivatedJobs()` and `job.getVariablesAsMap()` | Read the activated job after a waiting CPT assertion. |
| `Mocks.reset()` or `@After` engine-mock cleanup | Remove the engine-mock cleanup | CPT resets runtime data after each test. |

`withHandler` can complete a job with a selected output map. It can also fail a job:

```java
processTestContext.mockJobWorker("notify").withHandler((jobClient, job) ->
    jobClient.newFailCommand(job).retries(0).errorMessage("notify failed").send().join());
```

The CPT mock reports invocations and activated jobs without waiting. Mockito `verify` also does not
wait. Place a waiting CPT assertion on the related element before reading either mock.
When no suitable waiting CPT assertion exists, the skill uses `verify(mock, timeout(...))`.

## Real workers and Spring

Where migrated workers are Spring beans, the skill uses `@SpringBootTest` with
`@CamundaSpringProcessTest` and `@MockitoBean` for a mocked collaborator. (SHOULD) This also applies
to a C7 test with no Spring context. Use a minimal `TestProcessApplication` in another package.
Scan only the worker packages.

Where a Spring test mocks a job type, the test disables its real worker:

```properties
camunda.client.worker.override.<job type>.enabled=false
```

Add one override for every mocked job type. Otherwise, the real worker and mock can handle the same
job.

When a C7 test mocks an expression service or a service used by a delegate or worker, the skill checks the mapped C8 worker.
Where the mapped worker is not a Spring bean, the skill opens that worker in `@BeforeEach`.
The test uses the injected CPT client:

```java
client.newWorker().jobType(type).handler(handler).open();
```

Where the mapped worker is a Spring bean, CPT starts it through the Spring process application's
client-created event.
The skill does not open a second worker.

CPT closes its injected client after each test. Closing the client also closes workers that the test
opened through it.

## Parity ledger

Record the C7 mocks and CPT mocks for each mapped test in the parity ledger's `mocks` field. Keep
both `c7` and `c8` arrays.

When a side has no mocks, the skill records an empty array for that side:

```json
{
  "mocks": {
    "c7": ["Mocks.register(\"invoiceService\", mock)"],
    "c8": ["@MockitoBean InvoiceService"]
  }
}
```

The mock-boundary review reads these arrays. Do not write a passing review result by hand.

If the CPT test adds a mock that the C7 test did not use, then the skill asks the user for approval.
The skill records each approved addition in `mock_changes` with `cpt_test_id`, `mock`, `reason`, and
`approved_by`.
Without approval, the mock-boundary review fails.

## Build cleanup

| Asset | Remaining use | Action |
|---|---|---|
| C7 mock library | A remaining test uses the library. | Keep the dependency. |
| C7 mock library | No remaining test uses the library. | Remove the dependency. |
| `camunda.cfg.xml` | A remaining C7 test uses the file. | Keep the file and its required `MockExpressionManager` settings. |
| `camunda.cfg.xml` | No remaining C7 test uses the file. | Delete the file and its `MockExpressionManager` settings. Do not retain it for CPT tests. |

When migrated tests still use Mockito, the skill keeps Mockito.

## References

- [CPT Spring test setup and lifecycle](https://docs.camunda.io/docs/apis-tools/testing/getting-started/)
- [CPT mock job workers](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-job-workers)
- [Camunda Spring Boot Starter worker configuration](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#disable-a-job-worker)
- `code-conversion/patterns/10-general/dependencies.md`

## Remote-engine test migration

This section covers each `remote-engine test` from the [Test kinds](#test-kinds) table.

## Scope and classification

Classify each Camunda 7 test with the [Test kinds](#test-kinds) table before changing it.
A test that the table classifies as manual migration, manual redesign, or out of scope keeps that
classification and handling.
A shared-engine test keeps its [handling override](#handling-overrides).
Apply the [Camunda 8.8 target](#camunda-88-target) table before any client-shape row.
Apply the rows below from top to bottom. Stop at the first matching row.

| Camunda 7 test shape | Classification | Skill action |
|---|---|---|
| Load, performance, or end-to-end UI test against Camunda 7 | Out of scope | Do not migrate it in this test slice. |
| Unit test of an external-task handler that starts no engine | Out of scope | Migrate it as ordinary code. |
| WireMock or another Engine REST stub | Out of scope | Do not migrate it as a remote-engine test. |
| Engine REST calls through RestAssured, RestTemplate, TestRestTemplate, WebClient, HTTP clients, or generated OpenAPI clients | In scope | Replace Engine REST calls with the matching CPT command or assertion. |
| Java clients that call Engine REST through a Camunda 7 service API, including `camunda-platform-7-rest-client-spring-boot` | In scope | Replace the client calls with Camunda 8 commands and assertions. |
| `org.camunda.bpm.client.ExternalTaskClient` or `@ExternalTaskSubscription` from `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | In scope | Migrate the worker and keep its process behavior in the CPT test. |
| Testcontainers image `camunda/camunda-bpm-platform` or Docker Compose setup started by the test | In scope | Remove the Camunda 7 runtime setup and use the CPT-managed runtime. |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` calling the embedded engine's `/engine-rest` | In scope | Map the REST calls. Use the Spring test harness from the Spring migration. |

## Runtime and build changes

Use the CPT-managed Testcontainers runtime for each migrated automated test.
Remove the Camunda 7 container setup, Engine REST base URL, and credentials from migrated test configuration.
When no remaining test uses a Camunda 7 REST-client dependency, the skill removes that dependency.
When no remaining test uses Testcontainers, the skill removes Testcontainers from test dependencies.
When another test still uses Testcontainers, the skill keeps the test dependency.
Add `io.camunda:camunda-process-test-java` in test scope for non-Spring tests.
Select the Spring Process Test artifact from the [Spring harness table](#harness-and-dependencies).
Add the selected Spring artifact in test scope.
Annotate each migrated JUnit test that uses plain Java with `@CamundaProcessTest` to register `CamundaProcessTestExtension`.
Annotate each migrated Spring CPT test with `@CamundaSpringProcessTest` to start the Spring Process Test harness.
The artifact dependency alone does not start the CPT runtime or inject the CPT client and context fields.

| User request | CPT test type | Runtime action |
|---|---|---|
| No explicit request for remote mode | Spring or plain Java test | Use the default runtime. Never configure remote mode. |
| Explicit request for remote mode and a dedicated local Camunda 8 runtime | Spring test | Set Spring property `camunda.process-test.runtime-mode` to `remote` in `application.properties` or `application.yml` (MAY). |
| Explicit request for remote mode and a dedicated local Camunda 8 runtime | Plain Java test | Add `src/test/resources/camunda-container-runtime.properties` with `runtimeMode=remote` (MAY). |

The remote runtime requires management API port `9600` and `zeebe.clock.controlled: true`.
CPT deletes runtime data between tests, so never point remote mode at a shared or production runtime.

## Worker behavior

When the Camunda 7 test ran a real external-task worker, the skill runs the migrated job worker for real.
In a Spring Boot test, let the Spring harness start the `@JobWorker` beans.
Without Spring, open the migrated worker in `@BeforeEach` with the injected `CamundaClient`.
Store each returned `JobWorker` in a field.
Close each stored `JobWorker` in `@AfterEach`.

When the Camunda 7 test itself called `/external-task/fetchAndLock` and completed the task, no real worker ran.
Use `processTestContext.completeJob(type, variables)` or `processTestContext.mockJobWorker(type).thenComplete(variables)` for that boundary.

## Waiting, timers, and variables

Replace Awaitility or `Thread.sleep` polling on engine state with CPT assertions.
Keep Awaitility only for state outside Camunda.
When the default assertion timeout is too short, the skill sets `CamundaAssert.setAssertionTimeout(Duration)` or `camunda.process-test.assertion.timeout`.

Identify the job type before translating a Camunda 7 `POST /job/{id}/execute` call. The
[Engine REST mapping](#engine-rest-mapping) defines the timer and non-timer cases.

Replace Camunda 7 typed variable values with plain JSON values.
When a JSON value changes the Java type, the skill updates the Java assertions. For example, the skill changes `Integer` to `Long`.

## Engine REST mapping

Prefer `CamundaClient` commands and CPT assertions over raw HTTP. (SHOULD)

When a test checks the Orchestration Cluster REST API contract, the skill keeps raw HTTP. (SHOULD)

| Camunda 7 Engine REST call | CPT 8.9 replacement | Notes |
|---|---|---|
| `POST /deployment/create` | `@TestDeployment(resources = "converted-c8-<name>.bpmn")` or the application's `@Deployment` | Deploy the converted copy. |
| `POST /process-definition/key/{key}/start` | `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Pass plain JSON variables. |
| `POST /message` | `client.newCorrelateMessageCommand()` or `client.newPublishMessageCommand()` | Read the name and key from the converted copy's `zeebe:subscription`. |
| `POST /signal` | `client.newBroadcastSignalCommand().signalName(name).send().join()` | Keep the converted signal name. |
| `GET /task?processInstanceId=...` then `POST /task/{id}/complete` | `processTestContext.completeUserTask(elementId, vars)` or `client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()` | Pass completion variables. When the test calls the client directly, the skill uses the C8 user-task key. |
| `POST /task/{id}/claim` or `/task/{id}/assignee` | `client.newAssignUserTaskCommand(userTaskKey).assignee(user).send().join()` | Preserve the assignee. |
| `POST /external-task/fetchAndLock` then `POST /external-task/{id}/complete` | `processTestContext.completeJob(type, vars)` | When the test needs a mock worker boundary, the skill uses `mockJobWorker(type).thenComplete(vars)`. |
| `POST /external-task/{id}/bpmnError` | `processTestContext.throwBpmnErrorFromJob(type, code, vars)` | Preserve the BPMN error code and variables. |
| `GET /history/process-instance/{id}` with state `COMPLETED` | `assertThat(processInstance).isCompleted()` | Use the CPT process-instance assertion. |
| `GET /history/activity-instance?processInstanceId=...` | `hasCompletedElements(...)` or `hasCompletedElementsInOrder(...)` | Preserve required activity order. |
| `GET /process-instance/{id}/variables` or `GET /history/variable-instance` | `hasVariable(name, value)` or `hasVariables(map)` | Compare plain JSON values. |
| `GET /incident?processInstanceId=...` | `hasActiveIncidents()` or `hasNoActiveIncidents()` | Assert the expected incident state. |
| `POST /job/{id}/execute` for a timer job | `processTestContext.increaseTime(duration)` | When the active path contains a timer catch event, assert that the timer catch event is active first. When the active path contains a boundary timer, assert that the attached activity is active instead. CPT does not expose a boundary timer as an active element. |
| `POST /job/{id}/execute` for a non-timer job | No time-advancement mapping | The skill identifies the job type and why the test executes it. When the test controls a worker boundary, the skill uses the matching CPT worker command. The skill asserts the resulting process path for an engine-managed continuation. The skill does not advance time. |

Use the [Camunda 7 to Camunda 8 API mapping](https://camunda.github.io/camunda-7-to-8-migration-tooling/) for calls not listed here.

## Baseline and parity reporting

Run the baseline test against its Camunda 7 engine before migration.
If the test cannot reach or start that engine, then record the baseline as `not run` in `MIGRATION_REPORT.md`.
When the baseline did not run, the skill does not claim parity from an expected result.

| Test classification | Parity ledger verdict |
|---|---|
| In-scope test whose baseline did not run | `not run` |
| Shared-engine test, whether its baseline ran or not | `manual` |

Include the exact shared-engine reason from [Handling overrides](#handling-overrides).
Do not report a shared-engine test as an automated CPT pass.

See the [CPT 8.9 configuration](https://docs.camunda.io/docs/8.9/apis-tools/testing/configuration/), [assertions](https://docs.camunda.io/docs/8.9/apis-tools/testing/assertions/), and [utilities](https://docs.camunda.io/docs/8.9/apis-tools/testing/utilities/) documentation.
