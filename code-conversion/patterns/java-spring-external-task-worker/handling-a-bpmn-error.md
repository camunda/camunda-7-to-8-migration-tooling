# Handling a BPMN error

This example focuses on throwing a BPMN error from a JavaDelegate and job worker. A BPMN error is thrown for a task or listener and caught by a BPMN catch event in the BPMN model. The BPMN error is used for business errors that require a change in the process flow, not for technical errors.

Check the [README](./README.md) for more details on class-level changes.

## External Task Worker (Spring) - (Camunda 7)

### Java Object API

```java
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Map<String, Object> variablesMap = Map.ofEntries(Map.entry("transactionId", "TX12345"));
        externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", variablesMap);
    }
```

-   Using the Java Object API, a _Map<String, Object>_ is used in the method to throw a BPMN error

### Typed Value API

```java
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        VariableMap variablesMap = Variables.putValueTyped("transactionId", typedTransactionId);
        externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", variablesMap);
    }
```

-   Using the Typed Value API, a _VariableMap_ is created using a typed value.

## Job Worker (Spring) - (Camunda 8)

### autoComplete = true (default)

```java
    @JobWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        throw CamundaError.bpmnError("my error code", "my error message", Map.of("transactionId", "TX12345"));
    }
```

### autoComplete = false (blocking)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job) {
        client.newThrowErrorCommand(job.getKey())
            .errorCode("my error code")
            .errorMessage("my error message")
            .variables(Map.of("transactionId", "TX12345"))
            .send()
            .join();
    }
```

-   _.send().join()_ is blocking and waits for the response from the cluster

### autoComplete = false (reactive)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job) {
        client.newThrowErrorCommand(job.getKey())
            .errorCode("my error code")
            .errorMessage("my error message")
            .variables(Map.of("transactionId", "TX12345"))
            .send()
            .exceptionally(t -> {
                throw new RuntimeException("Could not throw BPMN error: " + t.getMessage(), t);
            });
    }
```

-   without _.join()_, the method _.send()_ returns a non-blocking _CamundaFuture_. With _thenApply()_ and _exceptionally()_ the response can be processed
-   this non-blocking programming style is **recommended** by Camunda
