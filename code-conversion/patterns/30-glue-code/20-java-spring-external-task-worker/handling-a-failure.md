# Handling a Failure

Execution code can fail, promting the engine to try again or raise an incident if no retries are left. This example focuses on throwing an exception from an external task worker vs. throwing an exception from a job worker.

Check the [README](./README.md) for more details on class-level changes.

## External Task Worker (Spring) - (Camunda 7)

```java
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            // do something...
        } catch(Exception e) {
            Map<String, Object> variableMap = Map.ofEntries(
                Map.entry("transactionId", "TX12345")
            );
            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", externalTask.getRetries() - 1, 30000L, variableMap, null);
        }
    }
```

-   the engine registers the exeception and either retries or raises an incident
-   the retry configuration and backoff strategy is handled by the external task worker itself
-   _async before_ should not be added to the BPMN, an external task worker constitutes a wait state itself

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
