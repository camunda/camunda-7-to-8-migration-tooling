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

The test application is in a sibling package outside the production component-scan root. It scans
only the required controllers, services, and workers. Its `@Deployment` annotation names the
converted copies.
`SpringProcessTest` uses `@BeforeEach` to repeat the startup hook because CPT deletes runtime data after each test.

The `manual-without-bootstrap` case deploys a process that reaches `ManualProcessWorker` and asserts
its execution. The application has no reusable C8 worker bootstrap, so the expected report marks
the test as `Report only` and records the manual migration reason.

The `standalone-task-only` case creates and completes a task without a BPMN process instance.
The expected report marks it out of scope because task completion alone does not prove process execution.

Run the static fixture checks from the repository root:

```sh
python3 agentic-migration-skills/fixtures/spring-process-tests/test_spring_process_tests.py
```

Run the CPT tests with Java 21 and Docker:

```sh
mvn -f agentic-migration-skills/fixtures/spring-process-tests/expected-c8/pom.xml test
```
