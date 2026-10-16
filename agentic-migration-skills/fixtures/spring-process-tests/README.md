# Spring Process Test Migration

`c7-source` shows a Spring Boot 3.5 application with an embedded Camunda 7 engine.
Its tests call an HTTP endpoint, mock a delegate service and a delegate bean, and check a startup
hook.

`expected-c8` shows the same tests with Camunda Process Test 8.9.21 and Spring Boot 4.1.1.
The application uses `camunda-spring-boot-starter` and the tests use
`camunda-process-test-spring`.
This matches the existing `worker-input-bindings` fixture's Boot 4 pairing.

The endpoint test keeps its `MockMvc` call and waits for the asynchronous workers with CPT
assertions. It mocks the payment service and the `ship-order` job type. The test disables the real
`ship-order` worker to prevent two handlers from racing for the same job.
The migrated endpoint preserves the C7 `202 Accepted` response with an empty body.
A second endpoint returns a computed total in a nonempty `200 OK` response.
Both the C7 and CPT tests call `/api/orders/total` and assert the status and total in the response.

The test application is in a sibling package outside the production component-scan root. It scans
only the required controllers, services, and workers. Its `@Deployment` annotation names the
converted copies.
`SpringProcessTest` uses `@BeforeEach` to repeat the startup hook because CPT deletes runtime data after each test.
`PostDeploymentOrderTest` invokes the migrated `CamundaPostDeploymentEvent` listener in
`@BeforeEach`. It checks the instance key recorded by that listener after the real worker completes.
It also polls secondary-storage search for that key. The search wait requires a completed result,
not an empty search or completion of another instance.

`RollbackProcessTest` is an intentional semantic-delta case.
The C7 failure test asserts zero inventory and payment writes after its third delegate fails.
The green CPT test observes two committed worker writes and an incident instead.
`expected-c8/MIGRATION_REPORT.md` marks rollback parity `NOT READY` without user approval.
A green CPT suite does not erase the original database expectation.

The `manual-without-bootstrap` case deploys a process that reaches `ManualProcessWorker` and asserts
its execution. The application has no reusable C8 worker bootstrap, so the expected report marks
the test as `Report only` and records the manual migration reason.

The `standalone-task-only` case creates and completes a task without a BPMN process instance.
The expected report marks it out of scope because task completion alone does not prove process execution.

Run the Spring process-test fixture suite from the repository root:

```sh
python3 agentic-migration-skills/fixtures/spring-process-tests/test_spring_process_tests.py
```

The suite also runs a Maven test for the manual C7 fixture. It requires JDK 17 or later,
Maven, and resolvable fixture dependencies.

Run the C7 failure and response assertions with Java 21:

```sh
mvn -f agentic-migration-skills/fixtures/spring-process-tests/c7-source/pom.xml test
```

Run the CPT tests with Java 21 and Docker:

```sh
mvn -f agentic-migration-skills/fixtures/spring-process-tests/expected-c8/pom.xml test
```

The deterministic empty-active-completed search tests do not need Docker:

```sh
mvn -f agentic-migration-skills/fixtures/spring-process-tests/expected-c8/pom.xml \
  -Dtest=ProcessInstanceSearchWaiterTest test
```
