# Handling an Incident

If an execution code failure cannot be solved via retries, or if all retries are exhausted, an incident can be raised. This incident is visible in Cockpit (Camunda 7) and Operate (Camunda 8). It can be retried once the underlying cause of failure is solved.

Check the [README](./README.md) for more details on class-level changes.

## JavaDelegate (Spring) - (Camunda 7)

```java
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            // do something...
        } catch(Exception e) {
            Map<String, Object> variableMap = Map.ofEntries(
                Map.entry("transactionId", "TX12345")
            );
            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", 0, 0L, variableMap, null);
        }
    }
```

-   with the retries set to 0, the engine raises an incident

## Job Worker (Spring) - (Camunda 8)

### autoComplete = true (default)

```java
    @JobWorker(type = "retrievePaymentAdapter")
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch (Exception e) {
            throw CamundaError.jobError("My error message", Map.of("transactionId", "TX12345"), 0, null, e);
        }
    }
```

-   raising an incident directly uses the same **fail job** API as handling a retryable failure
-   the number of retries is set to 0 to raise the incident
-   for more information on failing a job in a controlled way, look at [the docs](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/#failing-jobs-in-a-controlled-way)

### autoComplete = false (blocking)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            client.newFailCommand(job.getKey())
                .retries(0)
                .errorMessage("my error message")
                .variables(Map.of("transactionId", "TX12345"))
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
                .retries(0)
                .errorMessage("my error message")
                .variables(Map.of("transactionId", "TX12345"))
                .send()
                .exceptionally(t -> {
                    throw new RuntimeException("Could not raise incident: " + t.getMessage(), t);
                });
        }
    }
```

-   without _.join()_, the method _.send()_ returns a non-blocking _CamundaFuture_. With _thenApply()_ and _exceptionally()_ the response can be processed
-   this non-blocking programming style is **recommended** by Camunda
