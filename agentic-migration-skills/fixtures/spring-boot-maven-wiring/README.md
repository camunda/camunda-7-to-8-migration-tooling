# Spring Boot Maven wiring fixture

`c7-source` is a Camunda 7 Maven module with an embedded engine, no Spring Boot
entry point, and no `spring-boot-maven-plugin`. `expected-c8` is the migrated
Spring Boot deployment application. It adds the Camunda 8 starter,
`MessageStartApplication`, converted BPMN and DMN copies, and
`spring-boot-maven-plugin`. It keeps the compiler plugin.

## Running the evaluation

1. Copy `c7-source` to a temporary project and migrate it with the
   `migrate-c7-to-c8-code` skill. Ask for a runnable Spring Boot deployment
   application, so the skill creates `MessageStartApplication`. Accept
   `@Deployment` wiring.
2. Compare the result with `expected-c8`.
3. Run `mvn test` in `expected-c8`. `DeploymentResourcesTest` reads the annotation
   patterns with Spring's `PathMatchingResourcePatternResolver`. It requires each
   pattern to resolve a non-empty subset of the inventory and their union to equal
   the BPMN and DMN files. It does not start Spring or connect to a cluster.
4. Run `npx --yes dmnlint src/main/resources/converted-c8-message-decision.dmn`
   in `expected-c8`.
5. Configure a Camunda 8 connection. Confirm that `mvn spring-boot:run` launches
   `MessageStartApplication`, then stop it. Run `mvn package`. Confirm that
   `java -jar target/message-start-1.0-SNAPSHOT.jar` launches the same class,
   then stop it.
6. Negative case: migrate a second copy as a test-only module. Confirm that the
   migration adds no `@SpringBootApplication` class and no
   `spring-boot-maven-plugin` declaration.

Without a reachable cluster, startup fails after Spring Boot launches
`MessageStartApplication`. The launch in step 3 still counts as evidence that
the plugin and executable JAR work.
