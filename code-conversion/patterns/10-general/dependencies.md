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