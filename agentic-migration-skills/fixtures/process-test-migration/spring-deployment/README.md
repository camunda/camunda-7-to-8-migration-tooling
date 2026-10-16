# Spring deployment resource fixture

The Boot 3 module selects `camunda-spring-boot-3-starter:8.9.21` with Spring Boot 3.5.16.
The Boot 4 module selects `camunda-spring-boot-starter:8.9.21` with Spring Boot 4.1.1.
Both applications import `io.camunda.client.annotation.Deployment`.
The Boot 3 module selects `httpclient5:5.6.4`. Without that alignment, its CPT runtime cannot
construct the Camunda client.

`DeploymentResourcesTest` reads the application annotation and resolves each entry through
Spring's `PathMatchingResourcePatternResolver`. It parses the actual BPMN and DMN bytes by XML
namespace. It emits `CAMUNDA_DEPLOYMENT_RESOURCES` with one SHA-256 value per matched resource.
The BPMN entry matches two converted processes. The DMN entry matches one decision.
The `.form` entry matches one accepted form and records its type and SHA-256.
The test also checks that a comma-joined entry resolves to zero files. The missing-file,
original-model, and mixed-resource controls must fail the check.

Use Java 21. These compile and resolver tests do not start a Camunda runtime:

```bash
mvn test -f agentic-migration-skills/fixtures/process-test-migration/spring-deployment/pom.xml
```

The independent `ExplicitTestDeploymentIT` disables application annotation deployment.
Its `@TestDeployment` deploys the converted BPMN, DMN, and accepted form instead.
It starts the actual process, evaluates the actual decision, and asserts both results.
Run it with Docker:

```bash
mvn test -f agentic-migration-skills/fixtures/process-test-migration/spring-deployment/pom.xml \
  -Dtest=ExplicitTestDeploymentIT
```

The C7 deployment-only method remains out of scope. Neither the resolver test nor the runtime
deployment test claims C7 process-test parity for that method.
