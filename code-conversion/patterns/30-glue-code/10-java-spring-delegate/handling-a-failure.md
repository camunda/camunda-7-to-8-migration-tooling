# Handling a Failure

Execution code can fail, promting the engine to try again or raise an incident if no retries are left. This example focuses on throwing an exception from a JavaDelegate vs. throwing an exception from a job worker.

Check the [README](./README.md) for more details on class-level changes.

## Transaction boundary

Without `camunda:asyncBefore` or another intervening asynchronous boundary, C7 runs a JavaDelegate in the command that completes the preceding wait state. If the delegate fails, the command rolls back and the wait state remains incomplete.

C8 runs the worker after the engine creates a job. A failed job consumes retries and can raise an incident when retries are exhausted. The worker cannot roll back the completed wait state or share the C7 engine transaction and thread-bound security context.

Do not describe the same Java body in a worker as equivalent synchronous behavior. If the source relies on rollback, ask the user to choose C8 retry and incident handling, a BPMN error or compensation flow, or an explicit manual step. If the source relies on thread-bound context, ask the user to choose a worker-side replacement mechanism or a code refactor. Record the exact gap and selected behavior in `MIGRATION_REPORT.md`.

## JavaDelegate (Spring) - (Camunda 7)

```java
    @Override
    public void execute(DelegateExecution execution) {
        try {
            // do something...
        } catch(Exception e) {
            execution.setVariable("transactionId", "TX12345");
            throw new ProcessEngineException("my error message", e);
        }
    }
```

-   variables cannot be added to the _ProcessEngineException_ and need to be set separately
-   When a synchronous JavaDelegate follows a user task without an intervening asynchronous boundary, a failure rolls back the transaction that completes the user task.
-   Configure `camunda:asyncBefore` to run the delegate as an asynchronous job. The engine then decrements retries and raises an incident when none remain.
-   Set a retry time cycle on the asynchronous delegate, for example: R3/PT30S
-   engine configurations can be used to set a default retry behavior

## Job Worker (Spring) - (Camunda 8)

### autoComplete = true (default)

```java
    @JobWorker(type = "retrievePaymentAdapter")
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            throw CamundaError.jobError("My error message", Map.of("transactionId", "TX12345"), job.getRetries() - 1, Duration.ofSeconds(30));
        }
    }
```

-   the engine registers the exeception and either retries or raises an incident, depending on the number of retries left
-   the initial number of retries is set in the BPMN xml
-   the job worker handles decrementing the number of retries and the retry backoff strategy explicitely
-   the job can fail with variables to skip work in the next retry that was already done in a previous job run
-   for more information on failing a job in a controlled way, look at [the docs](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/#failing-jobs-in-a-controlled-way)

### autoComplete = false (blocking)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            client.newFailCommand(job.getKey())
                .retries(job.getRetries() - 1)
                .errorMessage("my error message")
                .variables(Map.of("transactionId", "TX12345"))
                .retryBackoff(Duration.ofSeconds(30))
                .send()
                .join();
        }
    }
```

-   _.send().join()_ is blocking and waits for the response from the cluster

### autoComplete = false (reactive)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            client.newFailCommand(job.getKey())
                .retries(job.getRetries() - 1)
                .errorMessage("my error message")
                .variables(Map.of("transactionId", "TX12345"))
                .retryBackoff(Duration.ofSeconds(30))
                .send()
                .exceptionally(t -> {
                    throw new RuntimeException("Could not fail job: " + t.getMessage(), t);
                });
        }
    }
```

-   without _.join()_, the method _.send()_ returns a non-blocking _CamundaFuture_. With _thenApply()_ and _exceptionally()_ the response can be processed
-   this non-blocking programming style is **recommended** by Camunda
