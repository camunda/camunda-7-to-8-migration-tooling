# Java Delegate (Java class) &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. Sometimes, the BPMN process model references the class name of the JavaDelegate and Camunda instantiates those classes.

In Camunda 8, you ideally use Spring to manage your workers, otherwise you have to code quite some code to control the worker lifecycle yourself.

## Before (Camunda 7)

Java:

```java
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        int amount = (int) execution.getVariable("amount");
        // do something...
        execution.setVariable("transactionId", "TX12345");
    }
}
```

BPMN model (XML):

```xml
<bpmn:serviceTask id="serviceTask_RetrievePayment" camunda:class="io.example.RetrievePaymentAdapter" />
```

## Diff View between Camunda 7 and Camunda 8

Java diff:

```diff
+ @Component
- public class RetrievePaymentAdapter implements JavaDelegate {
+ public class RetrievePaymentWorker {

-   @Override
-   public void execute(DelegateExecution execution) throws Exception {
+   @ZeebeWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
+   public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
-       int amount = (int) execution.getVariable("amount");
+       int amount = (int) job.getVariablesAsMap().get("amount");
        // do something...
-       execution.setVariable("transactionId", "TX12345");
+       return Map.of("transactionId", "TX12345");
    }
}
```

## After (Camunda 8)

Java:

```java
@Component
public class RetrievePaymentWorker {

    @JobWorker(type = "io.example.RetrievePaymentAdapter")
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        Map<String, Object> resultMap = new HashMap<String, Object>();

        int amount = (int) variables.get("amount");
        // do something...
        resultMap.setVariable("transactionId", "TX12345");

        return resultMap;
    }
}
```

BPMN model (XML):

```xml
    <bpmn:serviceTask id="serviceTask_RetrievePayment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="io.example.RetrievePaymentAdapter" />
      </bpmn:extensionElements>
    </bpmn:serviceTask>
```

## Remarks

-   The class name is used as task type, so that code and model conversion can both work without knowing each other
-   The JavaDelegate interface has been removed
-   The JobWorker annotation is added
-   Reading and writing process variables has changed (for more information, check [the docs](https://docs.camunda.io/docs/8.8/apis-tools/spring-zeebe-sdk/configuration/))

## OpenRewrite recipe

-   No recipe yet, but you can customize the recipe [JavaDelegateSpringToZeebeWorkerSpring](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)
