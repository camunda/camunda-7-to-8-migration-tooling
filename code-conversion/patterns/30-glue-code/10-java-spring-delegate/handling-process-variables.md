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

### Optional variable reads

`DelegateExecution#getVariable("comment")` returns `null` when the variable is absent.
The migration recipe preserves this for `getVariable(String)` with a nullable map lookup:

```java
Object comment = job.getVariablesAsMap().get("comment");
```

Do not replace it with `job.getVariable("comment")`, which fails for an absent variable.
Check local and typed variable lookups separately; they have different scope or type semantics.

### Typed date and byte factories

The recipe converts `DateValue` and `BytesValue` declarations to `Date` and `byte[]`,
including fields initialized with `Variables.dateValue(...)` or
`Variables.byteArrayValue(...)`. These factories can also take a Camunda 7
`isTransient` flag, which the unwrapped Java value cannot retain. When the flag
is `true` or computed, the recipe marks the declaration with a TODO. Review how
that value is published to the process before removing the TODO; do not assume
the transient behavior carries over.

When an initializer instead calls a helper that still returns a typed value,
the recipe leaves the declaration unchanged and marks it for manual migration
rather than producing an invalid raw assignment.
Qualified `getValue()` reads of converted fields, such as `this.date.getValue()`,
become direct field reads even when the method precedes the field declaration.
Reads of fields retained for manual migration keep `getValue()` until those
fields are migrated.
Later `Variables.dateValue(...)` and `Variables.byteArrayValue(...)` assignments
to converted values are unwrapped in the same way as initializers, with a TODO
for `true` or computed transient flags. Assignments to declarations retained for
manual migration keep their Camunda 7 typed getters and factories until the
declaration and its uses can be migrated together.

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
