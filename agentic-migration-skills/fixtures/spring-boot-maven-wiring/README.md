# Spring Boot Maven wiring fixture

`c7-source` is a Camunda 7 Maven module with an embedded engine, no Spring Boot
entry point, and no `spring-boot-maven-plugin`. `expected-c8` is the migrated
Spring Boot deployment application. It adds the Camunda 8 starter,
`MessageStartApplication`, a `converted-c8-*` BPMN copy, and
`spring-boot-maven-plugin`. It keeps the compiler plugin.

## Running the evaluation

1. Copy `c7-source` to a temporary project and migrate it with the
   `migrate-c7-to-c8-code` skill. Ask for a runnable Spring Boot deployment
   application, so the skill creates `MessageStartApplication`. Accept
   `@Deployment` wiring.
2. Compare the result with `expected-c8`.
3. Configure a Camunda 8 connection. Confirm that `mvn spring-boot:run` launches
   `MessageStartApplication`. Run `mvn package`. Confirm that
   `java -jar target/message-start-1.0-SNAPSHOT.jar` launches the same class.
4. Negative case: migrate a second copy as a test-only module. Confirm that the
   migration adds no `@SpringBootApplication` class and no
   `spring-boot-maven-plugin` declaration.

Without a reachable cluster, startup fails after Spring Boot launches
`MessageStartApplication`. The launch in step 3 still counts as evidence that
the plugin and executable JAR work.
