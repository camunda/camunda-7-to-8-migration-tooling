# Spring Boot web topology fixture

`c7-source` is a Spring Boot application with an embedded Camunda 7 engine,
Engine REST API, Actuator health endpoint, and application process-start route.
The application and Engine REST API share the default port `8080`.

`expected-c8` keeps the application route and moves process start to
`CamundaClient`. The application listens on port `8081`. The external Camunda 8
cluster uses REST port `8080`. The application does not recreate or proxy
`/engine-rest`.

## Running the evaluation

1. Copy `c7-source` to a temporary project and migrate it with the
   `migrate-c7-to-c8-code` skill. Inventory both application and Engine REST
   routes, then answer Question 7. Record the application port, cluster REST
   base address, authentication mode, endpoint decisions, health dependencies,
   and every Engine REST consumer.
2. Start a Camunda 8.9.21 self-managed cluster with REST at
   `http://localhost:8080` and gRPC at `http://localhost:26500`. Confirm the
   cluster health endpoint reports healthy:

   ```bash
   curl --fail http://localhost:8080/v2/status
   ```

3. Run the expected project's test suite:

   ```bash
   mvn -f agentic-migration-skills/fixtures/spring-boot-web-topology/expected-c8/pom.xml test
   ```

   The Spring Boot test starts the application on `8081` while the cluster
   remains on `8080`. The application deploys the converted process. The test
   checks the cluster health component, starts that process through the
   application API, and confirms that `/engine-rest` returns `404`.
4. Run `CamundaClusterHealthIndicatorTest` without a cluster. It confirms that
   the health check maps `/v2/status` responses `204` and `503` to `UP` and
   `DOWN`. It also confirms that an unavailable endpoint reports `DOWN`:

   ```bash
   mvn -f agentic-migration-skills/fixtures/spring-boot-web-topology/expected-c8/pom.xml \
     -Dtest=CamundaClusterHealthIndicatorTest test
   ```

The integration test needs the cluster from step 2. A context-load test does not
replace its endpoint and process-behavior checks.
