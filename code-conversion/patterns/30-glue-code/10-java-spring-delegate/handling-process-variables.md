# Handling Process Variables

The basic interaction of execution code and a running process instance is getting and setting process variables.

Check the [README](./README.md) for more details on class-level changes.

## JavaDelegate (Spring) - (Camunda 7)

### Java Object API

```java
    @Override
    public void execute(DelegateExecution execution) {
        int amount = (int) execution.getVariable("amount");
        // do something...
        execution.setVariable("transactionId", "TX12345");
    }
```

### Typed Value API

```java
    @Override
    public void execute(DelegateExecution execution) {
        IntegerValue typedAmount = execution.getVariableTyped("amount");
        int amount = typedAmount.getValue();
        // do something...
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        execution.setVariable("transactionId", typedTransactionId);
    }
```

## Job Worker (Spring) - (Camunda 8)

### autoComplete = true (default)

```java
    @JobWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJob(JobClient client, ActivatedJob job) {
        int amount = (int) job.getVariablesAsMap().get("amount");
        // do something...
        return Map.of("transactionId", "TX12345");
    }
```

-   _fetchVariables_ can be specified to restrict which variables are fetched from the process instance

### Preserve nullable variable reads

Camunda 7 returns `null` when `DelegateExecution#getVariable` reads a variable that is not
available. Use the variables map when the migrated code must preserve that behavior:

```java
    @JobWorker(type = "sampleJavaDelegate")
    public Map<String, Object> handleJob(ActivatedJob job) {
        Object comment = job.getVariablesAsMap().get("comment");
        // continue when comment is null...
        return Map.of("status", "processed");
    }
```

Do not replace this access with `job.getVariable("comment")`: the job worker API raises an
exception when the requested variable is unavailable. The OpenRewrite delegate migration recipe
uses `job.getVariablesAsMap().get(...)` for migrated `getVariable` calls. It leaves
`getVariableLocal` calls unchanged and adds a manual-migration TODO because the job worker API
does not expose the Camunda 7 execution scope.
When using Spring variable injection instead, mark an optional input explicitly:

```java
    @JobWorker(type = "sampleJavaDelegate")
    public void handleJob(@Variable(name = "comment", optional = true) String comment) {
        // comment is null when the process variable is unavailable
    }
```

### autoComplete = false (blocking)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job, @Variable(name = "amount") int amount) {
        // do something...
        client.newCompleteCommand(job.getKey())
            .variables(Map.of("transactionId", "TX12345"))
            .send()
            .join();
    }
```

-   _@Variable_ can be used to fetch and cast a specific variable. For more information, see [the docs](https://docs.camunda.io/docs/apis-tools/camunda-spring-boot-starter/configuration/#using-variable-recommended).
-   _.send().join()_ is blocking and waits for the response from the cluster

### autoComplete = false (reactive)

```java
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJob(JobClient client, ActivatedJob job, @Variable(name = "amount") int amount) {
        // do something...
        client.newCompleteCommand(job.getKey())
            .variables(Map.of("transactionId", "TX12345"))
            .send()
            .thenApply(jobResponse -> jobResponse)
            .exceptionally(t -> {
                throw new RuntimeException("Could not complete job: " + t.getMessage(), t);
            });
    }
```

-   without _.join()_, the method _.send()_ returns a non-blocking _CamundaFuture_. With _thenApply()_ and _exceptionally()_ the response can be processed
-   this non-blocking programming style is **recommended** by Camunda
