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

**Spring Boot version**: Select the starter from the [Camunda Spring Boot version compatibility matrix](https://docs.camunda.io/docs/8.8/apis-tools/camunda-spring-boot-starter/getting-started/#version-compatibility). For Camunda 8.8, `camunda-spring-boot-starter` is bundled with Spring Boot 3.5.x. Use `camunda-spring-boot-4-starter` from 8.8.9 for Spring Boot 4.0.x. Use `camunda-spring-boot-3-starter` from 8.8.15 when staying on Spring Boot 3.x.

**Startup validation**: When a project uses a Camunda Spring Boot starter, boot an application context that creates `CamundaClient`. If startup fails, record a blocking finding. Do not override individual transitive dependencies to force startup.