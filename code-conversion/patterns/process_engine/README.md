# RuntimeService

For an introduction to code conversion patterns, see the [patterns README](../README.md).

The RuntimeService provides access to deployments, process definitions and process instances.

The different methods of the RuntimeService are grouped into separated .md files by action, such as starting a process instance, with multiple examples covering different methods of performing the same action.

## Class-level Changes

The code conversion patterns will focus on method-level changes. All class-level changes are described below.

### Camunda 7 - Class with autowired ProcessEngine

In Camunda 7, all engine services can be accessed via the _ProcessEngine_ interface. To interact with the engine services, create a class and inject the engine via the _@Autowired_ annotation.

```java
@Component
public class SomeClass {

    @Autowired
    private ProcessEngine engine;

    // methods can access the engine's services
}
```

### Camunda 8 - Class with autowired CamundaClient

The setup is the same in Camunda 8. The interface is called _CamundaClient_.

```java
@Component
public class SomeClass {

    @Autowired
    private CamundaClient camundaClient;

    // methods can access the client's methods
}
```

## OpenRewrite recipe (WIP)

-   [Recipe "ProcessEngineToZeebeClient"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/client/ProcessEngineToZeebeClient.java)
-   [Learn how to apply recipes](../recipes/)
