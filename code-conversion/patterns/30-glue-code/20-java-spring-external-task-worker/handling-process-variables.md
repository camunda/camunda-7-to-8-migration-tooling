# Handling Process Variables

The basic interaction of execution code and a running process instance is getting and setting process variables.

Check the [README](./README.md) for more details on class-level changes.

## JavaDelegate (Spring) - (Camunda 7)

### Java Object API

```java
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        int amount = (int) externalTask.getVariable("amount");
        // do something
        Map<String, Object> variableMap = Map.ofEntries(
            Map.entry("transactionId", "TX12345")
        );
        externalTaskService.complete(externalTask.getId(), variableMap, null);
    }
```

### Typed Value API

```java
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        IntegerValue typedAmount = externalTask.getVariableTyped("amount");
        int amount = typedAmount.getValue();
        // do something
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        VariableMap variableMap = Variables.putValueTyped("transactionId", typedTransactionId);
        externalTaskService.complete(externalTask.getId(), variableMap, null);
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

## Completion variable scope

Camunda 7 external-task completion separates process variables from task-local variables. Inspect
the overload and both maps because downstream activities can depend on their different scopes.

| C7 completion call | Process variables | Task-local variables |
|---|---|---|
| `complete(id, processVariables)` or `complete(id, processVariables, null)` | Writes the supplied map | None |
| `complete(id, processVariables, localVariables)` | Writes the supplied map | Writes the supplied map |
| `complete(id, null, localVariables)` | None | Writes the supplied map |

Camunda 8 Spring auto-completion and `CompleteJobCommand.variables(...)` send one job-result map.
Neither call has a separate argument for C7 task-local variables. Camunda 8 applies BPMN variable
scope and propagation rules when the job completes. See the
[variable-scope docs](https://docs.camunda.io/docs/components/concepts/variables/).

An input mapping can create a local variable scope. An output mapping controls which local values
propagate when the activity completes. A worker result map alone does not prove that a C7 completion
keeps the same scope. See the
[input/output mapping docs](https://docs.camunda.io/docs/components/modeler/bpmn/data-handling/).

Inspect every C7 completion branch and every downstream read. Preserve each branch only when the
C8 worker and BPMN mappings preserve its scope. If no faithful mapping exists, then ask the user for
a manual BPMN/worker-scoping decision. Never leave the source branch unused and report parity.

When a condition such as `isRandomSample` selects the completion scope, test both outcomes. For
example, assert where `invoiceId` and `invoice` are visible and whether the archiver executes. If a
separate deployment blocker prevents the test, then record that blocker and keep parity unresolved.
After the blocker closes, run both outcomes before reporting parity.
