# Maven dependency and configuration

As part of the code migration, remove all Camunda 7 dependencies. Import the **Camunda Spring SDK**:

```
<dependency>
	<groupId>io.camunda</groupId>
	<artifactId>spring-boot-starter-camunda-sdk</artifactId>
	<version>{version}</version>
</dependency>
```

Also, configure your the connection to the Camunda 8 cluster in the `application.properties` or `application.yaml`.