# Delegate and Worker Mocks

The CPT mock-worker APIs in this pattern are available from Camunda 8.8. Preserve what the Camunda 7 test replaced. Use the job type from `zeebe:taskDefinition` in the converted copy, not the Camunda 7 bean name.

## Camunda 7

```java
Mocks.register("invoiceService", invoiceService);
DelegateExpressions.registerJavaDelegateMock("notifyDelegate")
    .onExecutionSetVariables(Variables.putValue("notified", true));
verifyJavaDelegateMock("notifyDelegate").executed();
```

## Camunda 8

```java
@CamundaProcessTest
class InvoiceProcessTest {
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void deployConvertedModel() {
    client.newDeployResourceCommand()
        .addResourceFromClasspath("converted-c8-invoice.bpmn")
        .send().join();
  }

  @Test
  void runsWithTheSameMockBoundary() {
    JobWorkerMock notify = processTestContext.mockJobWorker("notify")
        .thenComplete(Map.of("notified", true));

    client.newCreateInstanceCommand()
        .bpmnProcessId("invoice").latestVersion().send().join();

    assertThatProcessInstance(byProcessId("invoice")).hasCompletedElements("Task_Notify");
    assertThat(notify.getInvocations()).isEqualTo(1);
  }
}
```

| Camunda 7 | CPT | Note |
|---|---|---|
| `Mocks.register("service", mock)` where the service is a collaborator called by a delegate | Keep a Mockito mock of the collaborator | Run the real migrated worker. Use `@MockitoBean` in Spring or pass the mock to the worker. |
| `Mocks.register("delegate", mock)` for a whole delegate expression | `mockJobWorker(type)` | The real delegate did not run in Camunda 7, so mock the converted task's job type. |
| `registerJavaDelegateMock(name)` | `mockJobWorker(type)` | Read `type` from the converted model. |
| `.onExecutionSetVariables(vars)`, `.onExecutionSetVariable(key, value)` | `.thenComplete(vars)` | |
| `.onExecutionSetVariables(first, second)` | `.withHandler(...)` | Complete with the next result on each invocation. |
| `.onExecutionThrowBpmnError(code, message)` | `.thenThrowBpmnError(code, message, vars)` (8.9+) or `.thenThrowBpmnError(code, vars)` / `.thenThrowBpmnError(code)` (8.8) | The 8.8 builder cannot preserve the error message. Use `.withHandler(...)` and `newThrowErrorCommand(...)` when the message matters. |
| `.onExecutionThrowException(exception)` | `.withHandler(...)` that fails the job with zero retries | Camunda 7 throws into the test. Camunda 8 creates an incident. Assert `hasActiveIncidents()` instead. |
| `DelegateExpressions.autoMock("process.bpmn")` | One `mockJobWorker(type).thenComplete()` per converted job type | Include listener job types. Disable the matching real workers in a Spring test. |
| `registerExecutionListenerMock(...)`, `registerTaskListenerMock(...)` | `mockJobWorker(type)` | Use the converted listener job type. Record a listener that the converter removed. |
| `verifyJavaDelegateMock(name).executed(times(n))` or `executedNever()` | `mock.getInvocations()` | Add a waiting CPT assertion before checking invocations. |
| `ArgumentCaptor<DelegateExecution>` | `mock.getActivatedJobs()` and each job's variables | |
| `Mocks.reset()`, mock cleanup in `@After` | Remove the cleanup | CPT closes the client and clears runtime data after each test. |

For a failed job, a worker mock can use a custom handler:

```java
processTestContext.mockJobWorker("validate")
    .withHandler((jobClient, job) -> jobClient.newFailCommand(job)
        .retries(0).errorMessage("Validation failed").send().join());
```

In CPT 8.8, use a custom handler to preserve a BPMN error message:

```java
processTestContext.mockJobWorker("validate")
    .withHandler((jobClient, job) -> jobClient.newThrowErrorCommand(job)
        .errorCode("VALIDATION_ERROR")
        .errorMessage("Validation failed")
        .send().join());
```

Do not add a worker mock when the Camunda 7 test ran the real worker. If the current test cannot run that worker, ask the user before changing the mock boundary.

[CPT mock job workers](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-job-workers)
