# Camunda Platform Scenario Tests

Camunda Platform Scenario stubs wait states and runs an action when the process reaches each state. CPT conditional behavior provides this pattern from Camunda 8.9. Camunda 8.8 has no equivalent conditional-behavior API. Report scenario tests as `Report only` on 8.8 with the reason `test migration needs Camunda 8.9 or later`.

## Camunda 7

```java
@Deployment(resources = "invoice.bpmn")
public class InvoiceScenarioTest {
  @Mock ProcessScenario process;

  @Test
  public void completesTheInvoice() {
    when(process.waitsAtUserTask("Approve"))
        .thenReturn(task -> task.complete());
    Scenario.run(process).startByKey("invoice").execute();
    verify(process).hasFinished("Done");
  }
}
```

## Camunda 8

This example starts a single `invoice` process instance, so the unscoped selectors are unambiguous. If the test starts multiple instances, scope both the condition and user-task selector to the intended process-instance key.

```java
import io.camunda.process.test.api.assertions.UserTaskSelectors;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-invoice.bpmn")
class InvoiceScenarioTest {
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void completesTheInvoice() {
    processTestContext
        .when(() -> assertThatProcessInstance(byProcessId("invoice"))
            .hasActiveElements("Approve"))
        .as("Approve")
        .then(() -> processTestContext.completeUserTask(UserTaskSelectors.byElementId("Approve")));

    ProcessInstanceEvent pi = client.newCreateInstanceCommand()
        .bpmnProcessId("invoice").latestVersion().send().join();

    assertThat(pi).isCompleted().hasCompletedElements("Done");
  }
}
```

| Camunda Platform Scenario | Camunda Process Test 8.9 or later | Notes |
|---|---|---|
| `@Mock ProcessScenario` and its retained Scenario stubs | Convert each retained stub with the matching CPT behavior below. | Remove the C7 mock and Scenario runner setup only when no retained method needs them. Map each verification to the matching assertion row below. |
| `MockitoAnnotations.openMocks(this)` and matching cleanup | Remove initialization and cleanup when no retained Mockito annotation needs them. | Keep them for retained `@Mock`, `@Spy`, `@Captor`, or `@InjectMocks` fields. |
| JUnit 4 `@Before`, `@After`, and `@Test` | JUnit 5 `@BeforeEach`, `@AfterEach`, and `@Test` | |
| `waitsAtUserTask("A").thenReturn(task -> task.complete(vars))` | `when(() -> assertThatProcessInstance(byKey(processInstanceKey)).hasActiveElements("A")).as("A").then(() -> processTestContext.completeUserTask(UserTaskSelectors.byElementId("A", processInstanceKey), vars))` | The condition and completion action use the Scenario instance's process-instance key. The action must resolve the wait state so CPT can observe it again. |
| `thenReturn(first, second)` or different actions per call on a conditional behavior | Chain `.then(first).then(second)` on the matching CPT conditional behavior. | CPT repeats the last action after earlier actions run. Do not use this chain for worker mocks. |
| `task.handleBpmnError(...)`, `task.handleEscalation(...)` | No direct counterpart | Report for manual migration. |
| `waitsAtServiceTask`, `waitsAtSendTask`, `waitsAtMessageIntermediateThrowEvent`, `waitsAtMessageEndEvent` with `complete(vars)` | `mockJobWorker(type).thenComplete(vars)` | Read the job type from the converted copy. |
| `waitsAtBusinessRuleTask("R")` when the converted task uses `zeebe:calledDecision` | `mockDmnDecision(decisionId, output)` | The called decision runs natively and does not create a worker job. |
| `waitsAtBusinessRuleTask("R")` when the converted task defines `zeebe:taskDefinition` | `mockJobWorker(type).thenComplete(vars)` | Read the job type from the converted copy. |
| Worker-backed wait states with `handleBpmnError(code, vars)` | `mockJobWorker(type).thenThrowBpmnError(code, vars)` | Read the job type from the converted copy. |
| The same external-task stubs handling a BPMN error | `mockJobWorker(type).thenThrowBpmnError(code, variables)` | Read the job type from the converted copy. |
| Repeated external-task actions on a linear path | Call `completeJob(...)` or `throwBpmnErrorFromJob(...)` once per activation in the tested order. | Do not register a worker mock or chain `thenComplete(...)` or `thenThrowBpmnError(...)`; those builder methods return `JobWorkerMock`. |
| Repeated external-task actions on a non-linear path | Configure `mockJobWorker(type).withHandler(...)` to select the response for each activated job. | Read the job type from the converted copy. |
| `waitsAtTimerIntermediateEvent("T")` | Assert `hasActiveElements("T")`, then call `increaseTime(duration)` | Read the duration from the converted timer. |
| `action.defer(period, action)` | Increase time in steps, then run the action | Keep each step no longer than the shortest timer period on the path. Assert the expected timer effect after each step. |
| `waitsAtMessageIntermediateCatchEvent` or `waitsAtReceiveTask` with `receive(vars)` | `newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(vars).send().join()` | Read the message name and correlation-key FEEL expression from the converted copy's `zeebe:subscription`. Evaluate the expression against the test variables. Pass the result to `correlationKey(...)`, not the expression text. Wait with `isWaitingForMessage(name, key)`. |
| `waitsAtSignalIntermediateCatchEvent` with `receive()` | `newBroadcastSignalCommand().signalName(name).send().join()` | Read the signal name from the converted copy. |
| `waitsAtEventBasedGateway("G")` with an event action | The message, signal, or timer action for the selected event | Read the event type and subscription from the converted copy. |
| `waitsAtConditionalIntermediateEvent("C")` | `processTestContext.updateVariables(byKey(pik), vars)` | Conditional events require 8.9. |
| `runsCallActivity("C").thenReturn(Scenario.use(child))` | Deploy the converted child and register its behaviors with `byProcessId(childPid)` | Preserve the mocked-child boundary when the Camunda 7 test mocks the child process. |
| `withMockedProcess("child")`, `waitsAtMockedCallActivity("C")` | `mockChildProcess("child", vars)` | Preserve the existing mocked-child boundary. |
| `Scenario.run(process).startByKey(key, vars).execute()` | `newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Retain the returned `ProcessInstanceEvent`. When the source test sets a business key, apply the confirmed business-key mapping. |
| `startByMessage(name, vars)` | `CorrelateMessageResponse response = newCorrelateMessageCommand().messageName(name).withoutCorrelationKey().variables(vars).send().join()` | The response is not a `ProcessInstanceEvent`; use `response.getProcessInstanceKey()` with `ProcessInstanceSelectors.byKey(...)` to select the instance for CPT assertions. |
| `.fromBefore("A")` | `.startBeforeElement("A")` on the create command | |
| `.fromAfter("A")` with an unambiguous next element | `.startBeforeElement(nextElement)` on the create command | Resolve the next element from the converted copy. |
| `.fromAfter("A")` with no clear next element | No direct counterpart | Record `manual` in the parity ledger because the next element is ambiguous. |
| `Scenario.instance(process)` after `startByKey` or `fromBefore` | The `ProcessInstanceEvent` returned by the create command | |
| `Scenario.instance(process)` after `startByMessage` | `assertThatProcessInstance(ProcessInstanceSelectors.byKey(response.getProcessInstanceKey()))` | `response` is the `CorrelateMessageResponse` returned by the message-correlation command. |
| `verify(process).hasCompleted("E")` | `assertThat(pi).hasCompletedElements("E")` | |
| `verify(process, times(n)).hasCompleted("E")` | Assert `hasCompletedElement("E", n)`. | Preserve the exact completed-element count. |
| `verify(process).hasFinished("E")` | `hasCompletedElements("E")` or `hasTerminatedElements("E")` | `hasFinished` includes completed and cancelled activities. |
| `verify(process, times(n)).hasFinished("E")` | When all visits completed, assert `hasCompletedElement("E", n)`. When all visits terminated, assert `hasTerminatedElement("E", n)`. When the outcomes are mixed, assert both with their respective counts. | `hasFinished` includes completed and canceled activities. For mixed outcomes, assert after the scenario's final observation point. The completed and terminated counts must sum to `n`. Each exact-count assertion waits. |
| `verify(process).hasCanceled("E")` | `hasTerminatedElements("E")` | |
| `verify(process).hasStarted("E")` | Assert the reached state with `hasActiveElements`, `hasCompletedElements`, or `hasTerminatedElements` | |
| `verify(process, never()).hasStarted("E")` | `hasNotActivatedElements("E")` after a waiting assertion at the intended observation point | This check does not wait. A preceding `hasNoActiveElements("A")` can pass before A is reached. |

The scenario runner fails as soon as the process reaches an unstubbed wait state. CPT leaves the process waiting, so a final assertion fails after its timeout. Keep the existing stubs. Do not add behavior for a wait state that the Camunda 7 test left undefined.

The scenario runner advances the clock to each due timer in turn. CPT advances the clock once per call. Increase time in steps no longer than the shortest timer period on the path, and assert the expected effect after each step. For `defer(period, action)`, run the action after the total increase reaches `period`.

External tasks in Camunda 7 scenarios were completed by the scenario runner, so use `mockJobWorker` for those task types. Java delegates are not wait states. Keep migrated workers real unless the Camunda 7 test mocked them.

[CPT conditional behavior](https://docs.camunda.io/docs/apis-tools/testing/utilities/#conditional-behavior)
