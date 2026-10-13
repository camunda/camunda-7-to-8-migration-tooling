# Delegate and Worker Mocks

The basic CPT mock-worker APIs in this pattern are available from Camunda 8.8. Conditional behavior and user-task listener completion require Camunda 8.9 or later. Preserve what the Camunda 7 test replaced. Use the job type from `zeebe:taskDefinition` in the converted copy, not the Camunda 7 bean name.

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
| `Mocks.register("service", mock)` for a `camunda:expression` target | Keep a Mockito mock of the expression target. Inject it into the matching service used by the real worker. | Keep the real worker enabled. Use `@MockitoBean` in Spring or pass the mock to the worker. |
| `Mocks.register("service", mock)` for a collaborator called by a real delegate or worker | Keep a Mockito mock of the collaborator and inject it into the real worker's collaborator. | Keep the real worker enabled. Use `@MockitoBean` in Spring or pass the mock to the worker. |
| `Mocks.register("delegate", mock)` for a whole delegate expression | `mockJobWorker(type)` | The real delegate did not run in Camunda 7, so mock the converted task's job type. |
| `CamundaMockito.registerMockInstance(...)` | Apply the same-boundary mapping. | Classify the registered object. Do not infer its boundary from the helper name. |
| `doAnswer(...)` on a whole delegate with fixed outputs | `.thenComplete(outputs)` and `getActivatedJobs()` | Preserve every output variable. Read input variables from the activated job. Keep the invocation verification. |
| `doAnswer(...)` on a whole delegate with input-dependent outputs | `.withHandler(handler)` | Read activation variables and complete the job with the matching outputs. |
| `registerJavaDelegateMock(name)` | `mockJobWorker(type).thenComplete()` | Read `type` from the converted model. |
| `.onExecutionSetVariables(vars)`, `.onExecutionSetVariable(key, value)` | `.thenComplete(vars)` | |
| `.onExecutionSetVariables(first, second)` | `.withHandler(...)` | Complete with the next result on each invocation. |
| `.onExecutionThrowBpmnError(code, message)` | `.thenThrowBpmnError(code, message, Map.of())` or `.thenThrowBpmnError(code)` | Preserve the error code and message when the test checks them. On 8.8, use `.withHandler(...)` and `newThrowErrorCommand(...)` when the message matters. |
| `.onExecutionThrowException(exception)` | `.withHandler(...)` that fails the job with zero retries | Camunda 7 throws into the test. Camunda 8 creates an incident. Assert `hasActiveIncidents()` instead. |
| `DelegateExpressions.autoMock("process.bpmn")` | For each mocked delegate expression, use `mockJobWorker(type).thenComplete()` for its converted service-task or execution-listener type. | The helper applies registrations in source order, and the last registration for a bean sets the effective boundary. Do not infer mocks from `camunda:class` or `camunda:expression`. Read each job type from its own extension declaration. For each retained user-task listener, call `completeJobOfUserTaskListener(...)` for every matching activation. Disable matching real workers in Spring tests. |
| `registerExecutionListenerMock(...)` | `mockJobWorker(type)` | Read `type` from the converted copy's `zeebe:executionListener/@type`. Do not use the attached task's `zeebe:taskDefinition/@type`. |
| `registerTaskListenerMock("listener")` | Where the converted copy retains a listener job, call `completeJobOfUserTaskListener(JobSelectors.byJobType(type), result -> {})` once for every matching listener-job activation. | Read `type` from the matching `zeebe:taskListener/@type`. Record a dropped C7 listener in `mocks.c7` and leave `mocks.c8` without a corresponding mock. |
| `@MockBean` or `@MockitoBean` for a process-used delegate or listener | Apply the matching whole-component mock mapping. | Preserve the mock boundary and disable the matching real worker in Spring. |
| `@MockBean` or `@MockitoBean` for a service called by a delegate | `@MockitoBean` or the version-compatible Spring mock for the same service | Keep the real worker enabled. |
| `verifyJavaDelegateMock("name")` or `verifyExecutionListenerMock("name")` with `executed()`, `executed(times(n))`, or `executedNever()` | `assertThat(mock.getInvocations())` with `isEqualTo(1)`, `isEqualTo(n)`, or `isZero()` | Read the count only after a waiting CPT assertion on the related element. |
| `verifyTaskListenerMock("name").executed()` | Increment an `AtomicInteger` in the listener completion callback and assert that the count is `1`. | Read the count only after a waiting CPT assertion on the related task or process. |
| `verifyTaskListenerMock("name").executed(times(n))` | Increment an `AtomicInteger` in each listener completion callback and assert that the count is `n`. | Complete every matching listener-job activation. Read the count only after a waiting CPT assertion on the related task or process. |
| `verifyTaskListenerMock("name").executedNever()` | Do not complete a matching listener job. | Assert that the same CPT checkpoint succeeds without a matching blocking listener job. Ask the user before claiming parity when no waiting assertion proves the absence. |
| `ArgumentCaptor<DelegateExecution>` on a delegate mock | `mock.getActivatedJobs()` and each job's `getVariablesAsMap()` | Read the activated jobs after a waiting CPT assertion. |
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
