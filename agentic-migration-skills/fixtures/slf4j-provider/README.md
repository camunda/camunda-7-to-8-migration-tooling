# SLF4J provider fixture

`c7-source` is a runnable Camunda 7 Spring Boot module. Its Spring Boot starter
provides Logback. `expected-c8` is a migrated runtime module with a Camunda 8
client and the SLF4J API, but no SLF4J provider.

## Regression walkthrough

1. Copy `c7-source` to a temporary project and run the
   `migrate-c7-to-c8-code` skill. Select code migration and target Camunda 8.9.
2. Run the skill's Step 4 validation against `expected-c8` as the migrated runtime
   module. Do not change its POM or approve a logging exception before the skill
   reports the gap.
3. From the repository root, inspect the runtime dependencies:

   ```sh
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     dependency:tree -Dscope=runtime
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     dependency:build-classpath -Dmdep.includeScope=runtime \
     -Dmdep.outputFile=target/runtime-classpath.txt
   ```

   Start the application:

   ```sh
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     spring-boot:run
   ```

   After Spring Boot reports that the application has started, stop the
   `spring-boot:run` process with `Ctrl+C`.

   Then package and run the executable JAR:

   ```sh
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml package
   java -jar \
     agentic-migration-skills/fixtures/slf4j-provider/expected-c8/target/slf4j-provider-gap-1.0-SNAPSHOT.jar
   ```

   Confirm that the runtime tree has `slf4j-api` and no compatible provider.
   The diagnostic calls `LoggerFactory.getILoggerFactory()` with that classpath.
4. Confirm that the skill records the missing provider before it reports
   migration completion. It must keep logging and startup readiness out of
   **PASS** and leave the finding open.

The fixture has no Camunda 8 cluster dependency. Its application starts without
a cluster, so a zero exit code does not prove that logging is ready. The
diagnostic prints `org.slf4j.helpers.NOPLoggerFactory` when no provider exists.
After the failure-path check, add one compatible provider and rerun the
validation to test the passing path.
