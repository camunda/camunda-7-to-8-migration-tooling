# Spring Boot Maven wiring fixture

This fixture covers a Camunda 7 Maven module that does not use Spring Boot and
becomes a Camunda 8 Spring Boot deployment application during migration.

```text
c7-source/
  pom.xml
  src/main/resources/message-start.bpmn
expected-c8/
  pom.xml
  src/main/java/org/camunda/bpm/example/event/message/MessageStartApplication.java
  src/main/resources/converted-c8-message-start.bpmn
```

The source POM declares the embedded Camunda 7 engine and a compiler plugin.
It does not declare a Spring Boot parent, dependency, application class, or
Maven application plugin. The expected copy adds the Camunda 8 Spring Boot 3
starter, a `@SpringBootApplication` entry point, `@Deployment` for the
converted BPMN, and `spring-boot-maven-plugin`.

## Running the evaluation

1. Copy `c7-source` to a temporary project and confirm that it has no
   `spring-boot-maven-plugin` and no Spring Boot entry point.
2. Migrate the temporary project with the `migrate-c7-to-c8-code` skill.
3. Compare the migrated project with `expected-c8`. Keep the original BPMN
   unchanged and use a `converted-c8-*` copy for deployment.
4. Configure the Camunda 8 connection required by the selected starter.
5. Run `mvn spring-boot:run` from the migrated project. Confirm that Maven
   resolves the plugin and starts `MessageStartApplication`.
6. Run `mvn package` and inspect the JAR under `target/`. Confirm that it
   contains the Spring Boot loader and `MessageStartApplication`.
7. Run `java -jar target/message-start-1.0-SNAPSHOT.jar`. Confirm that the
   packaged application selects the same entry point and deploys the converted
   resource.

The start commands need a reachable Camunda 8 cluster because `@Deployment`
executes during application startup. A cluster connection failure does not
replace the Maven plugin and executable-JAR checks. Record the exact
environment blocker in `MIGRATION_REPORT.md` when the cluster is unavailable.

The source copy is also the negative case. A module that remains test-only or
has no runtime Spring Boot entry point must not receive the application class
or `spring-boot-maven-plugin`.
