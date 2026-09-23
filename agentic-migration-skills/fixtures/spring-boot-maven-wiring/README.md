# Spring Boot Maven wiring fixture

`c7-source` is a Camunda 7 Maven module with an embedded engine, no Spring Boot
entry point, and no `spring-boot-maven-plugin`. `expected-c8` is the migrated
Spring Boot deployment application. It adds the Camunda 8 starter,
`MessageStartApplication`, a `converted-c8-*` BPMN copy, and
`spring-boot-maven-plugin`. It keeps the compiler plugin.

## Running the evaluation

1. Copy `c7-source` to a temporary project and migrate it with the
   `migrate-c7-to-c8-code` skill. Accept `@Deployment` wiring.
2. Compare the result with `expected-c8`.
3. Configure a Camunda 8 connection. Run `mvn spring-boot:run`, then
   `mvn package` and `java -jar target/message-start-1.0-SNAPSHOT.jar`. Both
   commands start `MessageStartApplication`.
4. Negative case: migrate a second copy as a test-only module. Confirm that it
   has no `@SpringBootApplication` class and no `spring-boot-maven-plugin`.

Without a reachable cluster, startup fails after Maven resolves the plugin.
That failure does not replace the plugin and executable-JAR checks.
