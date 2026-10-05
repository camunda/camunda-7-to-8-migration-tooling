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
| out of scope (Camunda 8) | Zeebe Process Test (`io.camunda.zeebe.process.test.*`) or CPT (`io.camunda.process.test.*) | Not part of test migration |
