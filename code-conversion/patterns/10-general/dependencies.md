# Maven dependency and configuration

As part of the code migration, remove all Camunda 7 dependencies. Import the **Camunda Spring SDK**:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-spring-boot-starter</artifactId>
	<version>{version}</version>
</dependency>
```

Also, configure your connection to the Camunda 8 cluster in the `application.properties` or `application.yaml`.

**Spring Boot version**: Select the starter from the [Camunda Spring Boot version compatibility matrix](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/getting-started/#version-compatibility). For Camunda 8.9, `camunda-spring-boot-3-starter` is for Spring Boot 3.5.x. `camunda-spring-boot-starter` is bundled with Spring Boot 4.0.x and supports Spring Boot 4.1.x from 8.9.12:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-spring-boot-3-starter</artifactId>
	<version>{version}</version>
</dependency>
```

**BOM alignment**: Preserve existing parent dependency management and imported BOMs unless
inspection supports an explicit change. Inspect `mvn help:effective-pom -Dverbose` and
`mvn dependency:tree -Dverbose` for the Camunda starter and existing cloud libraries before changing
dependency management. Trace each imported BOM's resolved version to the POM or property that
supplies it. If a BOM does not resolve, record its coordinates and the exact Maven error. Check its
coordinates, inherited version source, and configured repositories before replacing or removing it.
Never comment out an unresolved BOM and replace selected managed artifacts with isolated version
pins.

For gRPC, inspect every resolved `io.grpc` artifact. Manage an incompatible gRPC family through a
compatible `io.grpc:grpc-bom` in `<dependencyManagement>`. Never pin only `grpc-xds`, `grpc-util`,
or `grpc-core`. Remove a conflicting direct dependency only after source, configuration, and test
searches show that the project does not use it. Record before-and-after resolved versions and the
remediation in `MIGRATION_REPORT.md`.

**Startup validation**: When a project uses a Camunda Spring Boot starter, boot an application context
that creates the real `CamundaClient` bean. Do not mock the bean or issue an API command in this
focused test. The test does not require a reachable cluster. If the focused test fails or cannot run,
block readiness and record its command, exit code, and error. Classify the failure as a classpath
incompatibility only when it reports a `LinkageError` or the resolved dependency graph proves an
incompatible family. A cluster connection failure during an API command is separate evidence and
never proves that the classpath is compatible.

**Version resolution**: Resolve the latest released GA version from Maven Central's direct artifact metadata, for example `https://repo.maven.apache.org/maven2/io/camunda/<artifact-id>/maven-metadata.xml` (the equivalent `repo1.maven.org` path is also available). From `<versions>`, select the highest version matching the target Camunda minor (`8.8.x`, `8.9.x`, etc.) and exclude `-SNAPSHOT`, `-alpha`, `-beta`, and `-rc` versions. If no GA version exists for the target, ask before using a pre-release. Do not use `search.maven.org`'s search API or the Camunda public repository metadata for this lookup.

**Java client artifact**: Use `io.camunda:camunda-client-java`. The legacy `io.camunda:zeebe-client-java` artifact is deprecated and will be discontinued in Camunda 8.10.

**Process test artifact**: Camunda 8.10 removes Zeebe Process Test. Replace `zeebe-process-test-extension` and `zeebe-process-test-extension-testcontainer` with `io.camunda:camunda-process-test-java`:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-process-test-java</artifactId>
	<version>{version}</version>
	<scope>test</scope>
</dependency>
```

For Spring Boot applications, use `camunda-process-test-spring` with the Spring Boot 4 starter or `camunda-process-test-spring-boot-3` with `camunda-spring-boot-3-starter`. The former `spring-boot-starter-camunda-test` and `spring-boot-starter-camunda-test-testcontainer` artifacts are replaced by these CPT Spring modules.

If the project uses the temporary `camunda-process-test-spring-4` or `camunda-process-test-spring-boot-4` artifact names from Camunda 8.8, replace them with `camunda-process-test-spring`.

**Logging backend**: When removing Camunda 7 webapp/rest starters, keep an SLF4J binding. If those starters were your only logging source, add `org.springframework.boot:spring-boot-starter-logging` (or another SLF4J backend) so startup failures remain visible.

**`jakarta.annotation` and process startup**: If `@PostConstruct` remains only to start process instances, migrate that startup to `@EventListener(CamundaPostDeploymentEvent.class)` first. Prefer fixing that lifecycle pattern over adding dependencies (for example `jakarta.annotation-api`) solely to keep `@PostConstruct`.