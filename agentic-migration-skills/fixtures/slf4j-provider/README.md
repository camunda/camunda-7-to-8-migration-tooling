# SLF4J provider fixture

`c7-source` is a runnable Camunda 7 Spring Boot module. Its Spring Boot starter
provides Logback. `expected-c8` is a migrated runtime module with a Camunda 8
client and the SLF4J API. Its POM declares Logback with test scope by default.
The `with-runtime-provider` profile changes Logback to runtime scope.

## Regression walkthrough

1. Copy `c7-source` to a temporary project and run the
   `migrate-c7-to-c8-code` skill. Select code migration and target Camunda 8.9.
2. Run the skill's SLF4J provider validation against `expected-c8` as the
   migrated runtime module. Do not change its POM before the skill reports the
   gap. Do not approve a logging exception before the skill reports the gap.
   The skill leaves the finding open. It does not report logging or startup
   readiness as **PASS**.
3. From the repository root, compare the ignored Maven property with the runtime
   property:

   ```sh
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     dependency:tree -Dscope=runtime
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     dependency:build-classpath -Dmdep.includeScope=runtime \
     -Dmdep.outputFile=target/test-classpath.txt
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     dependency:build-classpath -DincludeScope=runtime \
     -Dmdep.outputFile=target/runtime-classpath.txt
   ```

   The old property includes Logback because the plugin uses its default test
   scope. The runtime classpath includes `slf4j-api` and excludes Logback. The
   runtime dependency tree shows the same provider-free graph.

4. Package and run the fixture without the profile:

   ```sh
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml package
   jar tf \
     agentic-migration-skills/fixtures/slf4j-provider/expected-c8/target/slf4j-provider-gap-1.0-SNAPSHOT.jar
   java -jar \
     agentic-migration-skills/fixtures/slf4j-provider/expected-c8/target/slf4j-provider-gap-1.0-SNAPSHOT.jar
   ```

   Confirm that the packaged JAR contains `slf4j-api` but no
   `BOOT-INF/lib/logback-classic-*.jar`. The diagnostic must print
   `org.slf4j.helpers.NOPLoggerFactory`.

5. Add a runtime provider and run the green control:

   ```sh
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     -Pwith-runtime-provider dependency:tree -Dscope=runtime
   mvn -f agentic-migration-skills/fixtures/slf4j-provider/expected-c8/pom.xml \
     -Pwith-runtime-provider clean package
   jar tf \
     agentic-migration-skills/fixtures/slf4j-provider/expected-c8/target/slf4j-provider-gap-1.0-SNAPSHOT.jar
   java -jar \
     agentic-migration-skills/fixtures/slf4j-provider/expected-c8/target/slf4j-provider-gap-1.0-SNAPSHOT.jar
   ```

   Confirm that the packaged runtime graph has exactly one compatible provider.
   The diagnostic must print `ch.qos.logback.classic.LoggerContext`, not
   `org.slf4j.helpers.NOPLoggerFactory`.

The fixture has no Camunda 8 cluster dependency. Its application starts without
a cluster. A zero exit code does not prove that logging is ready. Run the fixture
assertions from the repository root with
`python3 agentic-migration-skills/fixtures/slf4j-provider/test_slf4j_provider_scope.py`.
