# gRPC dependency alignment fixture

This manual regression fixture records a dependency mismatch found during an agentic migration. It
does not claim that this mismatch explains every test error in the migrated project.

## Red evidence

The target used `camunda-spring-boot-3-starter:8.9.21`. The source POM imported a Google Cloud BOM,
but Maven could not resolve it from the configured repository. The migrated POM commented out that
BOM and pinned selected Google Cloud artifacts individually.

The resulting runtime graph included:

| Artifact | Resolved version |
|---|---:|
| `io.grpc:grpc-xds` | `1.66.0` |
| `io.grpc:grpc-util` | `1.79.0` |
| `io.grpc:grpc-core` | `1.79.0` |

The focused startup trace reported an `IncompatibleClassChangeError`. An xDS weighted round-robin
load balancer could not inherit from a class that the selected gRPC util version marked as final.
This evidence proves a gRPC compatibility defect. It does not explain every test error.

## Regression walkthrough

1. Run the `migrate-c7-to-c8-code` skill on a Maven project with a Camunda Spring Boot starter and
   Google Cloud dependencies.
2. Inspect `mvn help:effective-pom` and the dependency tree before and after POM changes. Keep the
   Google Cloud BOM unless a documented dependency decision supports changing it. If Maven cannot
   resolve the BOM, record the exact coordinates and error. Check the repository configuration
   before changing the BOM.
3. Inspect the dependency paths for the Camunda starter, Google Cloud libraries, and every
   `io.grpc` artifact. Use a compatible BOM in `<dependencyManagement>` to align an incompatible
   gRPC family. Remove a direct dependency only after source, configuration, and test searches show
   that the project does not use it. Never pin one transitive artifact.
4. Add a focused test that creates the real `CamundaClient` bean without mocking it or calling a
   cluster API. A minimal Spring application context can use local addresses and no authentication:

   ```java
   import static org.junit.jupiter.api.Assertions.assertNotNull;

   import io.camunda.client.CamundaClient;
   import org.junit.jupiter.api.Test;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.boot.SpringBootConfiguration;
   import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
   import org.springframework.boot.test.context.SpringBootTest;

   @SpringBootTest(
       classes = CamundaClientStartupTest.TestApplication.class,
       properties = {
         "camunda.client.mode=self-managed",
         "camunda.client.auth.method=none",
         "camunda.client.grpc-address=http://127.0.0.1:26500",
         "camunda.client.rest-address=http://127.0.0.1:8080"
       })
   class CamundaClientStartupTest {
     @Autowired CamundaClient camundaClient;

     @Test
     void createsCamundaClient() {
       assertNotNull(camundaClient);
     }

     @SpringBootConfiguration
     @EnableAutoConfiguration
     static class TestApplication {}
   }
   ```

5. Fail migration readiness if the focused test reports a `LinkageError` or the dependency tree
   proves an incompatible family. A cluster that is unavailable during a separate API call is a
   connectivity finding, not a passing startup test.
6. Record the failing and final artifact versions, the BOM resolution error, and the selected
   remediation in `MIGRATION_REPORT.md`. Record the focused test command and exit code there.
   Keep unrelated test failures separate from the gRPC finding.
