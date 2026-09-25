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
3. Run `mvn package` in `expected-c8`. This runs `DeploymentResourcesTest`, which
   resolves the annotation patterns with Spring's
   `PathMatchingResourcePatternResolver`. Each pattern must match a non-empty
   subset of the test classpath inventory. Their union must equal the BPMN and
   DMN files. This test does not inspect the packaged JAR or connect to a cluster.
4. From `spring-boot-maven-wiring`, run
   `python3 -m unittest -v test_verify_packaged_resources.py`, then run
   `python3 verify_packaged_resources.py --jar expected-c8/target/message-start-1.0-SNAPSHOT.jar`.
   The script checks the BPMN and DMN entries in the executable JAR.
5. Run `npx --yes dmnlint src/main/resources/converted-c8-message-decision.dmn`
   in `expected-c8`.
6. Configure a Camunda 8 connection. Confirm that `mvn spring-boot:run` launches
   `MessageStartApplication`, then stop it. Confirm that
   `java -jar target/message-start-1.0-SNAPSHOT.jar` launches the same class,
   then stop it.
7. Negative case: migrate a second copy as a test-only module. Confirm that the
   migration adds no `@SpringBootApplication` class and no
   `spring-boot-maven-plugin` declaration.

Without a reachable cluster, startup fails after Spring Boot launches
`MessageStartApplication`. The launch in step 6 still counts as evidence that
the plugin and executable JAR work.
