# Java Delegate (Spring) &#8594; Job Worker (Spring)

In Camunda 7, Java Delegates are a common way to implement glue code. Very often, Java Delegates are Spring beans and referenced via Expression language in the BPMN model.

## Before (Camunda 7)

Java:

```java
@Component
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
<bpmn:serviceTask id="serviceTask_RetrievePayment" camunda:delegateExpression="#{retrievePaymentAdapter}" />
```

## Diff View between Camunda 7 and Camunda 8

Java diff:

```diff
@Component
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

    @ZeebeWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int amount = (int) job.getVariablesAsMap().get("amount");
        // do something...
        return Map.of("transactionId", "TX12345");
    }
}
```

BPMN model (XML):

```xml
    <bpmn:serviceTask id="serviceTask_RetrievePayment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="retrievePaymentAdapter" />
      </bpmn:extensionElements>
    </bpmn:serviceTask>
```

## Remarks

-   The Spring bean name is kept stable
-   The JavaDelegate interface has been removed
-   The JobWorker annotation is added
-   Reading and writing process variables has changed (for more information, check [the docs](https://docs.camunda.io/docs/8.8/apis-tools/spring-zeebe-sdk/configuration/))

## OpenRewrite recipe (WIP)

-   [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)
-   [Learn how to apply recipes](../recipes/)
