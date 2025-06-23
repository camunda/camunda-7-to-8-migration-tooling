# JavaDelegate (Spring) &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. Very often, JavaDelegates are Spring beans and referenced via Expression language in the BPMN xml.

JavaDelegates run in the same context as the engine. In addition to the DelegateExecution class that provides an interface to interact with the running process instance, a JavaDelegate can also call all engine services, like the Runtime service.

The code conversion patterns for the JavaDelegate cover the most important methods how a JavaDelegate can interact with the running process instance:

-   getting and setting process variables
-   reporting a failure
-   raising an incident
-   throwing a BPMN error

There are often multiple methods that achieve the same result. The patterns try to capture as many examples as possible. Delegate code that accesses the engine services is not covered here. Please refer to the patterns for the engine services. In general, delegate code that utilizes engines services is more difficult to migrate to Camunda 8.

## Class-level Changes

### Camunda 7- JavaDelegate

Each JavaDelegate is implemented as a Spring bean that implements the JavaDelegate interface. The execution code is added by overriding the _execute_ function. This provides the _DelegateExecution_ class to interact with the running process instance. The bean name is used in the BPMN xml to specify which JavaDeleate to execute.

```java
@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // execution code
    }
}
```

### Camunda 8 - Job Worker

Job workers are implemented as methods in a Spring bean with arbitrary name. The method is annotated with the _@JobWorker_ annotation. In the annotation, the _type_ can be set, which is used in the BPMN xml to specify which job worker to execute. Alternatively, if the type is not set in the annotation, the method name is set as the type.

```java
@Component
public class RetrievePaymentWorker {

    @JobWorker(type = "retrievePaymentAdapter")
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        // execution code
    }
}
```

For more information on how to implement job workers, check [the docs](https://docs.camunda.io/docs/8.8/apis-tools/spring-zeebe-sdk/configuration/).

The code conversion patterns will not cover the above class-level changes between JavaDelegates and job workers. Instead, they focus on method-level changes.

## OpenRewrite recipe (WIP)

-   [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)
-   [Learn how to apply recipes](../recipes/)
