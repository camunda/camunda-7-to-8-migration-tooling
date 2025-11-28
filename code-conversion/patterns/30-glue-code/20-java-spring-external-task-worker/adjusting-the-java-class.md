# Class-level Changes

## Camunda 7: External Task Worker

External task workers can be implemented one per Java class, implementing the ExternalTaskHandler interface, similar to the JavaDelegate.

```java
@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentHandler implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        // execution code
    }

}
```

To define multiple external task workers per class, each external task worker method is annotated with _@Bean_ and _@ExternalTaskSubscription_. In this case, the _execute()_ method is implemented as a _lambda function_.

```java
@Configuration
public class RetrievePaymentWorker {

    @Bean
    @ExternalTaskSubscription("retrievePaymentAdapter")
    public ExternalTaskHandler retrievePaymentHandler() {
        return (externalTask, externalTaskService) -> {
            // execution code
        };
    }
}
```

In both cases, the external task worker is identified by the topic added to the _@ExternalTaskSubscription_ annotation. The topic is referenced in the BPMN xml.

## Camunda 8: Job Worker

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

