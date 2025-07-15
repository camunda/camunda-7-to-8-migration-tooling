# Class-level Changes

## Camunda 7: JavaDelegate as Spring Bean

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

In the process model this is referenced as `delegateExpression`:

```xml
<bpmn:serviceTask id="ServiceTask_A" camunda:delegateExpression="retrievePaymentAdapter">
```

## Camunda 7: JavaDelegate as Class

If defined via class name, Camunda instantiates the delegate itself.

```java
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // execution code
    }
}
```

In the process model this is referenced as `class`:

```xml
<bpmn:serviceTask id="ServiceTask_A" camunda:class="com.sample.RetrievePaymentAdapter">
```


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

When you convert your diagrams from Camunda 7 to Camunda 8 using the [Migration Analyzer & Diagram Converter](https://github.com/camunda-community-hub/camunda-7-to-8-migration-analyzer), you can automatically set the `job type` to either the Spring bean name, or the class name (in Camel case).

```xml
<bpmn:serviceTask id="ServiceTask_A" >
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="retrievePaymentAdapter"/>
```


For more information, check [the docs](https://docs.camunda.io/docs/8.8/apis-tools/spring-zeebe-sdk/get-started/).

