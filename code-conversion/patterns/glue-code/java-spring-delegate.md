# Java Delegate (Spring) &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. Very often, JavaDelegates are Spring beans and referenced via Expression language in the BPMN xml.

<details>
<summary>

## Receiving and Writing Process Variables

</summary>

The following example focuses on receiving and writing process variables to interact with a running process instance.

### JavaDelegate (Spring) - (Camunda 7)

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

-   to execute this code in a specific service task or listener, the **bean name** is referenced in the BPMN xml
-   the JavaDelegate interface handles the connectivity to the spring-integrated engine
-   the **execute** method of the interface needs to be implemented
-   the code can directly read and write process instance information and access the engines **services**

### Diff View between Camunda 7 and Camunda 8

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

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class RetrievePaymentWorker {

    @JobWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int amount = (int) job.getVariablesAsMap().get("amount");
        // do something...
        return Map.of("transactionId", "TX12345");
    }
}
```

-   the **bean name** is not relevant
-   to execute this code in a specific service task or listener, the **type** is referenced in the BPMN xml
-   the **@JobWorker** annotation handles the connectivity to the remote engine
-   the **method name** is not relevant in this example
-   the code runs externally, it receives all or specified variables from the process instance, and return process variables on completion
-   for more information on how to implement job workers, check [the docs](https://docs.camunda.io/docs/8.8/apis-tools/spring-zeebe-sdk/configuration/)

</details>

<details>
<summary>

## Handling a Failure

</summary>

The execution of business code can fail, promting the engine to try again or raise an incident if no retries are left. This example focuses on throwing an exception from a JavaDelegate vs. throwing an exception from a job worker.

### JavaDelegate (Spring) - (Camunda 7)

```java
@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        try {
            // do something...
        } catch(Exception e) {
            throw new ProcessEngineException("My error message", 2_222);
        }
    }
}
```

-   the engine registers the exeception and either retries or raises an incident
-   when setting async before, a retry time cycle can be specified for the executed delegate code, for example: R3/PT30S
-   the engine decrements the number of retries itself
-   engine configurations can be used to set a default retry behavior

### Diff View between Camunda 7 and Camunda 8

```diff
@Component
- public class RetrievePaymentAdapter implements JavaDelegate {
+ public class RetrievePaymentWorker {

-   @Override
-   public void execute(DelegateExecution execution) throws Exception {
+   @JobWorker(type = "retrievePaymentAdapter")
+   public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
-           throw new ProcessEngineException("My error message", 2_222);
+           throw new CamundaError.jobError("My error message", new ErrorVariables(), job.getRetries() - 1, Duration.ofSeconds(30), e);
        }
    }
}
```

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class RetrievePaymentWorker {

    @JobWorker(type = "retrievePaymentAdapter")
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            throw new CamundaError.jobError("My error message", new ErrorVariables(), job.getRetries() - 1, Duration.ofSeconds(30), e);
        }
    }
}
```

-   the engine registers the exeception and either retries or raises an incident, depending on the number of retries left
-   the number of retries is set in the BPMN xml
-   the job worker handles decrementing the number of retries and the retry backoff strategy
-   the job can fail with variables to skip work in the retry that was already done in a previous job run
-   for more information on failing a job in a controlled way, look at [the docs](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/#failing-jobs-in-a-controlled-way)

</details>

<details>

<summary>

##Handling an Incident

</summary>

This example focuses on raising an incident directly from a JavaDelegate or job worker, not incidents raised by depleted retries.

### JavaDelegate (Spring) - (Camunda 7)

```java
@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        int amount = (int) execution.getVariable("amount");
        if(amount < 10) {
            delegateExecution.createIncident("someType", "someConfiguration", "someMessage");
        }
    }
}
```

### Diff View between Camunda 7 and Camunda 8

```diff
@Component
- public class RetrievePaymentAdapter implements JavaDelegate {
+ public class RetrievePaymentWorker {

-   @Override
-   public void execute(DelegateExecution execution) throws Exception {
+   @JobWorker(type = "retrievePaymentAdapter")
+   public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
-       int amount = (int) execution.getVariable("amount");
+       int amount = (int) job.getVariablesAsMap().get("amount");
        if(amount < 10) {
-           delegateExecution.createIncident("someType", "someConfiguration", "someMessage");
+           throw new CamundaError.jobError("My error message", new ErrorVariables(), 0, null, e);
        }
    }
}
```

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class RetrievePaymentWorker {

    @JobWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int amount = (int) job.getVariablesAsMap().get("amount");
        if (amount < 10) {
            throw new CamundaError.jobError("My error message", new ErrorVariables(), 0, null, e);
        }
    }
}
```

-   raising an incident directly uses the same **fail job** API
-   the number of retries is set to 0 to raise the incident
-   for more information on failing a job in a controlled way, look at [the docs](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/#failing-jobs-in-a-controlled-way)

</details>

<details>

<summary>

## Handling a BPMN error

</summary>

This example focuses on throwing a BPMN error from a JavaDelegate and job worker. A BPMN error is thrown for a task or listener and caught by a BPMN catch event in the BPMN model. The BPMN error is used for business errors that require a change in the process flow, not for technical errors.

### JavaDelegate (Spring) - (Camunda 7)

```java
@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        int amount = (int) execution.getVariable("amount");
        if(amount < 10) {
            throw new BpmnError("My error message");
        }
    }
}
```

-   this example does not use a try catch block to indicate that BPMN errors are thrown for business errors, not technical errors

### Diff View between Camunda 7 and Camunda 8

```diff
@Component
- public class RetrievePaymentAdapter implements JavaDelegate {
+ public class RetrievePaymentWorker {

-   @Override
-   public void execute(DelegateExecution execution) throws Exception {
+   @JobWorker(type = "retrievePaymentAdapter")
+   public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
-       int amount = (int) execution.getVariable("amount");
+       int amount = (int) job.getVariablesAsMap().get("amount");
        if(amount < 10) {
-           delegateExecution.createIncident("someType", "someConfiguration", "someMessage");
+           throw new CamundaError.jobError("My error message", new ErrorVariables(), 0, null, e);
        }
    }
}
```

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class RetrievePaymentWorker {

    @JobWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int amount = (int) job.getVariablesAsMap().get("amount");
        if(amount < 10) {
            throw CamundaError.bpmnError("ERROR_CODE", "Some explanation why this does not work", Map.of("transactionId", "TX12345"));
            // throwable can be included
        }
    }
}
```

</details>

## Job Worker: The autoComplete Attribute

By default, the autoComplete attribute in the job worker annotation is set to true. This feature can simplify the business code, e.g., variables can be handed over to the process instance with a return statement and a **Map** without specifying the job completion manually, or throwing errors and exceptions via throwables.

The examples above show how to handle a failure, incident and BPMN error with autoComplete set to true. But all these examples use blocking code, limiting the degree of parallelism to the number of threads you have configured.

Setting autoComplete to false allows you to use reactive programming. For more information on Camunda's recommendation to use reactive programming, see [the docs](https://docs.camunda.io/docs/components/best-practices/development/writing-good-workers/#java).

## OpenRewrite recipe (WIP)

-   [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)
-   [Learn how to apply recipes](../recipes/)
