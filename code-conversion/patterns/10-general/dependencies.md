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

**Java client artifact**: Use `io.camunda:camunda-client-java`. The legacy `io.camunda:zeebe-client-java` artifact is deprecated and will be discontinued in Camunda 8.10.

**Spring Boot 3.5.x and Apache HttpClient**: Spring Boot 3.5.x may manage `org.apache.httpcomponents.client5:httpclient5` to `5.5.2`, while `io.camunda:camunda-client-java` 8.9.13 requires `5.6.1` or later. This mismatch can prevent the `CamundaClient` bean from starting with a `NoSuchMethodError`. Until the upstream dependency alignment is fixed, override the managed version in the application:

```
<dependencyManagement>
	<dependencies>
		<dependency>
			<groupId>org.apache.httpcomponents.client5</groupId>
			<artifactId>httpclient5</artifactId>
			<version>5.6.1</version>
		</dependency>
	</dependencies>
</dependencyManagement>
```

Verify the version required by the selected Camunda client release with `mvn dependency:tree -Dincludes=org.apache.httpcomponents.client5:httpclient5` before choosing the override.

**Logging backend**: When removing Camunda 7 webapp/rest starters, keep an SLF4J binding. If those starters were your only logging source, add `org.springframework.boot:spring-boot-starter-logging` (or another SLF4J backend) so startup failures remain visible.

**`jakarta.annotation` and process startup**: If `@PostConstruct` remains only to start process instances, migrate that startup to `@EventListener(CamundaPostDeploymentEvent.class)` first. Prefer fixing that lifecycle pattern over adding dependencies (for example `jakarta.annotation-api`) solely to keep `@PostConstruct`.