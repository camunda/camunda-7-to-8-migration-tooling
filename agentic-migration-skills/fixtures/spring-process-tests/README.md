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

The test application is in a separate package. Its `@Deployment` annotation names the converted
copies. Its `@BeforeEach` method starts the process from the application hook again because CPT
deletes runtime data after each test.

The `manual-without-bootstrap` case shows a Spring test with no reusable C8 worker bootstrap. Its
expected report marks the test as manual migration and gives the reason.

Run the static fixture checks from the repository root:

```sh
python3 agentic-migration-skills/fixtures/spring-process-tests/test_spring_process_tests.py
```

Run the CPT tests with Java 21 and Docker:

```sh
mvn -f agentic-migration-skills/fixtures/spring-process-tests/expected-c8/pom.xml test
```
