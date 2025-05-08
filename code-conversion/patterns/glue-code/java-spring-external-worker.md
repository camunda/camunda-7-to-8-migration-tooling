# Java External Worker (Spring) &#8594; Job Worker (Spring)

The external worker pattern is the recommended approach to implement glue code in Camunda 7. In contrast to JavaDelegates which can only be used with an embedded engine architecture, external workers are used to communicate asynchronously with a remote engine.

External workers in Camunda 7 are similar to job workers in Camunda 8.

<details>
<summary>

## Receiving and Writing Process Variables

</summary>

The following example focuses on receiving and writing process variables to interact with a running process instance.

### External Worker (Spring) - (Camunda 7)

```java
@Configuration
public class CreditScoreChecker {

    @Bean
    @ExternalTaskSubscription("creditScoreChecker")
    public ExternalTaskHandler creditScoreCheckerHandler() {
        return (externalTask, externalTaskService) -> {
            int defaultScore = (int) externalTask.getVariable("defaultScore");
            // do something
            externalTaskService.complete(externalTask, Variables.putValueTyped("creditScore", Variables.objectValue(defaultScore + 42).create()));
        }
    }
}
```

-   to execute this code in a specific service task or listener, the **topic** is referenced in the BPMN xml (see annotation)
-   this example allows the configuration of multiple external workers in one file. It is also possible to add let the class implement the ExternalTaskHandler and then override the **execute** function, similar to JavaDelegates. See [the docs](https://docs.camunda.org/manual/latest/user-guide/ext-client/spring-boot-starter/#handler-configuration-example)
-   the code cannot access the engine's service

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class CreditScoreChecker {

    @JobWorker(type = "creditScoreChecker", fetchVariables={"defaultScore"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int defaultScore = (int) job.getVariablesAsMap().get("defaultScore");
        // do something...
        return Map.of("creditScore", defaultScore + 42);
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

The execution of business code can fail, promting the engine to try again or raise an incident if no retries are left. This example focuses on throwing an exception from an external task worker vs. throwing an exception from a job worker.

### JavaDelegate (Spring) - (Camunda 7)

```java
@Configuration
public class CreditScoreChecker {

    @Bean
    @ExternalTaskSubscription("creditScoreChecker")
    public ExternalTaskHandler creditScoreCheckerHandler() {
        return (externalTask, externalTaskService) -> {
            try {
                // do something...
            } catch(Exception e) {
                externalTaskService.handleFailure(externalTask, "my error message", "my error details", externalTask.getRetries() - 1, 30000l);
                // variables and local variables can also be specified
            }
        }
    }
}
```

-   the number of retries is set in the BPMN xml
-   retries are decremented by the external worker on job failure, explicit retry handling is possible
-   the job can fail with variables to skip work in the retry that was already done in a previous job run
-   the architecture and functionality of an external task worker and a job worker are very similar

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class CreditScoreChecker {

    @JobWorker(type = "creditScoreChecker")
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            throw new CamundaError.jobError("My error message", new ErrorVariables(), job.getRetries() - 1, Duration.ofSeconds(30), e);
        }
    }
}
```

-   the API to throw a failure from a job worker requires no extra worker identifier
-   the engine registers the exeception and either retries or raises an incident, depending on the number of retries left
-   the number of retries is set in the BPMN xml
-   the job worker handles decrementing the number of retries and the retry backoff strategy
-   the job can fail with variables to skip work in the retry that was already done in a previous job run
-   for more information on failing a job in a controlled way, look at [the docs](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/#failing-jobs-in-a-controlled-way)

</details>

<details>

<summary>

## Handling an Incident

</summary>

This example focuses on raising an incident directly from a JavaDelegate or job worker, not incidents raised by depleted retries.

### JavaDelegate (Spring) - (Camunda 7)

```java
@Configuration
public class CreditScoreChecker {

    @Bean
    @ExternalTaskSubscription("creditScoreChecker")
    public ExternalTaskHandler creditScoreCheckerHandler() {
        return (externalTask, externalTaskService) -> {
            int defaultScore = (int) externalTask.getVariable("defaultScore");
            if (defaultScore < 200) {
                externalTaskService.handleFailure(externalTask, "my error message", "my error details", 0, null);
            }
        }
    }
}
```

-   raising an incident directly uses the same **handle failure** API
-   if the number of retries is 0, an incident is raised
-   there are API endpoints and externalTaskService methods to update the number of retries. These methods can also be used to raise incidents

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class CreditScoreChecker {

    @JobWorker(type = "creditScoreChecker", fetchVariables={"defaultScore"})l
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int defaultScore = (int) job.getVariablesAsMap().get("defaultScore");
        if (defaultScore < 300) {
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

This example focuses on throwing a BPMN error from an external task worker and job worker. A BPMN error is thrown for a task or listener and caught by a BPMN catch event in the BPMN model. The BPMN error is used for business errors that require a change in the process flow, not for technical errors.

### JavaDelegate (Spring) - (Camunda 7)

```java
@Configuration
public class CreditScoreChecker {

    @Bean
    @ExternalTaskSubscription("creditScoreChecker")
    public ExternalTaskHandler creditScoreCheckerHandler() {
        return (externalTask, externalTaskService) -> {
            int defaultScore = (int) externalTask.getVariable("defaultScore");
            if (defaultScore < 200) {
                externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", Variables.putValueTyped("creditScore", Variables.objectValue(defaultScore + 42).create()));
            }
        }
    }
}
```

-   this example does not use a try catch block to indicate that BPMN errors are thrown for business errors, not technical errors

### Job Worker (Spring) - (Camunda 8)

```java
@Component
public class CreditScoreChecker {

    @JobWorker(type = "creditScoreChecker", fetchVariables={"defaultScore"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int defaultScore = (int) job.getVariablesAsMap().get("defaultScore");
        if (defaultScore < 300) {
            throw CamundaError.bpmnError("ERROR_CODE", "Some explanation why this does not work", Map.of("creditScore", defaultScore + 42));
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

-   nothing yet
