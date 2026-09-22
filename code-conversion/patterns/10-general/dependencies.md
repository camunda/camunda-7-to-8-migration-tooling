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

**Spring Boot version**: `camunda-spring-boot-starter` requires Spring Boot 4.0.x as of Camunda 8.9. If you are not yet on Spring Boot 4.x, use `camunda-spring-boot-3-starter` instead:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>camunda-spring-boot-3-starter</artifactId>
	<version>{version}</version>
</dependency>
```

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

**Apache HttpClient compatibility**: Resolve the selected `httpclient5` version from the Camunda client release and the active Spring Boot dependency management before adding an override. Inspect the selected `httpclient5` POM to identify its managed `httpcore5` and `httpcore5-h2` versions. Compare all three resolved artifacts with the expected family:

```
org.apache.httpcomponents.client5:httpclient5
org.apache.httpcomponents.core5:httpcore5
org.apache.httpcomponents.core5:httpcore5-h2
```

Use the following decision table:

| Resolved graph | Action |
|---|---|
| `httpclient5`, `httpcore5`, and `httpcore5-h2` match the selected client's compatible family | Keep the graph and add no override. |
| Any artifact differs from the compatible family | Manage all three artifacts with the versions declared by the selected client's POM. |
| The compatible family cannot be determined | Stop dependency validation and record a blocking finding in `MIGRATION_REPORT.md`. |

Treat an incompatible graph as a blocking finding in `MIGRATION_REPORT.md` until the complete family
is aligned. Record the compatible family and the resolved versions when the finding is closed.

For Maven, inspect the graph with:

```
mvn dependency:tree -Dverbose \
	-Dincludes=org.apache.httpcomponents.client5:httpclient5,org.apache.httpcomponents.core5:httpcore5,org.apache.httpcomponents.core5:httpcore5-h2
```

For Gradle, inspect the graph with `dependencies` and `dependencyInsight` for each coordinate. Record the selected versions, the compatible family, and the commands in `MIGRATION_REPORT.md`.

When the active dependency management selects an incompatible family, manage the complete family. Do not copy a version from another Spring Boot line:

```
<dependencyManagement>
	<dependencies>
		<dependency>
			<groupId>org.apache.httpcomponents.client5</groupId>
			<artifactId>httpclient5</artifactId>
			<version>5.6.3</version>
		</dependency>
		<dependency>
			<groupId>org.apache.httpcomponents.core5</groupId>
			<artifactId>httpcore5</artifactId>
			<version>5.4.3</version>
		</dependency>
		<dependency>
			<groupId>org.apache.httpcomponents.core5</groupId>
			<artifactId>httpcore5-h2</artifactId>
			<version>5.4.3</version>
		</dependency>
	</dependencies>
</dependencyManagement>
```

After the change, assert that the dependency tree contains one compatible version for each family member. Then boot a minimal Spring application that creates a `CamundaClient`; compilation alone does not validate binary compatibility.

**Logging backend**: When removing Camunda 7 webapp/rest starters, keep an SLF4J binding. If those starters were your only logging source, add `org.springframework.boot:spring-boot-starter-logging` (or another SLF4J backend) so startup failures remain visible.

**`jakarta.annotation` and process startup**: If `@PostConstruct` remains only to start process instances, migrate that startup to `@EventListener(CamundaPostDeploymentEvent.class)` first. Prefer fixing that lifecycle pattern over adding dependencies (for example `jakarta.annotation-api`) solely to keep `@PostConstruct`.