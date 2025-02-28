# Glue code (Camunda calling your solution) 

Glue code, also known as adapter or delegation code, ties BPMN elements to code. Most prominently, there might be Java code executed when service tasks are triggered. 

This section looks into relevant migration patterns:

- [Java Delegate (Spring) &#8594; Job Worker (Spring)](#java-delegate-spring--job-worker-spring) 
- [Java Delegate (Java class) &#8594; Job Worker (Spring)](#java-delegate-java-class--job-worker-spring)

## Java Delegate (Spring) &#8594; Job Worker (Spring)

In Camunda 7, Java Delegates are a common way to implement glue code. Very often, Java Delegates are Spring beans and referenced via Expression language in the BPMN model.

### Before (Camunda 7)

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

### After (Camunda 8)

Java: 
```java
@Component
public class RetrievePaymentWorker {

    @ZeebeWorker(type = "retrievePaymentAdapter", autoComplete=true)
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
        <zeebe:taskDefinition type="retrievePaymentAdapter" />
      </bpmn:extensionElements>
    </bpmn:serviceTask>
```

### Remarks

- The Spring bean name is kept stable
- The JavaDelegate interface has been removed 
- The ZeebeWorker annotation is added
- Reading and writing process variables has changed

### OpenRewrite recipe

- [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)
- [Learn how to apply recipes](../recipes/)


## Java Delegate (Java class) &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. Sometimes, the BPMN process model references the class name of the JavaDelegate and Camunda instantiates those classes.

In Camunda 8, you ideally use Spring to manage your workers, otherwise you have to code quite some code to control the worker lifecycle yourself.


### Before (Camunda 7)

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

### After (Camunda 8)

Java: 
```java
@Component
public class RetrievePaymentWorker {

    @ZeebeWorker(type = "io.example.RetrievePaymentAdapter", autoComplete=true)
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

### Remarks

- The class name is used as task type, so that code and model conversion can both work without knowing each other
- The JavaDelegate interface has been removed 
- The ZeebeWorker annotation is added
- Reading and writing process variables has changed

### OpenRewrite recipe

- No recipe yet, but you can customize the recipe [JavaDelegateSpringToZeebeWorkerSpring](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)

